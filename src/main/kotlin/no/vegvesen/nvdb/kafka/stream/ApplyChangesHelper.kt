package no.vegvesen.nvdb.kafka.stream

import no.vegvesen.nvdb.api.uberiket.model.StedfestingErstattet
import no.vegvesen.nvdb.api.uberiket.model.StedfestingFjernet
import no.vegvesen.nvdb.api.uberiket.model.VegobjektVersjon
import no.vegvesen.nvdb.api.uberiket.model.VegobjektVersjonEndret

fun applyChanges(original: VegobjektVersjon, changes: VegobjektVersjonEndret): VegobjektVersjon {
    val updatedEgenskaper = original.egenskaper + changes.egenskapEndringer

    val updatedGyldighetsperiode = changes.gyldighetsperiode ?: original.gyldighetsperiode

    val updatedStedfesting = when (val stedfestingChange = changes.stedfestingEndring) {
        is StedfestingErstattet -> stedfestingChange.stedfesting
        is StedfestingFjernet -> null
        null -> original.stedfesting
        else -> error("Unsupported stedfesting change type: ${stedfestingChange::class.simpleName}")
    }

    return VegobjektVersjon(
        versjonId = changes.versjonId,
        gyldighetsperiode = updatedGyldighetsperiode,
        egenskaper = updatedEgenskaper,
        barn = emptyMap(), // not used
        stedfesting = updatedStedfesting
    )
}
