package no.vegvesen.nvdb.kafka.stream

import no.vegvesen.nvdb.kafka.model.*

fun vegobjekt(
    id: Long,
    typeId: Int,
    egenskaper: Map<Int, String> = emptyMap(),
    stedfestinger: List<Utstrekning> = emptyList()
): Vegobjekt = Vegobjekt(
    vegobjektId = id,
    vegobjektType = typeId,
    egenskaper = egenskaper,
    stedfestinger = stedfestinger
)

fun utstrekning(
    veglenkesekvensId: Long,
    start: Double = 0.0,
    slutt: Double = 1.0
): Utstrekning = Utstrekning(
    veglenkesekvensId = veglenkesekvensId,
    startposisjon = start,
    sluttposisjon = slutt
)

fun vegobjektDelta(
    before: Vegobjekt? = null,
    after: Vegobjekt? = null
): VegobjektDelta = VegobjektDelta(
    before = before,
    after = after
)

fun strekningKey(
    vegkategori: VegkategoriEgenskap = VegkategoriEgenskap.E,
    fase: VegfaseEgenskap = VegfaseEgenskap.V,
    vegnummer: Int = 6,
    strekning: Int = 1,
    delstrekning: Int = 1
): StrekningKey = StrekningKey(
    vegkategori = vegkategori,
    fase = fase,
    vegnummer = vegnummer,
    strekning = strekning,
    delstrekning = delstrekning
)

fun vegsystemEgenskaper(
    vegkategori: VegkategoriEgenskap = VegkategoriEgenskap.E,
    fase: VegfaseEgenskap = VegfaseEgenskap.V,
    vegnummer: Int = 6
): Map<Int, String> = mapOf(
    11276 to vegkategori.nvdbValue.toString(),  // Vegkategori
    11278 to fase.nvdbValue.toString(),         // Fase
    11277 to vegnummer.toString()               // Vegnummer
)

fun strekningEgenskaper(
    strekning: Int = 1,
    delstrekning: Int = 1
): Map<Int, String> = mapOf(
    11281 to strekning.toString(),     // Strekning
    11284 to delstrekning.toString()   // Delstrekning
)
