package no.vegvesen.nvdb.kafka.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import kotlin.reflect.typeOf

class KotlinxJsonSerializer : Serializer<Any> {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun serialize(topic: String?, data: Any?): ByteArray? {
        if (data == null) return null
        
        return try {
            val kType = typeOf<Any>()
            val serializer = serializer(data::class.java) as kotlinx.serialization.KSerializer<Any>
            json.encodeToString(serializer, data).toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            throw SerializationException("Error serializing value of type ${data::class.simpleName}", e)
        }
    }

    override fun close() {
        // No resources to close
    }
}
