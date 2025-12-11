package no.vegvesen.nvdb.kafka.serialization

import no.vegvesen.nvdb.kafka.model.VegfaseEgenskap
import no.vegvesen.nvdb.kafka.model.VegkategoriEgenskap
import no.vegvesen.nvdb.kafka.stream.StrekningKey
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class StrekningKeySerializationTest {

    @Test
    fun `StrekningKey serializes and deserializes correctly`() {
        val key = StrekningKey(
            vegkategori = VegkategoriEgenskap.E,
            fase = VegfaseEgenskap.V,
            vegnummer = 6,
            strekning = 1,
            delstrekning = 1
        )

        val serde = kotlinxJsonSerde<StrekningKey>()
        val serializer = serde.serializer()
        val deserializer = serde.deserializer()

        val bytes = serializer.serialize("test-topic", key)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())

        println("Serialized StrekningKey size: ${bytes.size} bytes")
        println("Serialized JSON: ${bytes.decodeToString()}")

        val deserialized = deserializer.deserialize("test-topic", bytes)
        assertEquals(key, deserialized)
    }

    @Test
    fun `StrekningKey serialization is compact`() {
        val key = StrekningKey(
            vegkategori = VegkategoriEgenskap.E,
            fase = VegfaseEgenskap.V,
            vegnummer = 6,
            strekning = 1,
            delstrekning = 1
        )

        val serde = kotlinxJsonSerde<StrekningKey>()
        val bytes = serde.serializer().serialize("test-topic", key)

        // Should be small - just a simple object
        assertTrue(bytes.size < 200, "StrekningKey serialized to ${bytes.size} bytes, expected < 200")
    }
}
