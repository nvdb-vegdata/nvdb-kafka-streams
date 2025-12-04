package no.geirsagberg.kafkaathome.stream

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import no.geirsagberg.kafkaathome.api.NvdbApiClient
import no.geirsagberg.kafkaathome.model.ProducerMode
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Service that manages NVDB data ingestion with two operational modes:
 * - BACKFILL: Initial bulk fetch of all vegobjekter for a type
 * - UPDATES: Continuous polling of hendelser (events) for incremental changes
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

    @Value("\${nvdb.producer.enabled:false}")
    private var producerEnabled: Boolean = false

    @Value("\${nvdb.producer.backfill.batch-size:100}")
    private var backfillBatchSize: Int = 100

    @Value("\${nvdb.producer.updates.batch-size:100}")
    private var updatesBatchSize: Int = 100

    private val isProcessingType915 = AtomicBoolean(false)
    private val isProcessingType916 = AtomicBoolean(false)

    /**
     * Scheduled task for type 915 (Vegsystem).
     * Runs every minute, but actual work depends on mode.
     */
    @Scheduled(fixedRateString = "\${nvdb.producer.schedule.type915:60000}")
    fun processType915() {
        if (!producerEnabled) return
        if (!isProcessingType915.compareAndSet(false, true)) {
            logger.debug("Type 915 processing already in progress, skipping")
            return
        }

        try {
            processType(NvdbApiClient.TYPE_VEGSYSTEM)
        } finally {
            isProcessingType915.set(false)
        }
    }

    /**
     * Scheduled task for type 916 (Strekning).
     * Runs every minute, but actual work depends on mode.
     */
    @Scheduled(fixedRateString = "\${nvdb.producer.schedule.type916:60000}")
    fun processType916() {
        if (!producerEnabled) return
        if (!isProcessingType916.compareAndSet(false, true)) {
            logger.debug("Type 916 processing already in progress, skipping")
            return
        }

        try {
            processType(NvdbApiClient.TYPE_STREKNING)
        } finally {
            isProcessingType916.set(false)
        }
    }

    /**
     * Main processing logic: delegates to backfill or updates based on current mode.
     */
    private fun processType(typeId: Int) {
        val progress = progressRepository.findByTypeId(typeId)

        when (progress?.mode) {
            ProducerMode.BACKFILL -> {
                logger.info("Processing type {} in BACKFILL mode", typeId)
                runBackfillBatch(typeId, progress)
            }
            ProducerMode.UPDATES -> {
                logger.info("Processing type {} in UPDATES mode", typeId)
                runUpdatesCheck(typeId, progress)
            }
            null -> {
                logger.debug("Type {} not initialized, skipping", typeId)
            }
        }
    }

    /**
     * Backfill mode: Fetch vegobjekter using pagination.
     * Processes one batch per invocation, saving progress after each batch.
     */
    private fun runBackfillBatch(typeId: Int, progress: ProducerProgress) = runBlocking {
        try {
            val start = progress.lastProcessedId
            val lastId = AtomicLong(start ?: 0)

            val count = nvdbApiClient.streamVegobjekter(typeId, backfillBatchSize, start)
                .onEach { vegobjekt ->
                    produceToKafka(typeId, vegobjekt)
                    lastId.set(vegobjekt.id)
                }
                .count()

            if (count > 0) {
                val newProgress = progress.copy(
                    lastProcessedId = lastId.get(),
                    updatedAt = Instant.now()
                )
                progressRepository.save(newProgress)
                logger.info("Backfill batch for type {}: processed {} items, last ID = {}",
                    typeId, count, lastId.get())
            }

            // Check if backfill is complete (batch < batch size)
            if (count < backfillBatchSize) {
                logger.info("Backfill complete for type {}, transitioning to UPDATES mode", typeId)
                transitionToUpdatesMode(typeId, progress)
            }

        } catch (e: Exception) {
            logger.error("Error during backfill for type {}: {}", typeId, e.message, e)
            progressRepository.save(progress.copy(
                lastError = e.message,
                updatedAt = Instant.now()
            ))
        }
    }

    /**
     * Transition from BACKFILL to UPDATES mode.
     * Queries the latest hendelse ID to start polling from.
     */
    private fun transitionToUpdatesMode(typeId: Int, progress: ProducerProgress) = runBlocking {
        val latestHendelseId = nvdbApiClient.getLatestHendelseId(typeId)
        if (latestHendelseId == null) {
            logger.error("Failed to fetch latest hendelse ID for type {}, cannot transition to UPDATES", typeId)
            return@runBlocking
        }

        val updatedProgress = progress.copy(
            mode = ProducerMode.UPDATES,
            hendelseId = latestHendelseId,
            backfillCompletionTime = Instant.now(),
            updatedAt = Instant.now()
        )
        progressRepository.save(updatedProgress)
        logger.info("Transitioned type {} to UPDATES mode, starting from hendelse ID {}",
            typeId, latestHendelseId)
    }

    /**
     * Updates mode: Poll hendelser endpoint and fetch full vegobjekt details.
     */
    private fun runUpdatesCheck(typeId: Int, progress: ProducerProgress) = runBlocking {
        try {
            val startHendelseId = progress.hendelseId
            if (startHendelseId == null) {
                logger.error("No hendelse ID stored for type {} in UPDATES mode", typeId)
                return@runBlocking
            }

            val response = nvdbApiClient.fetchHendelser(typeId, startHendelseId, updatesBatchSize)
            val hendelser = response.hendelser

            if (hendelser.isEmpty()) {
                logger.debug("No new hendelser for type {}", typeId)
                return@runBlocking
            }

            logger.info("Fetched {} hendelser for type {}", hendelser.size, typeId)

            // Process each hendelse: fetch full vegobjekt and produce to Kafka
            var lastHendelseId = startHendelseId
            for (hendelse in hendelser) {
                try {
                    val vegobjekt = nvdbApiClient.getVegobjekt(typeId, hendelse.vegobjektId)
                    produceToKafka(typeId, vegobjekt)
                    lastHendelseId = hendelse.hendelseId
                } catch (e: Exception) {
                    logger.error("Error fetching vegobjekt {} for hendelse {}: {}",
                        hendelse.vegobjektId, hendelse.hendelseId, e.message)
                    // Continue processing other hendelser
                }
            }

            // Save progress with last processed hendelse ID
            val newProgress = progress.copy(
                hendelseId = lastHendelseId,
                updatedAt = Instant.now()
            )
            progressRepository.save(newProgress)
            logger.info("Updates processed for type {}, last hendelse ID = {}", typeId, lastHendelseId)

        } catch (e: Exception) {
            logger.error("Error during updates check for type {}: {}", typeId, e.message, e)
            progressRepository.save(progress.copy(
                lastError = e.message,
                updatedAt = Instant.now()
            ))
        }
    }

    /**
     * Start backfill for a specific type.
     * Queries the latest hendelse ID and stores it before starting backfill.
     */
    suspend fun startBackfill(typeId: Int) {
        val existing = progressRepository.findByTypeId(typeId)
        if (existing != null && existing.mode == ProducerMode.BACKFILL) {
            logger.warn("Backfill already in progress for type {}", typeId)
            return
        }

        // Query latest hendelse ID before starting backfill
        val latestHendelseId = nvdbApiClient.getLatestHendelseId(typeId)
        if (latestHendelseId == null) {
            logger.error("Failed to fetch latest hendelse ID for type {}, cannot start backfill", typeId)
            throw IllegalStateException("Cannot fetch latest hendelse ID")
        }

        val progress = ProducerProgress(
            typeId = typeId,
            mode = ProducerMode.BACKFILL,
            lastProcessedId = null,
            hendelseId = latestHendelseId,
            backfillStartTime = Instant.now(),
            backfillCompletionTime = null,
            lastError = null,
            updatedAt = Instant.now()
        )
        progressRepository.save(progress)
        logger.info("Started backfill for type {}, stored hendelse ID {} for later", typeId, latestHendelseId)
    }

    /**
     * Stop/pause backfill for a type by deleting progress record.
     */
    fun stopBackfill(typeId: Int) {
        progressRepository.deleteByTypeId(typeId)
        logger.info("Stopped backfill for type {}", typeId)
    }

    /**
     * Reset backfill: delete existing progress and restart.
     */
    suspend fun resetBackfill(typeId: Int) {
        progressRepository.deleteByTypeId(typeId)
        logger.info("Reset backfill for type {}", typeId)
        startBackfill(typeId)
    }

    /**
     * Get current status for a type.
     */
    fun getStatus(typeId: Int): ProducerProgress? {
        return progressRepository.findByTypeId(typeId)
    }

    /**
     * Produce a vegobjekt to its type-specific Kafka topic.
     */
    private fun produceToKafka(typeId: Int, vegobjekt: Vegobjekt) {
        try {
            val topic = NvdbApiClient.getTopicNameForType(typeId)
            val key = vegobjekt.id.toString()
            val value = objectMapper.writeValueAsString(vegobjekt)

            kafkaTemplate.send(topic, key, value)
                .whenComplete { result, ex ->
                    if (ex != null) {
                        logger.error("Failed to produce vegobjekt {} to topic {}: {}",
                            vegobjekt.id, topic, ex.message)
                    } else {
                        logger.debug("Produced vegobjekt {} to topic {} partition {}",
                            vegobjekt.id,
                            topic,
                            result?.recordMetadata?.partition()
                        )
                    }
                }
        } catch (e: Exception) {
            logger.error("Error serializing vegobjekt {}: {}", vegobjekt.id, e.message)
        }
    }
}
