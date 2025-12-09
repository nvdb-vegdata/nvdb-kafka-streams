package no.vegvesen.nvdb.kafka.service

import kotlinx.coroutines.delay
import no.vegvesen.nvdb.kafka.config.KafkaTopicProperties
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.stereotype.Service
import kotlin.math.min

@Service
class KafkaTopicReadinessService(
    private val kafkaAdmin: KafkaAdmin,
    private val topics: List<NewTopic>,
    private val properties: KafkaTopicProperties
) {
    private val logger = LoggerFactory.getLogger(KafkaTopicReadinessService::class.java)

    data class TopicReadinessResult(
        val ready: Boolean,
        val readyTopics: List<String>,
        val missingTopics: List<String>,
        val error: String? = null
    )

    suspend fun verifyAllTopicsExist(): TopicReadinessResult {
        return try {
            val requiredTopicNames = topics.map { it.name() }
            logger.debug("Required topics: {}", requiredTopicNames)

            AdminClient.create(kafkaAdmin.configurationProperties).use { adminClient ->
                val existingTopics = adminClient.listTopics().names().get()
                logger.debug("Existing topics: {}", existingTopics)

                val readyTopics = requiredTopicNames.filter { it in existingTopics }
                val missingTopics = requiredTopicNames.filter { it !in existingTopics }

                TopicReadinessResult(
                    ready = missingTopics.isEmpty(),
                    readyTopics = readyTopics,
                    missingTopics = missingTopics
                )
            }
        } catch (e: Exception) {
            logger.error("Error verifying topics: {}", e.message, e)
            TopicReadinessResult(
                ready = false,
                readyTopics = emptyList(),
                missingTopics = topics.map { it.name() },
                error = e.message
            )
        }
    }

    suspend fun waitUntilTopicsReady(): Boolean {
        val requiredTopicNames = topics.map { it.name() }
        logger.info(
            "Waiting for Kafka topics to be ready: {} (timeout: {}s, max retries: {})",
            requiredTopicNames,
            properties.readiness.timeoutSeconds,
            properties.readiness.maxRetries
        )

        var attempt = 0
        var currentDelay = properties.readiness.initialRetryDelayMs

        while (attempt < properties.readiness.maxRetries) {
            attempt++

            val result = verifyAllTopicsExist()

            if (result.ready) {
                logger.info("All Kafka topics are ready: {}", result.readyTopics)
                return true
            }

            if (attempt % 5 == 0) {
                logger.info(
                    "Still waiting for topics (attempt {}/{}): missing {}",
                    attempt,
                    properties.readiness.maxRetries,
                    result.missingTopics
                )
            } else {
                logger.debug(
                    "Attempt {}/{}: Missing topics: {}",
                    attempt,
                    properties.readiness.maxRetries,
                    result.missingTopics
                )
            }

            if (attempt < properties.readiness.maxRetries) {
                logger.debug("Waiting {}ms before retry...", currentDelay)
                delay(currentDelay)
                currentDelay = min(currentDelay * 2, properties.readiness.maxRetryDelayMs)
            }
        }

        logger.error(
            "Timeout waiting for Kafka topics after {} attempts. Some topics are still missing.",
            properties.readiness.maxRetries
        )
        return false
    }
}
