package no.vegvesen.nvdb.kafka.stream

import no.vegvesen.nvdb.kafka.model.VegfaseEgenskap
import no.vegvesen.nvdb.kafka.model.VegkategoriEgenskap

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

        // Strict left-to-right hierarchy patterns (vegkategori + fase + vegnummer required)
        private val fullPatternRegex = Regex("""([A-Z])([A-Z])(\d+)\s*S(\d+)D(\d+)""")       // EV6 S3D1
        private val sectionPatternRegex = Regex("""([A-Z])([A-Z])(\d+)\s*S(\d+)""")          // EV6 S3
        private val phasePatternRegex = Regex("""([A-Z])([A-Z])(\d+)""")                      // EV6

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
         * Strict left-to-right hierarchy: vegkategori, fase, and vegnummer are always required.
         *
         * Supported formats:
         * - "EV6" → category + fase + number (minimum required)
         * - "EV6 S3" → + section
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

            // Try phase pattern: "EV6" (minimum required)
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

            throw IllegalArgumentException(
                "Invalid key format: '$input'. " +
                        "Supported formats: 'EV6', 'EV6 S3', 'EV6 S3D1', 'E V 6 3 1'"
            )
        }

        /**
         * Create a range query for all keys matching the given criteria.
         * Strict left-to-right hierarchy: vegkategori, fase, and vegnummer are required.
         *
         * Examples:
         * - rangeFor(E, V, 6) → All EV6 roads (all sections/subsections)
         * - rangeFor(E, V, 6, 3) → All EV6 S3 (all subsections)
         * - rangeFor(E, V, 6, 3, 1) → Exact EV6 S3D1
         */
        fun rangeFor(
            vegkategori: VegkategoriEgenskap,
            fase: VegfaseEgenskap,
            vegnummer: Int,
            strekning: Int? = null,
            delstrekning: Int? = null
        ): Pair<StrekningKey, StrekningKey> {
            // Build "from" key with minimum values for unspecified fields
            val from = StrekningKey(
                vegkategori = vegkategori,
                fase = fase,
                vegnummer = vegnummer,
                strekning = strekning ?: 0,
                delstrekning = delstrekning ?: 0
            )

            // Build "to" key by incrementing the rightmost specified field
            // Sort order: vegkategori → fase → vegnummer → strekning → delstrekning
            val to = when {
                delstrekning != null -> from.copy(delstrekning = delstrekning + 1)
                strekning != null -> from.copy(strekning = strekning + 1, delstrekning = 0)
                else -> {
                    // No strekning/delstrekning specified, increment vegnummer
                    from.copy(vegnummer = vegnummer + 1, strekning = 0, delstrekning = 0)
                }
            }

            return from to to
        }
    }
}

/**
 * Partial strekning key for flexible queries.
 * Strict left-to-right hierarchy: fields match the sort order.
 * Sort order: vegkategori → fase → vegnummer → strekning → delstrekning
 *
 * Minimum requirement: vegkategori, fase, and vegnummer must all be specified.
 */
data class PartialStrekningKey(
    val vegkategori: VegkategoriEgenskap,
    val fase: VegfaseEgenskap,
    val vegnummer: Int,
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
