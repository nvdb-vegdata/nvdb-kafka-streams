package no.vegvesen.nvdb.kafka.serialization

import no.vegvesen.nvdb.kafka.model.VegfaseEgenskap
import no.vegvesen.nvdb.kafka.model.VegkategoriEgenskap
import no.vegvesen.nvdb.kafka.stream.StrekningKey
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer
import java.nio.ByteBuffer

/**
 * Efficient binary serializer for StrekningKey.
 * Format: 8 bytes total
 * - 1 byte: vegkategori (ordinal)
 * - 1 byte: fase (ordinal)
 * - 2 bytes: vegnummer (short)
 * - 2 bytes: strekning (short)
 * - 2 bytes: delstrekning (short)
 */
class StrekningKeySerializer : Serializer<StrekningKey> {
    override fun serialize(topic: String, data: StrekningKey?): ByteArray? {
        if (data == null) return null

        return ByteBuffer.allocate(8)
            .put(data.vegkategori.ordinal.toByte())
            .put(data.fase.ordinal.toByte())
            .putShort(data.vegnummer.toShort())
            .putShort(data.strekning.toShort())
            .putShort(data.delstrekning.toShort())
            .array()
    }
}

class StrekningKeyDeserializer : Deserializer<StrekningKey> {
    override fun deserialize(topic: String, data: ByteArray?): StrekningKey? {
        if (data == null || data.isEmpty()) return null

        val buffer = ByteBuffer.wrap(data)
        val vegkategoriOrdinal = buffer.get().toInt()
        val faseOrdinal = buffer.get().toInt()
        val vegnummer = buffer.short.toInt()
        val strekning = buffer.short.toInt()
        val delstrekning = buffer.short.toInt()

        return StrekningKey(
            vegkategori = VegkategoriEgenskap.entries[vegkategoriOrdinal],
            fase = VegfaseEgenskap.entries[faseOrdinal],
            vegnummer = vegnummer,
            strekning = strekning,
            delstrekning = delstrekning
        )
    }
}

fun strekningKeySerde(): Serde<StrekningKey> = Serdes.serdeFrom(
    StrekningKeySerializer(),
    StrekningKeyDeserializer()
)

/**
 * Efficient binary serializer for Set<StrekningKey>.
 * Format:
 * - 4 bytes: set size (int)
 * - 8 bytes per key: StrekningKey binary format
 */
class StrekningKeySetSerializer : Serializer<Set<StrekningKey>> {
    private val keySerializer = StrekningKeySerializer()

    override fun serialize(topic: String, data: Set<StrekningKey>?): ByteArray? {
        if (data == null) return null

        val buffer = ByteBuffer.allocate(4 + data.size * 8)
        buffer.putInt(data.size)

        data.forEach { key ->
            val keyBytes = keySerializer.serialize(topic, key)!!
            buffer.put(keyBytes)
        }

        return buffer.array()
    }
}

class StrekningKeySetDeserializer : Deserializer<Set<StrekningKey>> {
    private val keyDeserializer = StrekningKeyDeserializer()

    override fun deserialize(topic: String, data: ByteArray?): Set<StrekningKey>? {
        if (data == null || data.isEmpty()) return null

        val buffer = ByteBuffer.wrap(data)
        val size = buffer.getInt()
        val keys = mutableSetOf<StrekningKey>()

        repeat(size) {
            val keyBytes = ByteArray(8)
            buffer.get(keyBytes)
            val key = keyDeserializer.deserialize(topic, keyBytes)!!
            keys.add(key)
        }

        return keys
    }
}

fun strekningKeySetSerde(): Serde<Set<StrekningKey>> = Serdes.serdeFrom(
    StrekningKeySetSerializer(),
    StrekningKeySetDeserializer()
)

/**
 * Efficient binary serializer for StrekningReferenceDelta.
 * Format: 17 bytes total
 * - 1 byte: fjernet (boolean)
 * - 8 bytes: key (StrekningKey binary format)
 * - 8 bytes: veglenkesekvensId (long)
 */
class StrekningReferenceDeltaSerializer : Serializer<no.vegvesen.nvdb.kafka.stream.StrekningReferenceDelta> {
    private val keySerializer = StrekningKeySerializer()

    override fun serialize(topic: String, data: no.vegvesen.nvdb.kafka.stream.StrekningReferenceDelta?): ByteArray? {
        if (data == null) return null

        val buffer = ByteBuffer.allocate(17)
        buffer.put(if (data.fjernet) 1.toByte() else 0.toByte())
        buffer.put(keySerializer.serialize(topic, data.key)!!)
        buffer.putLong(data.veglenkesekvensId)

        return buffer.array()
    }
}

class StrekningReferenceDeltaDeserializer : Deserializer<no.vegvesen.nvdb.kafka.stream.StrekningReferenceDelta> {
    private val keyDeserializer = StrekningKeyDeserializer()

    override fun deserialize(topic: String, data: ByteArray?): no.vegvesen.nvdb.kafka.stream.StrekningReferenceDelta? {
        if (data == null || data.isEmpty()) return null

        val buffer = ByteBuffer.wrap(data)
        val fjernet = buffer.get() == 1.toByte()

        val keyBytes = ByteArray(8)
        buffer.get(keyBytes)
        val key = keyDeserializer.deserialize(topic, keyBytes)!!

        val veglenkesekvensId = buffer.long

        return no.vegvesen.nvdb.kafka.stream.StrekningReferenceDelta(
            fjernet = fjernet,
            key = key,
            veglenkesekvensId = veglenkesekvensId
        )
    }
}

fun strekningReferenceDeltaSerde(): Serde<no.vegvesen.nvdb.kafka.stream.StrekningReferenceDelta> = Serdes.serdeFrom(
    StrekningReferenceDeltaSerializer(),
    StrekningReferenceDeltaDeserializer()
)
