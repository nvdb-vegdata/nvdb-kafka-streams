package no.vegvesen.nvdb.kafka.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kafka.topics")
data class KafkaTopicProperties(
    val partitions: Int = 5,
    val replicas: Short = 1,
    val readiness: ReadinessProperties = ReadinessProperties()
)

data class ReadinessProperties(
    val enabled: Boolean = true,
    val timeoutSeconds: Long = 60,
    val maxRetries: Int = 20,
    val initialRetryDelayMs: Long = 100,
    val maxRetryDelayMs: Long = 5000,
    val failOnStartupTimeout: Boolean = true
)
