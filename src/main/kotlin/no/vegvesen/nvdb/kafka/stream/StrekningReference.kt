package no.vegvesen.nvdb.kafka.stream

import kotlinx.serialization.Serializable

@Serializable
data class StrekningReference(
    val key: StrekningKey,
    val veglenkesekvensId: Long
)
