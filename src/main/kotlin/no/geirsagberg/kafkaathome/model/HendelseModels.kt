package no.geirsagberg.kafkaathome.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class VegobjektHendelse(
    @JsonProperty("hendelseId")
    val hendelseId: Long,

    @JsonProperty("hendelseType")
    val hendelseType: String,

    @JsonProperty("vegobjektId")
    val vegobjektId: Long,

    @JsonProperty("vegobjektTypeId")
    val vegobjektTypeId: Int,

    @JsonProperty("vegobjektVersjon")
    val vegobjektVersjon: Int,

    @JsonProperty("tidspunkt")
    val tidspunkt: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VegobjektHendelserResponse(
    @JsonProperty("hendelser")
    val hendelser: List<VegobjektHendelse> = emptyList(),

    @JsonProperty("metadata")
    val metadata: HendelseMetadata? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HendelseMetadata(
    @JsonProperty("returnert")
    val returnert: Int? = null,

    @JsonProperty("sidestorrelse")
    val sidestorrelse: Int? = null,

    @JsonProperty("neste")
    val neste: NesteHendelseLink? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NesteHendelseLink(
    @JsonProperty("start")
    val start: String? = null,

    @JsonProperty("href")
    val href: String? = null
)
