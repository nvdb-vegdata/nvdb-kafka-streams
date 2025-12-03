package no.geirsagberg.kafkaathome.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "nvdb.api")
data class NvdbApiProperties(
    val baseUrl: String = "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/"
)
