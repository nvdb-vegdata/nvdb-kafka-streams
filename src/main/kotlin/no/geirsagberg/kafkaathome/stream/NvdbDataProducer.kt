package no.geirsagberg.kafkaathome.stream

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import no.geirsagberg.kafkaathome.api.NvdbApiClient
import no.geirsagberg.kafkaathome.model.ProducerProgress
import no.geirsagberg.kafkaathome.model.Vegobjekt
import no.geirsagberg.kafkaathome.repository.ProducerProgressRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Service that fetches road data from NVDB API and produces it to Kafka topics.
 * This bridges the REST API with Kafka Streams processing.
 */
@Service
@ConditionalOnProperty(name = ["nvdb.producer.enabled"], havingValue = "true")
class NvdbDataProducer(
    private val nvdbApiClient: NvdbApiClient,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val progressRepository: ProducerProgressRepository
) {
    private val logger = LoggerFactory.getLogger(NvdbDataProducer::class.java)

    @Value($$"${kafka.topics.input:nvdb-vegobjekter-raw}")
    private lateinit var inputTopic: String

    @Value($$"${nvdb.producer.enabled:false}")
    private var producerEnabled: Boolean = false

    @Value($$"${nvdb.producer.batch-size:100}")
    private var batchSize: Int = 100

    /**
     * Scheduled task that periodically fetches speed limits from NVDB and produces to Kafka.
     * Uses fixedRate for more predictable scheduling (runs every hour by default).
     * Implements pagination to fetch all speed limits, resuming from last progress.
     */
    @Scheduled(fixedRateString = $$"${nvdb.producer.interval-ms:3600000}")
    fun fetchAndProduceSpeedLimits() {
        if (!producerEnabled) {
            logger.debug("NVDB producer is disabled")
            return
        }

        logger.info("Starting to fetch speed limits from NVDB API with pagination")
        fetchAndProduceVegobjekterWithPagination(NvdbApiClient.TYPE_FARTSGRENSE, null)
    }

    /**
     * Manually trigger fetching road objects of a specific type.
     * Implements pagination to fetch all objects, resuming from last progress if maxCount is null.
     *
     * @param typeId The NVDB type ID of the road objects to fetch
     * @param maxCount Maximum number of objects to fetch (null for all remaining)
     * @return Total number of objects produced
     */
    fun fetchAndProduceVegobjekter(typeId: Int, maxCount: Int? = null): Long {
        logger.info("Manually fetching vegobjekter of type {} (maxCount: {})", typeId, maxCount)
        return fetchAndProduceVegobjekterWithPagination(typeId, maxCount)
    }

    /**
     * Internal method that handles pagination for fetching and producing vegobjekter.
     *
     * @param typeId The NVDB type ID of the road objects to fetch
     * @param maxCount Maximum number of objects to fetch (null for unlimited)
     * @return Total number of objects produced
     */
    private fun fetchAndProduceVegobjekterWithPagination(typeId: Int, maxCount: Int?): Long = runBlocking {
        val progress = progressRepository.findByTypeId(typeId)
        var start = progress?.lastProcessedId
        var totalProduced = 0L
        val lastId = AtomicLong(start ?: 0)

        logger.info("Starting pagination for type {} from ID {}", typeId, start)

        try {
            do {
                val count = nvdbApiClient.streamVegobjekter(typeId, batchSize, start)
                    .onEach { vegobjekt ->
                        produceToKafka(vegobjekt)
                        lastId.set(vegobjekt.id)
                    }
                    .count()

                totalProduced += count

                if (count > 0) {
                    start = lastId.get()
                    val newProgress = ProducerProgress(
                        typeId = typeId,
                        lastProcessedId = start,
                        updatedAt = Instant.now()
                    )
                    progressRepository.save(newProgress)
                    logger.info("Saved progress for type {}: last ID = {}, batch count = {}", typeId, start, count)
                }

                val shouldContinue = count == batchSize &&
                    (maxCount == null || totalProduced < maxCount)

                if (!shouldContinue) break

            } while (true)

            logger.info("Completed fetching type {}: produced {} total vegobjekter", typeId, totalProduced)
            return@runBlocking totalProduced

        } catch (e: Exception) {
            logger.error("Error during pagination for type {}: {}", typeId, e.message, e)
            if (lastId.get() > 0) {
                val newProgress = ProducerProgress(
                    typeId = typeId,
                    lastProcessedId = lastId.get(),
                    updatedAt = Instant.now()
                )
                progressRepository.save(newProgress)
                logger.info("Saved progress on error for type {}: last ID = {}", typeId, lastId.get())
            }
            throw e
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
