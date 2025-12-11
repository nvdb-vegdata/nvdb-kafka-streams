package no.vegvesen.nvdb.kafka.stream

import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.kafka.model.*
import no.vegvesen.nvdb.kafka.serialization.kotlinxJsonSerde
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KTable
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.StoreBuilder
import org.apache.kafka.streams.state.Stores
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Serializable
data class StrekningKey(
    val vegkategori: VegkategoriEgenskap,
    val fase: VegfaseEgenskap,
    val vegnummer: Int,
    val strekning: Int,
    val delstrekning: Int
) : Comparable<StrekningKey> {
    override fun compareTo(other: StrekningKey): Int {
        vegkategori.compareTo(other.vegkategori).takeIf { it != 0 }?.let { return it }
        fase.compareTo(other.fase).takeIf { it != 0 }?.let { return it }
        vegnummer.compareTo(other.vegnummer).takeIf { it != 0 }?.let { return it }
        strekning.compareTo(other.strekning).takeIf { it != 0 }?.let { return it }
        return delstrekning.compareTo(other.delstrekning)
    }

    override fun toString(): String = buildString {
        append(vegkategori)
        append(fase)
        append(vegnummer)
        append(" S")
        append(strekning)
        append("D")
        append(delstrekning)
    }

    companion object {
        /**
         * Create a range query for all keys matching the given criteria.
         * Pass null for fields that should match any value.
         *
         * Examples:
         * - rangeFor(EV, V, 6, null, null) → All of EV6
         * - rangeFor(EV, V, 6, 3, null) → All of EV6 S3 (all delstrekninger)
         * - rangeFor(EV, V, null, null, null) → All EV roads
         */
        fun rangeFor(
            vegkategori: VegkategoriEgenskap? = null,
            fase: VegfaseEgenskap? = null,
            vegnummer: Int? = null,
            strekning: Int? = null,
            delstrekning: Int? = null
        ): Pair<StrekningKey, StrekningKey> {
            // Build the "from" key with minimum values for unspecified fields
            val from = StrekningKey(
                vegkategori = vegkategori ?: VegkategoriEgenskap.entries.first(),
                fase = fase ?: VegfaseEgenskap.entries.first(),
                vegnummer = vegnummer ?: 0,
                strekning = strekning ?: 0,
                delstrekning = delstrekning ?: 0
            )

            // Build the "to" key by incrementing the last specified field
            val to = when {
                delstrekning != null -> from.copy(delstrekning = delstrekning + 1)
                strekning != null -> from.copy(strekning = strekning + 1, delstrekning = 0)
                vegnummer != null -> from.copy(vegnummer = vegnummer + 1, strekning = 0, delstrekning = 0)
                fase != null -> {
                    val nextFase = VegfaseEgenskap.entries.getOrNull(fase.ordinal + 1)
                    if (nextFase != null) {
                        from.copy(fase = nextFase, vegnummer = 0, strekning = 0, delstrekning = 0)
                    } else {
                        // Move to next vegkategori
                        val nextKat = VegkategoriEgenskap.entries.getOrNull(vegkategori!!.ordinal + 1)
                        from.copy(
                            vegkategori = nextKat ?: vegkategori,
                            fase = VegfaseEgenskap.entries.first(),
                            vegnummer = 0,
                            strekning = 0,
                            delstrekning = 0
                        )
                    }
                }

                vegkategori != null -> {
                    val nextKat = VegkategoriEgenskap.entries.getOrNull(vegkategori.ordinal + 1)
                    from.copy(
                        vegkategori = nextKat ?: vegkategori,
                        fase = VegfaseEgenskap.entries.first(),
                        vegnummer = 0,
                        strekning = 0,
                        delstrekning = 0
                    )
                }

                else -> from.copy(
                    vegkategori = VegkategoriEgenskap.entries.last(),
                    fase = VegfaseEgenskap.entries.last(),
                    vegnummer = Int.MAX_VALUE,
                    strekning = Int.MAX_VALUE,
                    delstrekning = Int.MAX_VALUE
                )
            }

            return from to to
        }
    }
}

