package no.vegvesen.nvdb.kafka.stream

data class StrekningReference(
    val key: StrekningKey,
    val veglenkesekvensId: Long
)
