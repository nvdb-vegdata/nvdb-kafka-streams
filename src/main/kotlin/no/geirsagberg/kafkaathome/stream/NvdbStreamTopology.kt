package no.geirsagberg.kafkaathome.stream

import com.fasterxml.jackson.databind.ObjectMapper
import no.geirsagberg.kafkaathome.model.Vegobjekt
import no.geirsagberg.kafkaathome.service.GeometryEnrichmentService
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kafka Streams topology for processing NVDB road data.
 *
 * This topology:
 * 1. Consumes raw road object data from the input topic
 * 2. Transforms the data (e.g., extracts speed limits, enriches with geometry)
 * 3. Produces transformed data to output topics
 */
@Configuration
class NvdbStreamTopology(
    private val objectMapper: ObjectMapper,
    private val geometryEnrichmentService: GeometryEnrichmentService
) {
    private val logger = LoggerFactory.getLogger(NvdbStreamTopology::class.java)

    @Value($$"${kafka.topics.input:nvdb-vegobjekter-raw}")
    private lateinit var inputTopic: String

    @Value($$"${kafka.topics.output:nvdb-vegobjekter-transformed}")
    private lateinit var outputTopic: String

    @Value($$"${kafka.topics.speedlimits:nvdb-fartsgrenser}")
    private lateinit var speedLimitsTopic: String

    @Value($$"${nvdb.enrichment.enabled:false}")
    private var enrichmentEnabled: Boolean = false

    @Bean
    fun nvdbStreamsTopology(streamsBuilder: StreamsBuilder): KStream<String, String> {
        logger.info("Building NVDB Kafka Streams topology (enrichment: {})", enrichmentEnabled)

        // Consume raw road object data
        val inputStream: KStream<String, String> = streamsBuilder.stream(
            inputTopic,
            Consumed.with(Serdes.String(), Serdes.String())
        )

        // Transform and filter the data
        val transformedStream = inputStream
            .peek { key, value -> logger.debug("Processing record with key: {}", key) }
            .mapValues { value -> parseVegobjekt(value) }
            .filter { _, vegobjekt -> vegobjekt != null }
            .mapValues { vegobjekt ->
                if (enrichmentEnabled) {
                    geometryEnrichmentService.enrichWithGeometry(vegobjekt!!)
                } else {
                    vegobjekt!!
                }
            }
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
