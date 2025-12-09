package no.vegvesen.nvdb.kafka.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/**
 * Serializer for handling Any type fields using KotlinX Serialization.
 * Serializes to JsonElement for flexible handling of dynamic data.
 */
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("Any", PolymorphicKind.SEALED)

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as JsonEncoder
        val element = when (value) {
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { 
                it.key.toString() to jsonEncoder.json.encodeToJsonElement(it.value ?: JsonNull) 
            })
            is List<*> -> JsonArray(value.map { 
                jsonEncoder.json.encodeToJsonElement(it ?: JsonNull) 
            })
            else -> JsonPrimitive(value.toString())
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> element.content
            is JsonObject -> element.toString()
            is JsonArray -> element.toString()
        }
    }
}

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
 * Represents a single road link (Veglenke) from the NVDB vegnett API.
 * Road links are the atomic segments that make up the road network.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Veglenke(
    @JsonProperty("veglenkesekvensId")
    val veglenkesekvensId: Long? = null,

    @JsonProperty("veglenkenummer")
    val veglenkenummer: Long? = null,

    @JsonProperty("gyldighetsperiode")
    val gyldighetsperiode: Gyldighetsperiode? = null,

    @JsonProperty("konnektering")
    val konnektering: Boolean? = null,

    @JsonProperty("topologiniva")
    val topologiniva: String? = null,

    @JsonProperty("maledato")
    val maledato: String? = null,

    @JsonProperty("malemetode")
    val malemetode: String? = null,

    @JsonProperty("detaljniva")
    val detaljniva: String? = null,

    @JsonProperty("typeVeg")
    val typeVeg: String? = null,

    @JsonProperty("startnode")
    val startnode: Long? = null,

    @JsonProperty("sluttnode")
    val sluttnode: Long? = null,

    @JsonProperty("startposisjon")
    val startposisjon: Double? = null,

    @JsonProperty("sluttposisjon")
    val sluttposisjon: Double? = null,

    @JsonProperty("kommune")
    val kommune: Int? = null,

    @JsonProperty("geometri")
    val geometri: Geometri? = null,

    @JsonProperty("lengde")
    val lengde: Double? = null,

    @JsonProperty("feltoversikt")
    val feltoversikt: List<String>? = null
)

/**
 * Represents geometry data in WKT or other formats.
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class Geometri(
    @SerialName("wkt")
    @JsonProperty("wkt")
    val wkt: String? = null,

    @SerialName("srid")
    @JsonProperty("srid")
    val srid: Int? = null,

    @SerialName("lengde")
    @JsonProperty("lengde")
    val lengde: Double? = null,

    @SerialName("datafangstdato")
    @JsonProperty("datafangstdato")
    val datafangstdato: String? = null,

    @SerialName("temakode")
    @JsonProperty("temakode")
    val temakode: Int? = null,

    @SerialName("kommune")
    @JsonProperty("kommune")
    val kommune: Int? = null,

    @SerialName("oppdateringsdato")
    @JsonProperty("oppdateringsdato")
    val oppdateringsdato: String? = null,

    @SerialName("kvalitet")
    @JsonProperty("kvalitet")
    val kvalitet: GeometriKvalitet? = null
)

/**
 * Represents quality metadata for geometry data.
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeometriKvalitet(
    @SerialName("malemetode")
    @JsonProperty("malemetode")
    val malemetode: Int? = null,

    @SerialName("malemetodeHoyde")
    @JsonProperty("malemetodeHoyde")
    val malemetodeHoyde: Int? = null,

    @SerialName("noyaktighet")
    @JsonProperty("noyaktighet")
    val noyaktighet: Int? = null,

    @SerialName("noyaktighetHoyde")
    @JsonProperty("noyaktighetHoyde")
    val noyaktighetHoyde: Int? = null,

    @SerialName("synbarhet")
    @JsonProperty("synbarhet")
    val synbarhet: Int? = null,

    @SerialName("maksimaltAvvik")
    @JsonProperty("maksimaltAvvik")
    val maksimaltAvvik: Int? = null
)

/**
 * Represents a road object (Vegobjekt) from NVDB Uberiket API.
 * Road objects are features placed along roads, such as speed limits, signs, etc.
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class Vegobjekt(
    @SerialName("id")
    @JsonProperty("id")
    val id: Long,

    @SerialName("typeId")
    @JsonProperty("typeId")
    val typeId: Int? = null,

    @SerialName("versjon")
    @JsonProperty("versjon")
    val versjon: Int? = null,

    @SerialName("gyldighetsperiode")
    @JsonProperty("gyldighetsperiode")
    val gyldighetsperiode: Gyldighetsperiode? = null,

    @SerialName("egenskaper")
    @JsonProperty("egenskaper")
    val egenskaper: Map<String, EgenskapValue>? = null,

    @SerialName("barn")
    @JsonProperty("barn")
    val barn: Map<String, @Serializable(with = AnySerializer::class) Any>? = null,

    @SerialName("stedfesting")
    @JsonProperty("stedfesting")
    val stedfesting: Stedfesting? = null,

    @SerialName("geometri")
    @JsonProperty("geometri")
    val geometri: Geometri? = null,

    @SerialName("sistEndret")
    @JsonProperty("sistEndret")
    val sistEndret: String? = null
)

/**
 * Represents the validity period of a road object.
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class Gyldighetsperiode(
    @SerialName("startdato")
    @JsonProperty("startdato")
    val startdato: String? = null,

    @SerialName("sluttdato")
    @JsonProperty("sluttdato")
    val sluttdato: String? = null
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
 * Represents a property value in the egenskaper map.
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class EgenskapValue(
    @SerialName("type")
    @JsonProperty("type")
    val type: String? = null,

    @SerialName("verdi")
    @JsonProperty("verdi")
    val verdi: @Serializable(with = AnySerializer::class) Any? = null
)

/**
 * Represents the location/placement of a road object on the road network.
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class Stedfesting(
    @SerialName("type")
    @JsonProperty("type")
    val type: String? = null,

    @SerialName("linjer")
    @JsonProperty("linjer")
    val linjer: List<StedfestingLinje>? = null,

    @SerialName("geometries")
    @JsonProperty("geometries")
    val geometries: List<String>? = null
)

/**
 * Represents a line segment in stedfesting (StedfestingLinjer type).
 * The id field in the JSON is actually a veglenkesekvensId.
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class StedfestingLinje(
    @SerialName("id")
    @JsonProperty("id")
    val veglenkesekvensId: Long? = null,

    @SerialName("startposisjon")
    @JsonProperty("startposisjon")
    val startposisjon: Double? = null,

    @SerialName("sluttposisjon")
    @JsonProperty("sluttposisjon")
    val sluttposisjon: Double? = null,

    @SerialName("retning")
    @JsonProperty("retning")
    val retning: String? = null
)

/**
 * API response wrapper for paginated road object results.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VegobjekterResponse(
    @JsonProperty("vegobjekter")
    val vegobjekter: List<Vegobjekt> = emptyList(),

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
