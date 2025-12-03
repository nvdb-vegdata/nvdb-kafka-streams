package no.geirsagberg.kafkaathome.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(NvdbApiProperties::class)
class WebClientConfig(private val nvdbApiProperties: NvdbApiProperties) {

    @Bean
    fun nvdbWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(nvdbApiProperties.baseUrl)
            .defaultHeader("Accept", "application/json")
            .build()
    }
}
