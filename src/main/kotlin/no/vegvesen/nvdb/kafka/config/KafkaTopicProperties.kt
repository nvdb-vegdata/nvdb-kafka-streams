package no.vegvesen.nvdb.kafka.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kafka.topics")
data class KafkaTopicProperties(
    val partitions: Int,
    val replicas: Short,
    val readiness: ReadinessProperties
)

data class ReadinessProperties(
    val enabled: Boolean,
    val timeoutSeconds: Long,
    val maxRetries: Int,
    val initialRetryDelayMs: Long,
    val maxRetryDelayMs: Long,
    val failOnStartupTimeout: Boolean
)
