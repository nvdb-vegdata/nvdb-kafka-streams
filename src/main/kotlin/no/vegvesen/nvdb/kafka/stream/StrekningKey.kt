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

        private val fullPatternRegex = Regex("""([A-Z])([A-Z])(\d+)\s+S(\d+)D(\d+)""")
        private val sectionPatternRegex = Regex("""([A-Z])([A-Z])(\d+)\s+S(\d+)""")
        private val phasePatternRegex = Regex("""([A-Z])([A-Z])(\d+)""")
        private val simplePatternRegex = Regex("""([A-Z])(\d+)""")

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
         * Parse a flexible string format into a PartialStrekningKey.
         *
         * Supported formats:
         * - "E6" → category + number (all phases/sections/subsections)
         * - "EV6" → category + phase + number (all sections/subsections)
         * - "EV6 S3" → category + phase + number + section (all subsections)
         * - "EV6 S3D1" → full key (exact match)
         * - "E V 6 3 1" → space-separated (backward compatibility)
         *
         * @throws IllegalArgumentException if the format is invalid
         */
        fun parseFlexible(input: String): PartialStrekningKey {
            val normalized = input.trim().uppercase()

            // Try space-separated format first (5 parts)
            val spaceParts = normalized.split(Regex("\\s+"))
            if (spaceParts.size == 5) {
                return PartialStrekningKey(
                    vegkategori = VegkategoriEgenskap.fromNvdbLetter(spaceParts[0])
                        ?: throw IllegalArgumentException("Invalid vegkategori: ${spaceParts[0]}. Valid values: E, R, F, K, P, S"),
                    fase = VegfaseEgenskap.fromNvdbLetter(spaceParts[1])
                        ?: throw IllegalArgumentException("Invalid fase: ${spaceParts[1]}. Valid values: V, P, A, F"),
                    vegnummer = spaceParts[2].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid vegnummer: ${spaceParts[2]}"),
                    strekning = spaceParts[3].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid strekning: ${spaceParts[3]}"),
                    delstrekning = spaceParts[4].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid delstrekning: ${spaceParts[4]}")
                )
            }

            // Try full pattern: "EV6 S3D1"
            fullPatternRegex.matchEntire(normalized)?.let { match ->
                val (kat, phase, num, section, subsection) = match.destructured
                return PartialStrekningKey(
                    vegkategori = VegkategoriEgenskap.fromNvdbLetter(kat)
                        ?: throw IllegalArgumentException("Invalid vegkategori: $kat"),
                    fase = VegfaseEgenskap.fromNvdbLetter(phase)
                        ?: throw IllegalArgumentException("Invalid fase: $phase"),
                    vegnummer = num.toInt(),
                    strekning = section.toInt(),
                    delstrekning = subsection.toInt()
                )
            }

            // Try section pattern: "EV6 S3"
            sectionPatternRegex.matchEntire(normalized)?.let { match ->
                val (kat, phase, num, section) = match.destructured
                return PartialStrekningKey(
                    vegkategori = VegkategoriEgenskap.fromNvdbLetter(kat)
                        ?: throw IllegalArgumentException("Invalid vegkategori: $kat"),
                    fase = VegfaseEgenskap.fromNvdbLetter(phase)
                        ?: throw IllegalArgumentException("Invalid fase: $phase"),
                    vegnummer = num.toInt(),
                    strekning = section.toInt(),
                    delstrekning = null
                )
            }

            // Try phase pattern: "EV6"
            phasePatternRegex.matchEntire(normalized)?.let { match ->
                val (kat, phase, num) = match.destructured
                return PartialStrekningKey(
                    vegkategori = VegkategoriEgenskap.fromNvdbLetter(kat)
                        ?: throw IllegalArgumentException("Invalid vegkategori: $kat"),
                    fase = VegfaseEgenskap.fromNvdbLetter(phase)
                        ?: throw IllegalArgumentException("Invalid fase: $phase"),
                    vegnummer = num.toInt(),
                    strekning = null,
                    delstrekning = null
                )
            }

            // Try simple pattern: "E6"
            simplePatternRegex.matchEntire(normalized)?.let { match ->
                val (kat, num) = match.destructured
                return PartialStrekningKey(
                    vegkategori = VegkategoriEgenskap.fromNvdbLetter(kat)
                        ?: throw IllegalArgumentException("Invalid vegkategori: $kat"),
                    fase = null,
                    vegnummer = num.toInt(),
                    strekning = null,
                    delstrekning = null
                )
            }

            throw IllegalArgumentException(
                "Invalid key format: '$input'. " +
                        "Supported formats: 'E6', 'EV6', 'EV6 S3', 'EV6 S3D1', 'E V 6 3 1'"
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

/**
 * Partial strekning key for flexible queries.
 * Null fields will match any value in that position.
 */
data class PartialStrekningKey(
    val vegkategori: VegkategoriEgenskap?,
    val fase: VegfaseEgenskap?,
    val vegnummer: Int?,
    val strekning: Int?,
    val delstrekning: Int?
) {
    /**
     * Convert this partial key to a range query (from, to) bounds.
     */
    fun toRange(): Pair<StrekningKey, StrekningKey> {
        return StrekningKey.rangeFor(vegkategori, fase, vegnummer, strekning, delstrekning)
    }
}
