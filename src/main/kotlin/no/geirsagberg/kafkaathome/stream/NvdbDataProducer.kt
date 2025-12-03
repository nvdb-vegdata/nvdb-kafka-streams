package no.geirsagberg.kafkaathome.stream

import com.fasterxml.jackson.databind.ObjectMapper
import no.geirsagberg.kafkaathome.api.NvdbApiClient
import no.geirsagberg.kafkaathome.model.Vegobjekt
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Service that fetches road data from NVDB API and produces it to Kafka topics.
 * This bridges the REST API with Kafka Streams processing.
 */
@Service
class NvdbDataProducer(
    private val nvdbApiClient: NvdbApiClient,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(NvdbDataProducer::class.java)

    @Value("\${kafka.topics.input:nvdb-vegobjekter-raw}")
    private lateinit var inputTopic: String

    @Value("\${nvdb.producer.enabled:false}")
    private var producerEnabled: Boolean = false

    @Value("\${nvdb.producer.batch-size:100}")
    private var batchSize: Int = 100

    /**
     * Scheduled task that periodically fetches speed limits from NVDB and produces to Kafka.
     * Uses fixedRate for more predictable scheduling (runs every hour by default).
     */
    @Scheduled(fixedRateString = "\${nvdb.producer.interval-ms:3600000}")
    fun fetchAndProduceSpeedLimits() {
        if (!producerEnabled) {
            logger.debug("NVDB producer is disabled")
            return
        }

        logger.info("Starting to fetch speed limits from NVDB API")
        
        nvdbApiClient.streamVegobjekter(NvdbApiClient.TYPE_FARTSGRENSE, batchSize)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext { vegobjekt -> 
                produceToKafka(vegobjekt)
            }
            .doOnComplete {
                logger.info("Completed fetching and producing speed limits")
            }
            .doOnError { error ->
                logger.error("Error fetching speed limits: {} - {}", error.javaClass.simpleName, error.message, error)
            }
            .subscribe()
    }

    /**
     * Manually trigger fetching of road objects of a specific type.
     *
     * @param typeId The NVDB type ID of the road objects to fetch
     * @param count Number of objects to fetch
     */
    fun fetchAndProduceVegobjekter(typeId: Int, count: Int = 100): Mono<Long> {
        logger.info("Fetching {} vegobjekter of type {}", count, typeId)
        
        return nvdbApiClient.streamVegobjekter(typeId, count)
            .doOnNext { vegobjekt -> produceToKafka(vegobjekt) }
            .count()
            .doOnSuccess { total -> 
                logger.info("Successfully produced {} vegobjekter to Kafka", total)
            }
    }

    /**
     * Produce a single road object to the Kafka input topic.
     */
    private fun produceToKafka(vegobjekt: Vegobjekt) {
        try {
            val key = vegobjekt.id.toString()
            val value = objectMapper.writeValueAsString(vegobjekt)
            
            kafkaTemplate.send(inputTopic, key, value)
                .whenComplete { result, ex ->
                    if (ex != null) {
                        logger.error("Failed to produce vegobjekt {}: {}", vegobjekt.id, ex.message)
                    } else {
                        logger.debug("Produced vegobjekt {} to partition {}", 
                            vegobjekt.id, 
                            result?.recordMetadata?.partition()
                        )
                    }
                }
        } catch (e: Exception) {
            logger.error("Error serializing vegobjekt {}: {}", vegobjekt.id, e.message)
        }
    }
}
