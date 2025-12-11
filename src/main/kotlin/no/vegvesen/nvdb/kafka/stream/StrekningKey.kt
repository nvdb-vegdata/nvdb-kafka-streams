package no.vegvesen.nvdb.kafka.stream

import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.kafka.model.VegfaseEgenskap
import no.vegvesen.nvdb.kafka.model.VegkategoriEgenskap

@Serializable
data class StrekningKey(
    val vegkategori: VegkategoriEgenskap,
    val fase: VegfaseEgenskap,
    val vegnummer: Int,
    val strekning: Int,
    val delstrekning: Int
) : Comparable<StrekningKey> {
    override fun compareTo(other: StrekningKey): Int {
        vegkategori.compareTo(other.vegkategori).takeIf { it != 0 }?.let { return it }
        fase.compareTo(other.fase).takeIf { it != 0 }?.let { return it }
        vegnummer.compareTo(other.vegnummer).takeIf { it != 0 }?.let { return it }
        strekning.compareTo(other.strekning).takeIf { it != 0 }?.let { return it }
        return delstrekning.compareTo(other.delstrekning)
    }

    override fun toString(): String = buildString {
        append(vegkategori)
        append(fase)
        append(vegnummer)
        append(" S")
        append(strekning)
        append("D")
        append(delstrekning)
    }

    companion object {

        val regex = Regex("")

        fun parse(key: String): StrekningKey {
            val parts = key.split(" ")
            return StrekningKey(
                vegkategori = VegkategoriEgenskap.valueOf(parts[0]),
                fase = VegfaseEgenskap.valueOf(parts[1]),
                vegnummer = parts[2].toInt(),
                strekning = parts[3].toInt(),
                delstrekning = parts[4].toInt()
            )
        }


        /**
         * Create a range query for all keys matching the given criteria.
         * Pass null for fields that should match any value.
         *
         * Examples:
         * - rangeFor(EV, V, 6, null, null) → All of EV6
         * - rangeFor(EV, V, 6, 3, null) → All of EV6 S3 (all delstrekninger)
         * - rangeFor(EV, V, null, null, null) → All EV roads
         */
        fun rangeFor(
            vegkategori: VegkategoriEgenskap? = null,
            fase: VegfaseEgenskap? = null,
            vegnummer: Int? = null,
            strekning: Int? = null,
            delstrekning: Int? = null
        ): Pair<StrekningKey, StrekningKey> {
            // Build the "from" key with minimum values for unspecified fields
            val from = StrekningKey(
                vegkategori = vegkategori ?: VegkategoriEgenskap.entries.first(),
                fase = fase ?: VegfaseEgenskap.entries.first(),
                vegnummer = vegnummer ?: 0,
                strekning = strekning ?: 0,
                delstrekning = delstrekning ?: 0
            )

            // Build the "to" key by incrementing the last specified field
            val to = when {
                delstrekning != null -> from.copy(delstrekning = delstrekning + 1)
                strekning != null -> from.copy(strekning = strekning + 1, delstrekning = 0)
                vegnummer != null -> from.copy(vegnummer = vegnummer + 1, strekning = 0, delstrekning = 0)
                fase != null -> {
                    val nextFase = VegfaseEgenskap.entries.getOrNull(fase.ordinal + 1)
                    if (nextFase != null) {
                        from.copy(fase = nextFase, vegnummer = 0, strekning = 0, delstrekning = 0)
                    } else {
                        // Move to next vegkategori
                        val nextKat = VegkategoriEgenskap.entries.getOrNull(vegkategori!!.ordinal + 1)
                        from.copy(
                            vegkategori = nextKat ?: vegkategori,
                            fase = VegfaseEgenskap.entries.first(),
                            vegnummer = 0,
                            strekning = 0,
                            delstrekning = 0
                        )
                    }
                }

                vegkategori != null -> {
                    val nextKat = VegkategoriEgenskap.entries.getOrNull(vegkategori.ordinal + 1)
                    from.copy(
                        vegkategori = nextKat ?: vegkategori,
                        fase = VegfaseEgenskap.entries.first(),
                        vegnummer = 0,
                        strekning = 0,
                        delstrekning = 0
                    )
                }

                else -> from.copy(
                    vegkategori = VegkategoriEgenskap.entries.last(),
                    fase = VegfaseEgenskap.entries.last(),
                    vegnummer = Int.MAX_VALUE,
                    strekning = Int.MAX_VALUE,
                    delstrekning = Int.MAX_VALUE
                )
            }

            return from to to
        }
    }
}
