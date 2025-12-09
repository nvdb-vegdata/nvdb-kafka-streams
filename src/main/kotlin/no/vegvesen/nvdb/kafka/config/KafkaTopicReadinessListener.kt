package no.vegvesen.nvdb.kafka.config

import kotlinx.coroutines.runBlocking
import no.vegvesen.nvdb.kafka.service.KafkaTopicReadinessService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["kafka.topics.readiness.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class KafkaTopicReadinessListener(
    private val topicReadinessService: KafkaTopicReadinessService,
    private val properties: KafkaTopicProperties
) {
    private val logger = LoggerFactory.getLogger(KafkaTopicReadinessListener::class.java)

    @EventListener(ApplicationStartedEvent::class)
    fun onApplicationStarted(event: ApplicationStartedEvent) = runBlocking {
        logger.info("Verifying Kafka topic readiness before application becomes ready...")

        val success = topicReadinessService.waitUntilTopicsReady()

        if (success) {
            logger.info("Kafka topic readiness check passed. Application is ready to process scheduled tasks.")
        } else {
            val message = "Kafka topics are not ready after timeout (${properties.readiness.timeoutSeconds}s)"
            if (properties.readiness.failOnStartupTimeout) {
                logger.error("$message. Failing application startup.")
                throw IllegalStateException(message)
            } else {
                logger.warn("$message. Continuing with degraded functionality.")
            }
        }
    }
}
