package no.vegvesen.nvdb.kafka.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer

class KotlinxJsonSerializer<T : Any> : Serializer<T> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun serialize(topic: String?, data: T?): ByteArray {
        if (data == null) return ByteArray(0)

        return try {
            val serializer = serializer(data::class.java)
            json.encodeToString(serializer, data).toByteArray()
        } catch (e: Exception) {
            throw SerializationException("Error serializing value of type ${data::class.simpleName}", e)
        }
    }

    override fun close() {
        // No resources to close
    }
}
