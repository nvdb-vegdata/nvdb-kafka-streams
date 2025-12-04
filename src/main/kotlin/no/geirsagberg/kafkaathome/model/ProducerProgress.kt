package no.geirsagberg.kafkaathome.model

import java.time.Instant

data class ProducerProgress(
    val typeId: Int,
    val lastProcessedId: Long,
    val updatedAt: Instant
)
