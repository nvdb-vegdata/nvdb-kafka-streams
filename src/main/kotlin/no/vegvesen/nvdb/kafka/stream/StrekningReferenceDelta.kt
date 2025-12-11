package no.vegvesen.nvdb.kafka.stream

import kotlinx.serialization.Serializable

@Serializable
data class StrekningReferenceDelta(
    val fjernet: Boolean,
    val key: StrekningKey,
    val veglenkesekvensId: Long
)
