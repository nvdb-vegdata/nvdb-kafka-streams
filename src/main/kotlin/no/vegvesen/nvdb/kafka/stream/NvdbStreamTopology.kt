package no.vegvesen.nvdb.kafka.stream

import com.fasterxml.jackson.databind.ObjectMapper
import no.vegvesen.nvdb.kafka.model.Vegobjekt
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NvdbStreamTopology(
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(NvdbStreamTopology::class.java)

    private val vegsystemTopic = "nvdb-vegobjekter-915"
    private val strekningTopic = "nvdb-vegobjekter-916"
    private val inputTopic = vegsystemTopic
    private val enrichmentEnabled = true


    @Bean
    fun nvdbStreamsTopology(streamsBuilder: StreamsBuilder): KStream<String, String> {
        logger.info("Building NVDB Kafka Streams topology")

        val inputStream: KStream<String, String> = streamsBuilder.stream(
            inputTopic,
            Consumed.with(Serdes.String(), Serdes.String())
        )

        // Transform and filter the data
        val transformedStream = inputStream
            .peek { key, value -> logger.debug("Processing record with key: {}", key) }
            .mapValues { value -> parseVegobjekt(value) }
            .filter { _, vegobjekt -> vegobjekt != null }
            .mapValues { vegobjekt -> transformVegobjekt(vegobjekt) }
            .filter { _, value -> value != null }
            .mapValues { value -> value!! }

        // Produce to the main output topic
        transformedStream.to(
            outputTopic,
            Produced.with(Serdes.String(), Serdes.String())
        )

        // Branch speed limits to a dedicated topic
        val speedLimitsStream = transformedStream
            .filter { _, value -> isSpeedLimit(value) }

        speedLimitsStream.to(
            speedLimitsTopic,
            Produced.with(Serdes.String(), Serdes.String())
        )

        logger.info("NVDB Kafka Streams topology built successfully")
        return transformedStream
    }

    /**
     * Parse JSON to Vegobjekt.
     */
    private fun parseVegobjekt(jsonValue: String): Vegobjekt? {
        return try {
            objectMapper.readValue(jsonValue, Vegobjekt::class.java)
        } catch (e: Exception) {
            logger.warn("Failed to parse vegobjekt: {}", e.message)
            null
        }
    }

    /**
     * Transform a vegobjekt into an enriched format.
     */
    private fun transformVegobjekt(vegobjekt: Vegobjekt): String? {
        return try {
            val enriched = mapOf(
                "id" to vegobjekt.id,
                "typeId" to vegobjekt.typeId,
                "versjon" to vegobjekt.versjon,
                "gyldighetsperiode" to vegobjekt.gyldighetsperiode,
                "egenskaper" to vegobjekt.egenskaper,
                "stedfesting" to vegobjekt.stedfesting,
                "geometri" to vegobjekt.geometri?.wkt,
                "sistEndret" to vegobjekt.sistEndret,
                "processedAt" to System.currentTimeMillis()
            )

            objectMapper.writeValueAsString(enriched)
        } catch (e: Exception) {
            logger.warn("Failed to transform vegobjekt: {}", e.message)
            null
        }
    }

    /**
     * Check if a road object is a speed limit (fartsgrense).
     */
    private fun isSpeedLimit(jsonValue: String): Boolean {
        return try {
            val node = objectMapper.readTree(jsonValue)
            node.get("typeId")?.asInt() == 105 // Speed limit type ID
        } catch (e: Exception) {
            false
        }
    }
}
