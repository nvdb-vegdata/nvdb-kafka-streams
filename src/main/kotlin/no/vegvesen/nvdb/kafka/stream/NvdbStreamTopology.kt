package no.vegvesen.nvdb.kafka.stream

import no.vegvesen.nvdb.kafka.model.*
import no.vegvesen.nvdb.kafka.serialization.kotlinxJsonSerde
import no.vegvesen.nvdb.kafka.serialization.strekningKeySerde
import no.vegvesen.nvdb.kafka.serialization.strekningKeySetSerde
import no.vegvesen.nvdb.kafka.serialization.strekningReferenceDeltaSerde
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.StoreBuilder
import org.apache.kafka.streams.state.Stores
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NvdbStreamTopology {
    @Suppress("unused")
    private val logger = LoggerFactory.getLogger(NvdbStreamTopology::class.java)

    private val vegsystemTopic = "nvdb-vegobjekter-915"
    private val strekningTopic = "nvdb-vegobjekter-916"

    private val vegobjektDeltaSerde: Serde<VegobjektDelta> = kotlinxJsonSerde()
    private val vegobjektUtstrekningDeltaSerde: Serde<VegobjektUtstrekningDelta> = kotlinxJsonSerde()
    private val vegobjektUtstrekningerSerde: Serde<Set<VegobjektUtstrekning>> = kotlinxJsonSerde()
    private val strekningKeySerde: Serde<StrekningKey> = strekningKeySerde()
    private val strekningKeySetSerde: Serde<Set<StrekningKey>> = strekningKeySetSerde()
    private val strekningReferenceDeltaSerde: Serde<StrekningReferenceDelta> = strekningReferenceDeltaSerde()
    private val veglenkesekvensIdSetSerde: Serde<Set<Long>> = kotlinxJsonSerde()

    @Bean
    fun vegobjektTopology(builder: StreamsBuilder): StreamsBuilder {
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
        @Suppress("unused")
        val strekningreferanserTable = referenceDeltas
            .peek { key, delta ->
//                logger.debug("Processing delta: key=$key, delta=$delta")
            }
            .groupBy(
                { _, delta ->
//                    logger.debug("Grouping by StrekningKey: ${delta.key}")
                    delta.key
                },
                Grouped.with(strekningKeySerde, strekningReferenceDeltaSerde)
            )
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

        return builder
    }

    private fun StreamsBuilder.buildUtstrekningTable(
        topic: String
    ): KTable<Long, Set<VegobjektUtstrekning>> =
        stream(
            topic,
            Consumed.with(Serdes.Long(), vegobjektDeltaSerde)
        )
            .flatMapToUtstrekningDeltas()
            .groupByKey(Grouped.with(Serdes.Long(), vegobjektUtstrekningDeltaSerde))
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
    ): Set<StrekningReference> {
        val references = mutableSetOf<StrekningReference>()

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

