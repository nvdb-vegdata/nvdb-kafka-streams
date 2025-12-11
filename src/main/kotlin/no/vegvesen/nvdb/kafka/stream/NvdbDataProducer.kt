package no.vegvesen.nvdb.kafka.stream

import io.github.nomisRev.kafka.publisher.KafkaPublisher
import kotlinx.coroutines.flow.takeWhile
import no.vegvesen.nvdb.api.uberiket.model.*
import no.vegvesen.nvdb.kafka.api.NvdbApiClient
import no.vegvesen.nvdb.kafka.extensions.associate
import no.vegvesen.nvdb.kafka.model.*
import no.vegvesen.nvdb.kafka.model.Vegobjekt
import no.vegvesen.nvdb.kafka.repository.ProducerProgressRepository
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant.now
import java.util.concurrent.atomic.AtomicBoolean
import no.vegvesen.nvdb.api.uberiket.model.Vegobjekt as ApiVegobjekt

/**
 * Service that manages NVDB data ingestion with two operational modes:
 * - BACKFILL: Initial bulk fetch of all vegobjekter for a type
 * - UPDATES: Continuous polling of hendelser (events) for incremental changes
 */
@Service
@ConditionalOnProperty(name = ["nvdb.producer.enabled"], havingValue = "true")
class NvdbDataProducer(
    private val nvdbApiClient: NvdbApiClient,
    private val kafkaPublisher: KafkaPublisher<Long, VegobjektDelta>,
    private val progressRepository: ProducerProgressRepository,

    @Value($$"${nvdb.producer.backfill.batch-size}")
    private val backfillBatchSize: Int,

    @Value($$"${nvdb.producer.updates.batch-size}")
    private val updatesBatchSize: Int

) : SmartLifecycle {
    private val logger = LoggerFactory.getLogger(NvdbDataProducer::class.java)

    @Value($$"${nvdb.producer.enabled:false}")
    private var producerEnabled: Boolean = false

    private val isRunning = AtomicBoolean(false)
    private val isProcessingType915 = AtomicBoolean(false)
    private val isProcessingType916 = AtomicBoolean(false)

    @Scheduled(fixedRateString = "5s")
    suspend fun processType915() {
        if (!producerEnabled || !isRunning.get()) return
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

    @Scheduled(fixedRateString = "5s")
    suspend fun processType916() {
        if (!producerEnabled || !isRunning.get()) return
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
     * In BACKFILL mode, continues processing batches until completion or error.
     */
    private suspend fun processType(typeId: Int) {
        var progress = progressRepository.findByTypeId(typeId)

        when (progress?.mode) {
            ProducerMode.BACKFILL -> {
                logger.debug("Processing type {} in BACKFILL mode", typeId)
                while (progress?.mode == ProducerMode.BACKFILL && isRunning.get()) {
                    progress = runBackfillBatch(typeId, progress)
                    if (progress.lastError != null) {
                        logger.warn("Stopping backfill for type {} due to error: {}", typeId, progress.lastError)
                        break
                    }
                }
                if (!isRunning.get()) {
                    logger.info("Backfill for type {} stopped due to shutdown", typeId)
                } else if (progress.mode != ProducerMode.BACKFILL) {
                    logger.info("Type {} backfill complete, now in {} mode", typeId, progress.mode)
                }
            }

            ProducerMode.UPDATES -> {
                logger.debug("Processing type {} in UPDATES mode", typeId)
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
    private suspend fun runBackfillBatch(typeId: Int, progress: ProducerProgress): ProducerProgress {
        try {
            val start = progress.lastProcessedId
            val topic = NvdbApiClient.getTopicNameForType(typeId)
            var lastId: Long? = null
            var count = 0

            // publishScope automatically awaits all sends before returning
            kafkaPublisher.publishScope {
                nvdbApiClient.streamVegobjekter(typeId, backfillBatchSize, start)
                    .takeWhile { isRunning.get() }
                    .collect { apiVegobjekt ->
                        val vegobjekt = toDomain(apiVegobjekt)
                        val delta = VegobjektDelta(before = null, after = vegobjekt)

                        offer(ProducerRecord(topic, vegobjekt.vegobjektId, delta))

                        lastId = vegobjekt.vegobjektId
                        count++
                    }
            }
            // All sends acknowledged by this point (at-least-once guaranteed)

            if (!isRunning.get()) {
                logger.info("Backfill batch for type {} interrupted by shutdown after {} items", typeId, count)
                val interruptedProgress = progress.copy(
                    lastProcessedId = lastId,
                    updatedAt = now()
                )
                progressRepository.save(interruptedProgress)
                return interruptedProgress
            }

            if (count == 0) {
                val updatedProgress = progress.copy(
                    mode = ProducerMode.UPDATES,
                    backfillCompletionTime = now(),
                    lastError = null,
                    updatedAt = now()
                )
                progressRepository.save(updatedProgress)
                logger.info(
                    "Backfill complete for type {}, transitioning to UPDATES mode from hendelse ID {}",
                    typeId,
                    progress.hendelseId
                )
                return updatedProgress
            }

            val newProgress = progress.copy(
                lastProcessedId = lastId!!,
                lastError = null,
                updatedAt = now()
            )
            progressRepository.save(newProgress)
            logger.info(
                "Backfill batch for type {}: processed {} items, last ID = {}",
                typeId, count, lastId
            )
            return newProgress

        } catch (e: Exception) {
            logger.error("Error during backfill for type {}: {}", typeId, e.message, e)
            val errorProgress = progress.copy(
                lastError = e.message,
                updatedAt = now()
            )
            progressRepository.save(errorProgress)
            return errorProgress
        }
    }

    private fun toDomain(apiVegobjekt: ApiVegobjekt): Vegobjekt = Vegobjekt(
        vegobjektId = apiVegobjekt.id,
        vegobjektType = apiVegobjekt.typeId,
        egenskaper = apiVegobjekt.egenskaper!!.associate { (key, value) ->
            key.toInt() to when (value) {
                is HeltallEgenskap -> value.verdi.toString()
                is TekstEgenskap -> value.verdi
                is EnumEgenskap -> value.verdi.toString()
                else -> error("unexpected egenskap type: ${value::class.simpleName}")
            }
        },
        stedfestinger = apiVegobjekt.stedfesting!!.let {
            when (it) {
                is StedfestingLinjer -> it.linjer.map {
                    Utstrekning(
                        veglenkesekvensId = it.id,
                        startposisjon = it.startposisjon,
                        sluttposisjon = it.sluttposisjon
                    )
                }

                else -> error("unexpected stedfesting type: ${it::class.simpleName}")
            }
        }
    )

    /**
     * Updates mode: Stream hendelser and fetch full vegobjekt details.
     */
    private suspend fun runUpdatesCheck(typeId: Int, progress: ProducerProgress) {
        try {
            val startHendelseId = progress.hendelseId
            if (startHendelseId == null) {
                logger.error("No hendelse ID stored for type {} in UPDATES mode", typeId)
                return
            }

            var lastHendelseId = startHendelseId
            val topic = NvdbApiClient.getTopicNameForType(typeId)
            var count = 0

            // Wrap all sends in publishScope for at-least-once delivery
            kafkaPublisher.publishScope {
                nvdbApiClient.streamVegobjektHendelser(typeId, updatesBatchSize, startHendelseId)
                    .takeWhile { isRunning.get() }
                    .collect { deltaHendelse ->
                        try {
                            val delta = when (val hendelseData = deltaHendelse.data) {
                                is VegobjektVersjonOpprettet -> {
                                    hendelseData.opprettetToDelta(deltaHendelse)
                                }

                                is VegobjektVersjonEndret -> {
                                    hendelseData.endretToDelta(deltaHendelse)
                                }

                                is VegobjektVersjonFjernet -> {
                                    hendelseData.fjernetToDelta(deltaHendelse)
                                }

                                else -> {
                                    logger.warn("Unknown hendelse type: ${hendelseData::class.simpleName}, skipping")
                                    null
                                }
                            }

                            if (delta != null) {
                                offer(ProducerRecord(topic, deltaHendelse.vegobjektId, delta))
                                lastHendelseId = deltaHendelse.hendelseId
                                count++
                            }
                        } catch (e: Exception) {
                            logger.error(
                                "Error processing hendelse {} for vegobjekt {}: {}",
                                deltaHendelse.hendelseId, deltaHendelse.vegobjektId, e.message, e
                            )
                            // Continue processing other hendelser
                        }
                    }
            }
            // All sends acknowledged before saving progress

            if (count > 0) {
                val newProgress = progress.copy(
                    hendelseId = lastHendelseId,
                    updatedAt = now()
                )
                progressRepository.save(newProgress)
                if (!isRunning.get()) {
                    logger.info("Updates for type {} interrupted by shutdown after {} hendelser", typeId, count)
                } else {
                    logger.info(
                        "Updates processed for type {}, {} hendelser, last ID = {}",
                        typeId,
                        count,
                        lastHendelseId
                    )
                }
            } else {
                logger.debug("No new hendelser for type {}", typeId)
            }

        } catch (e: Exception) {
            logger.error("Error during updates check for type {}: {}", typeId, e.message, e)
            progressRepository.save(
                progress.copy(
                    lastError = e.message,
                    updatedAt = now()
                )
            )
        }
    }

    private fun VegobjektVersjonFjernet.fjernetToDelta(
        deltaHendelse: VegobjektDeltaHendelse
    ): VegobjektDelta {
        val originalVersjon = originalVersjon
            ?: error("originalVersjon is null for VegobjektVersjonFjernet, hendelseId=${deltaHendelse.hendelseId}, vegobjektId=${deltaHendelse.vegobjektId}")

        val before = toDomain(
            originalVersjon,
            deltaHendelse.vegobjektId,
            deltaHendelse.vegobjektType
        )
        return VegobjektDelta(before = before, after = null)
    }

    private fun VegobjektVersjonEndret.endretToDelta(
        deltaHendelse: VegobjektDeltaHendelse
    ): VegobjektDelta {
        val originalVersjon = originalVersjon
            ?: error("originalVersjon is null for VegobjektVersjonEndret, hendelseId=${deltaHendelse.hendelseId}, vegobjektId=${deltaHendelse.vegobjektId}")

        val before = toDomain(
            originalVersjon,
            deltaHendelse.vegobjektId,
            deltaHendelse.vegobjektType
        )
        val afterVersjon = applyChanges(originalVersjon, this)
        val after =
            toDomain(afterVersjon, deltaHendelse.vegobjektId, deltaHendelse.vegobjektType)

        return VegobjektDelta(before = before, after = after)
    }

    private fun VegobjektVersjonOpprettet.opprettetToDelta(
        deltaHendelse: VegobjektDeltaHendelse
    ): VegobjektDelta {
        val after = toDomain(
            versjon,
            deltaHendelse.vegobjektId,
            deltaHendelse.vegobjektType
        )
        return VegobjektDelta(before = null, after = after)
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
        val latestHendelseId = nvdbApiClient.getLatestVegobjektHendelseId(typeId)

        val now = now()

        val progress = ProducerProgress(
            typeId = typeId,
            mode = ProducerMode.BACKFILL,
            lastProcessedId = null,
            hendelseId = latestHendelseId,
            backfillStartTime = now,
            backfillCompletionTime = null,
            lastError = null,
            updatedAt = now
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

    private fun shutdown() {
        logger.info("Initiating graceful shutdown of NvdbDataProducer...")
        isRunning.set(false)

        val shutdownStartTime = System.currentTimeMillis()
        val maxWaitMillis = 30000L

        while ((isProcessingType915.get() || isProcessingType916.get()) &&
            System.currentTimeMillis() - shutdownStartTime < maxWaitMillis
        ) {
            logger.info("Waiting for in-flight processing to complete...")
            Thread.sleep(500)
        }

        if (isProcessingType915.get() || isProcessingType916.get()) {
            logger.warn("Shutdown timeout reached, some processing may have been interrupted")
        } else {
            logger.info("All in-flight processing completed successfully")
        }

        logger.info("NvdbDataProducer shutdown complete")
    }

    // SmartLifecycle implementation for coordinated shutdown with Tomcat
    override fun start() {
        logger.info("Starting NvdbDataProducer lifecycle")
        isRunning.set(true)
    }

    override fun stop(callback: Runnable) {
        logger.info("Stopping NvdbDataProducer via SmartLifecycle (async)")
        Thread {
            try {
                shutdown()
            } finally {
                callback.run()
            }
        }.start()
    }

    override fun stop() {
        logger.info("Stopping NvdbDataProducer via SmartLifecycle (sync)")
        shutdown()
    }

    override fun isRunning(): Boolean = isRunning.get()

    override fun getPhase(): Int = Integer.MAX_VALUE - 1000

    override fun isAutoStartup(): Boolean = true
}
