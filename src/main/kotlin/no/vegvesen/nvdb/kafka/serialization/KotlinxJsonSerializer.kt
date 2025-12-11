package no.vegvesen.nvdb.kafka.serialization

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

class KotlinxJsonSerializer<T : Any> : Serializer<T> {

    override fun serialize(topic: String, data: T?): ByteArray {
        if (data == null) return ByteArray(0)

        return try {
            val serializer = serializer(data::class.java)
            json.encodeToString(serializer, data).toByteArray()
        } catch (e: Exception) {
            throw SerializationException("Error serializing value of type ${data::class.simpleName}", e)
        }
    }
}

class KotlinxJsonDeserializer<T : Any>(private val targetClass: Class<T>) : Deserializer<T> {

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(topic: String, data: ByteArray?): T? {
        if (data == null || data.isEmpty()) return null

        return try {
            val deserializer = serializer(targetClass)
            json.decodeFromString(deserializer, data.decodeToString()) as T
        } catch (e: Exception) {
            throw SerializationException("Error deserializing value to type ${targetClass.simpleName}", e)
        }
    }
}

inline fun <reified T : Any> kotlinxJsonSerializer(): KotlinxJsonSerializer<T> =
    KotlinxJsonSerializer()

inline fun <reified T : Any> kotlinxJsonDeserializer(): KotlinxJsonDeserializer<T> =
    KotlinxJsonDeserializer(T::class.java)

inline fun <reified T : Any> kotlinxJsonSerde(): Serde<T> = Serdes.serdeFrom(
    kotlinxJsonSerializer(),
    kotlinxJsonDeserializer()
)
