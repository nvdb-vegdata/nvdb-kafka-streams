package no.vegvesen.nvdb.kafka.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

class KotlinxJsonSerializer<T : Any>(private val serializer: KSerializer<T>) : Serializer<T> {

    override fun serialize(topic: String, data: T?): ByteArray {
        if (data == null) return ByteArray(0)

        return try {
            val jsonString = json.encodeToString(serializer, data)
            val bytes = jsonString.toByteArray()

            // Log large messages that might cause issues
            if (bytes.size > 100_000) {
                println("WARNING: Large message being serialized to topic '$topic': ${bytes.size} bytes")
            }

            bytes
        } catch (e: Exception) {
            throw SerializationException("Error serializing value to topic '$topic': ${e.message}", e)
        }
    }
}

class KotlinxJsonDeserializer<T : Any>(private val deserializer: KSerializer<T>) : Deserializer<T> {

    override fun deserialize(topic: String, data: ByteArray?): T? {
        if (data == null || data.isEmpty()) return null

        return try {
            json.decodeFromString(deserializer, data.decodeToString())
        } catch (e: Exception) {
            throw SerializationException("Error deserializing value", e)
        }
    }
}

inline fun <reified T : Any> kotlinxJsonSerializer(): KotlinxJsonSerializer<T> =
    KotlinxJsonSerializer(serializer())

inline fun <reified T : Any> kotlinxJsonDeserializer(): KotlinxJsonDeserializer<T> =
    KotlinxJsonDeserializer(serializer())

inline fun <reified T : Any> kotlinxJsonSerde(): Serde<T> = Serdes.serdeFrom(
    kotlinxJsonSerializer(),
    kotlinxJsonDeserializer()
)
