package no.vegvesen.nvdb.kafka.stream

data class StrekningReferenceDelta(
    val fjernet: Boolean,
    val key: StrekningKey,
    val veglenkesekvensId: Long
)
