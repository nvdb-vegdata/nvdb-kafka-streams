package no.geirsagberg.kafkaathome.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a road link sequence (Veglenkesekvens) from NVDB Uberiket API.
 * A road link sequence is a continuous sequence of road links that together form
 * a logical road segment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Veglenkesekvens(
    @JsonProperty("veglenkesekvensid")
    val veglenkesekvensId: Long,

    @JsonProperty("startdato")
    val startdato: String? = null,

    @JsonProperty("sluttdato")
    val sluttdato: String? = null,

    @JsonProperty("kortform")
    val kortform: String? = null,

    @JsonProperty("veglenker")
    val veglenker: List<Veglenke> = emptyList(),

    @JsonProperty("geometri")
    val geometri: Geometri? = null
)

/**
 * Represents a single road link (Veglenke) within a road link sequence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Veglenke(
    @JsonProperty("veglenkeid")
    val veglenkeId: Long,

    @JsonProperty("startposisjon")
    val startposisjon: Double? = null,

    @JsonProperty("sluttposisjon")
    val sluttposisjon: Double? = null,

    @JsonProperty("startdato")
    val startdato: String? = null,

    @JsonProperty("sluttdato")
    val sluttdato: String? = null,

    @JsonProperty("typeVeg")
    val typeVeg: String? = null,

    @JsonProperty("detaljnivå")
    val detaljniva: String? = null,

    @JsonProperty("feltoversikt")
    val feltoversikt: List<String>? = null,

    @JsonProperty("geometri")
    val geometri: Geometri? = null
)

/**
 * Represents geometry data in WKT or other formats.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Geometri(
    @JsonProperty("wkt")
    val wkt: String? = null,

    @JsonProperty("srid")
    val srid: Int? = null,

    @JsonProperty("lengde")
    val lengde: Double? = null
)

/**
 * Represents a road object (Vegobjekt) from NVDB Uberiket API.
 * Road objects are features placed along roads, such as speed limits, signs, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Vegobjekt(
    @JsonProperty("id")
    val id: Long,

    @JsonProperty("typeId")
    val typeId: Int? = null,

    @JsonProperty("versjon")
    val versjon: Int? = null,

    @JsonProperty("startdato")
    val startdato: String? = null,

    @JsonProperty("sluttdato")
    val sluttdato: String? = null,

    @JsonProperty("egenskaper")
    val egenskaper: List<Egenskap> = emptyList(),

    @JsonProperty("stedfesting")
    val stedfesting: Stedfesting? = null,

    @JsonProperty("geometri")
    val geometri: Geometri? = null
)

/**
 * Represents a property/attribute of a road object.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Egenskap(
    @JsonProperty("id")
    val id: Long? = null,

    @JsonProperty("navn")
    val navn: String? = null,

    @JsonProperty("verdi")
    val verdi: Any? = null,

    @JsonProperty("datatype")
    val datatype: String? = null
)

/**
 * Represents the location/placement of a road object on the road network.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Stedfesting(
    @JsonProperty("type")
    val type: String? = null,

    @JsonProperty("veglenkesekvensider")
    val veglenkesekvensider: List<VeglenkeStedfesting>? = null
)

/**
 * Represents the placement on a specific road link sequence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VeglenkeStedfesting(
    @JsonProperty("veglenkesekvensid")
    val veglenkesekvensId: Long? = null,

    @JsonProperty("startposisjon")
    val startposisjon: Double? = null,

    @JsonProperty("sluttposisjon")
    val sluttposisjon: Double? = null,

    @JsonProperty("retning")
    val retning: String? = null
)

/**
 * API response wrapper for paginated road object results.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VegobjekterResponse(
    @JsonProperty("objekter")
    val objekter: List<Vegobjekt> = emptyList(),

    @JsonProperty("metadata")
    val metadata: ResponseMetadata? = null
)

/**
 * API response wrapper for paginated road link sequence results.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VeglenkesekvensResponse(
    @JsonProperty("objekter")
    val objekter: List<Veglenkesekvens> = emptyList(),

    @JsonProperty("metadata")
    val metadata: ResponseMetadata? = null
)

/**
 * Metadata for paginated API responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ResponseMetadata(
    @JsonProperty("antall")
    val antall: Int? = null,

    @JsonProperty("returnert")
    val returnert: Int? = null,

    @JsonProperty("neste")
    val neste: NesteLink? = null
)

/**
 * Link to the next page of results.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NesteLink(
    @JsonProperty("start")
    val start: String? = null,

    @JsonProperty("href")
    val href: String? = null
)
