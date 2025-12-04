package no.geirsagberg.kafkaathome.model

import java.time.Instant

enum class ProducerMode {
    BACKFILL,
    UPDATES
}

data class ProducerProgress(
    val typeId: Int,
    val mode: ProducerMode,
    val lastProcessedId: Long?,
    val hendelseId: Long?,
    val backfillStartTime: Instant?,
    val backfillCompletionTime: Instant?,
    val lastError: String?,
    val updatedAt: Instant
)
