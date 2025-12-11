package no.vegvesen.nvdb.kafka.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import no.vegvesen.nvdb.kafka.serialization.contextualSerializersModule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


val json = Json {
    ignoreUnknownKeys = true
    serializersModule = contextualSerializersModule
}

@Configuration
class HttpClientConfig(
    @Value($$"${nvdb.api.base-url}") private val baseUrl: String
) {

    private val log = LoggerFactory.getLogger(HttpClientConfig::class.java)

    @Bean
    fun uberiketApiHttpClient() = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 5)
            retryOnException(maxRetries = 5, retryOnTimeout = true)
            exponentialDelay(base = 2.0, maxDelayMs = 30_000)

            modifyRequest { request ->
                log.warn("Retrying API request to ${request.url}")
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 60_000
        }
        defaultRequest {
            url(baseUrl)
            headers.append("Accept", "application/json, application/x-ndjson")
            headers.append("X-Client", "nvdb-kafka")
        }
    }
}
