package no.vegvesen.nvdb.kafka.model

import kotlinx.serialization.Serializable

@Serializable
data class Vegobjekt(
    val vegobjektId: Long,
    val vegobjektType: Int,
    val egenskaper: Map<Int, String>,
    val stedfestinger: List<Utstrekning>,
)

@Serializable
data class Utstrekning(
    val veglenkesekvensId: Long,
    val startposisjon: Double,
    val sluttposisjon: Double,
)

@Serializable
data class VegobjektDelta(
    val before: Vegobjekt?,
    val after: Vegobjekt?,
)

@Serializable
data class VegobjektUtstrekning(
    val veglenkesekvensId: Long,
    val startposisjon: Double,
    val sluttposisjon: Double,
    val stedfestingIndex: Int,
    val vegobjektId: Long,
    val vegobjektType: Int,
    val egenskaper: Map<Int, String>,
)

@Serializable
data class VegobjektUtstrekningDelta(
    val fjernet: Boolean,                // false = lagt til, true = fjernet
    val utstrekning: VegobjektUtstrekning,
)

fun Vegobjekt?.toUtstrekninger(): Set<VegobjektUtstrekning> {
    if (this == null) return emptySet()
    return stedfestinger.mapIndexed { index, utstrekning ->
        VegobjektUtstrekning(
            veglenkesekvensId = utstrekning.veglenkesekvensId,
            startposisjon = utstrekning.startposisjon,
            sluttposisjon = utstrekning.sluttposisjon,
            stedfestingIndex = index,
            vegobjektId = vegobjektId,
            vegobjektType = vegobjektType,
            egenskaper = egenskaper
        )
    }.toSet()
}
