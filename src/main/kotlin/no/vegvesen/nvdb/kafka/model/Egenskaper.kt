package no.vegvesen.nvdb.kafka.model


enum class VegkategoriEgenskap(val nvdbValue: Int) {
    E(19024), // Europaveg
    R(19025), // Riksveg
    F(19026), // Fylkseveg
    K(19027), // Kommuneveg
    P(19028), // Privat veg
    S(19029), // Skogsveg
    ;

    companion object {
        fun fromNvdbValue(nvdbValue: Int): VegkategoriEgenskap? = entries.find { it.nvdbValue == nvdbValue }
    }
}

fun VegkategoriEgenskap.needsKommune() =
    this in setOf(VegkategoriEgenskap.K, VegkategoriEgenskap.P, VegkategoriEgenskap.S)

enum class VegfaseEgenskap(val nvdbValue: Int) {
    P(19030), // Planned
    A(19031), // Under construction
    V(19032), // In use
    F(19090), // Fictional
    ;

    companion object {
        fun fromNvdbValue(nvdbValue: Int): VegfaseEgenskap? = entries.find { it.nvdbValue == nvdbValue }
    }
}