@Serializable
data class StrekningReference(
    val key: StrekningKey,
    val veglenkesekvensId: Long
)

@Serializable
data class StrekningReferenceDelta(
    val fjernet: Boolean,
    val key: StrekningKey,
    val veglenkesekvensId: Long
)

class StrekningReferenceChangeDetector : Processor<Long, Set<StrekningKey>, Long, StrekningReferenceDelta> {
    private lateinit var context: ProcessorContext<Long, StrekningReferenceDelta>
    private lateinit var stateStore: KeyValueStore<Long, Set<StrekningKey>>

    override fun init(context: ProcessorContext<Long, StrekningReferenceDelta>) {
        this.context = context
        this.stateStore = context.getStateStore(STORE_NAME)
    }

    override fun process(record: Record<Long, Set<StrekningKey>>) {
        val veglenkesekvensId = record.key()
        val newKeys = record.value() ?: emptySet()
        val oldKeys = stateStore.get(veglenkesekvensId) ?: emptySet()

        // Emit removals for keys that were removed
        (oldKeys - newKeys).forEach { key ->
            context.forward(
                record.withKey(veglenkesekvensId)
                    .withValue(StrekningReferenceDelta(fjernet = true, key = key, veglenkesekvensId = veglenkesekvensId))
            )
        }

        // Emit additions for keys that were added
        (newKeys - oldKeys).forEach { key ->
            context.forward(
                record.withKey(veglenkesekvensId)
                    .withValue(StrekningReferenceDelta(fjernet = false, key = key, veglenkesekvensId = veglenkesekvensId))
            )
        }

        // Update state
        if (newKeys.isEmpty()) {
            stateStore.delete(veglenkesekvensId)
        } else {
            stateStore.put(veglenkesekvensId, newKeys)
        }
    }

    companion object {
        const val STORE_NAME = "strekning-reference-previous-state"
    }
}

@Configuration
class NvdbStreamTopology {
    private val logger = LoggerFactory.getLogger(NvdbStreamTopology::class.java)

    private val vegsystemTopic = "nvdb-vegobjekter-915"
    private val strekningTopic = "nvdb-vegobjekter-916"

    private val vegobjektDeltaSerde: Serde<VegobjektDelta> = kotlinxJsonSerde()
    private val vegobjektUtstrekningerSerde: Serde<Set<VegobjektUtstrekning>> = kotlinxJsonSerde()
    private val strekningKeySerde: Serde<StrekningKey> = kotlinxJsonSerde()
    private val strekningKeySetSerde: Serde<Set<StrekningKey>> = kotlinxJsonSerde()
    private val veglenkesekvensIdSetSerde: Serde<Set<Long>> = kotlinxJsonSerde()

    @Bean
    fun vegobjektTopology(builder: StreamsBuilder) {
        val vegsystemTable = builder.buildUtstrekningTable(vegsystemTopic)
        val strekningTable = builder.buildUtstrekningTable(strekningTopic)

        // Create state store for tracking previous StrekningKey sets
        val storeBuilder: StoreBuilder<KeyValueStore<Long, Set<StrekningKey>>> = Stores
            .keyValueStoreBuilder(
                Stores.persistentKeyValueStore(StrekningReferenceChangeDetector.STORE_NAME),
                Serdes.Long(),
                strekningKeySetSerde
            )
        builder.addStateStore(storeBuilder)

        // Convert join updates to reference deltas (add/remove events)
        val referenceDeltas = vegsystemTable
            .join(strekningTable) { vegsystemSet, strekningSet ->
                findOverlappingReferences(vegsystemSet, strekningSet).map { it.key }.toSet()
            }
            .toStream()
            .process(
                ProcessorSupplier { StrekningReferenceChangeDetector() },
                StrekningReferenceChangeDetector.STORE_NAME
            )

        // Aggregate deltas into final state
        val strekningreferanserTable = referenceDeltas
            .groupBy { _, delta -> delta.key }
            .aggregate(
                { emptySet() },
                { _, delta, acc ->
                    if (delta.fjernet)
                        acc - delta.veglenkesekvensId
                    else
                        acc + delta.veglenkesekvensId
                },
                Materialized.`as`<StrekningKey, Set<Long>, KeyValueStore<Bytes, ByteArray>>(
                    "strekningreferanser-store"
                )
                    .withKeySerde(strekningKeySerde)
                    .withValueSerde(veglenkesekvensIdSetSerde)
            )
    }

