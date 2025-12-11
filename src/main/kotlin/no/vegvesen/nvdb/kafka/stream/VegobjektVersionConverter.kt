package no.vegvesen.nvdb.kafka.stream

import no.vegvesen.nvdb.api.uberiket.model.*
import no.vegvesen.nvdb.kafka.extensions.associate
import no.vegvesen.nvdb.kafka.model.Utstrekning
import no.vegvesen.nvdb.kafka.model.Vegobjekt

fun toDomain(versjon: VegobjektVersjon, vegobjektId: Long, typeId: Int): Vegobjekt = Vegobjekt(
    vegobjektId = vegobjektId,
    vegobjektType = typeId,
    egenskaper = versjon.egenskaper.associate { (key, value) ->
        key.toInt() to when (value) {
            is HeltallEgenskap -> value.verdi.toString()
            is TekstEgenskap -> value.verdi
            is EnumEgenskap -> value.verdi.toString()
            else -> error("unexpected egenskap type: ${value::class.simpleName}")
        }
    },
    stedfestinger = versjon.stedfesting?.let { stedfesting ->
        when (stedfesting) {
            is StedfestingLinjer -> stedfesting.linjer.map { linje ->
                Utstrekning(
                    veglenkesekvensId = linje.id,
                    startposisjon = linje.startposisjon,
                    sluttposisjon = linje.sluttposisjon
                )
            }
            else -> error("unexpected stedfesting type: ${stedfesting::class.simpleName}")
        }
    } ?: emptyList()
)
