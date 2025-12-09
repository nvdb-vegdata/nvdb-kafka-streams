package no.vegvesen.nvdb.kafka.health

import kotlinx.coroutines.runBlocking
import no.vegvesen.nvdb.kafka.service.KafkaTopicReadinessService
import org.apache.kafka.clients.admin.AdminClient
import org.slf4j.LoggerFactory
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component("kafkaTopics")
@ConditionalOnProperty(
    name = ["management.health.kafka-topics.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class KafkaTopicHealthIndicator(
    private val topicReadinessService: KafkaTopicReadinessService,
    private val kafkaAdmin: KafkaAdmin
) : HealthIndicator {
    private val logger = LoggerFactory.getLogger(KafkaTopicHealthIndicator::class.java)

    @Volatile
    private var cachedHealth: Health? = null

    @Volatile
    private var cacheTimestamp: Instant = Instant.MIN

    private val cacheDurationSeconds = 10L

    override fun health(): Health {
        val now = Instant.now()
        val cached = cachedHealth

        if (cached != null && ChronoUnit.SECONDS.between(cacheTimestamp, now) < cacheDurationSeconds) {
            logger.debug("Returning cached health status")
            return cached
        }

        logger.debug("Cache expired or empty, checking Kafka health")
        return runBlocking {
            val newHealth = checkHealth()
            cachedHealth = newHealth
            cacheTimestamp = now
            newHealth
        }
    }

    private suspend fun checkHealth(): Health {
        return try {
            var brokerConnected = false
            var brokerAddress = ""

            AdminClient.create(kafkaAdmin.configurationProperties).use { adminClient ->
                val clusterDescription = adminClient.describeCluster()
                val nodes = clusterDescription.nodes().get()
                brokerConnected = nodes.isNotEmpty()
                brokerAddress = nodes.firstOrNull()?.host() ?: "unknown"
            }

            val topicResult = topicReadinessService.verifyAllTopicsExist()

            val builder = if (brokerConnected && topicResult.ready) {
                Health.up()
            } else {
                Health.down()
            }

            builder
                .withDetail("broker", brokerAddress)
                .withDetail("connected", brokerConnected)
                .withDetail("requiredTopics", topicResult.readyTopics + topicResult.missingTopics)
                .withDetail("readyTopics", topicResult.readyTopics)
                .withDetail("missingTopics", topicResult.missingTopics)
                .withDetail("lastChecked", Instant.now().toString())
                .apply {
                    if (topicResult.error != null) {
                        withDetail("error", topicResult.error)
                    }
                }
                .build()
        } catch (e: Exception) {
            logger.error("Error checking Kafka health: {}", e.message, e)
            Health.down()
                .withDetail("error", e.message ?: "Unknown error")
                .withDetail("lastChecked", Instant.now().toString())
                .build()
        }
    }
}