    private fun StreamsBuilder.buildUtstrekningTable(
        topic: String
    ): KTable<Long, Set<VegobjektUtstrekning>> =
        stream(
            topic,
            Consumed.with(Serdes.Long(), vegobjektDeltaSerde)
        )
            .flatMapToUtstrekningDeltas()
            .groupByKey()
            .aggregate(
                { emptySet() },
                { _, delta, acc ->
                    if (delta.fjernet)
                        acc - delta.utstrekning
                    else
                        acc + delta.utstrekning
                },
                Materialized.`as`<Long, Set<VegobjektUtstrekning>, KeyValueStore<Bytes, ByteArray>>(
                    "$topic-utstrekninger"
                )
                    .withKeySerde(Serdes.Long())
                    .withValueSerde(vegobjektUtstrekningerSerde)
            )

    private fun findOverlappingReferences(
        vegsystemSet: Set<VegobjektUtstrekning>,
        strekningSet: Set<VegobjektUtstrekning>
    ): List<StrekningReference> {
        val references = mutableListOf<StrekningReference>()

        for (vegsystem in vegsystemSet) {
            for (strekning in strekningSet) {
                if (extentsOverlap(vegsystem, strekning)) {
                    val key = computeReferenceKey(vegsystem.egenskaper, strekning.egenskaper) ?: continue
                    references.add(StrekningReference(key, vegsystem.veglenkesekvensId))
                }
            }
        }

        return references
    }

    private fun extentsOverlap(a: VegobjektUtstrekning, b: VegobjektUtstrekning): Boolean {
        return a.startposisjon < b.sluttposisjon && b.startposisjon < a.sluttposisjon
    }

    object EgenskapTyper {
        // Vegsystem
        const val Vegkategori = 11276
        const val Fase = 11278
        const val Vegnummer = 11277

        // Strekning
        const val Strekning = 11281
        const val Delstrekning = 11284
    }

    private fun computeReferenceKey(
        vegsystemEgenskaper: Map<Int, String>,
        strekningEgenskaper: Map<Int, String>
    ): StrekningKey? {
        val vegkategori =
            vegsystemEgenskaper[EgenskapTyper.Vegkategori]?.let { VegkategoriEgenskap.fromNvdbValue(it.toInt()) }
                ?: return null
        val fase =
            vegsystemEgenskaper[EgenskapTyper.Fase]?.let { VegfaseEgenskap.fromNvdbValue(it.toInt()) }
                ?: return null
        val vegnummer = vegsystemEgenskaper[EgenskapTyper.Vegnummer]?.toInt() ?: return null

        val strekning = strekningEgenskaper[EgenskapTyper.Strekning]?.toInt() ?: return null
        val delstrekning = strekningEgenskaper[EgenskapTyper.Delstrekning]?.toInt() ?: return null

        return StrekningKey(
            vegkategori = vegkategori,
            fase = fase,
            vegnummer = vegnummer,
            strekning = strekning,
            delstrekning = delstrekning
        )
    }

    private fun KStream<Long, VegobjektDelta>.flatMapToUtstrekningDeltas(): KStream<Long, VegobjektUtstrekningDelta> =
        flatMap { _, delta ->
            val gamle = delta.before.toUtstrekninger()
            val nye = delta.after.toUtstrekninger()

            val fjernet = gamle - nye
            val lagtTil = nye - gamle

            val fjernetRecords = fjernet.map {
                KeyValue(it.veglenkesekvensId, VegobjektUtstrekningDelta(true, it))
            }
            val lagtTilRecords = lagtTil.map {
                KeyValue(it.veglenkesekvensId, VegobjektUtstrekningDelta(false, it))
            }

            fjernetRecords + lagtTilRecords
        }
}

