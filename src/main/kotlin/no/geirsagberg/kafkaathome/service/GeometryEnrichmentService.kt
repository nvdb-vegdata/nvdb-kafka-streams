package no.geirsagberg.kafkaathome.service

import no.geirsagberg.kafkaathome.api.NvdbApiClient
import no.geirsagberg.kafkaathome.model.Vegobjekt
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GeometryEnrichmentService(
    private val nvdbApiClient: NvdbApiClient
) {
    private val logger = LoggerFactory.getLogger(GeometryEnrichmentService::class.java)

    /**
     * Enrich a vegobjekt with geometry data from veglenker.
     * Returns a copy of the vegobjekt with enriched stedfesting.
     */
    fun enrichWithGeometry(vegobjekt: Vegobjekt): Vegobjekt {
        val stedfesting = vegobjekt.stedfesting ?: return vegobjekt
        val veglenkesekvensider = stedfesting.veglenkesekvensider?.mapNotNull { it.veglenkesekvensId }
            ?: return vegobjekt

        if (veglenkesekvensider.isEmpty()) return vegobjekt

        return try {
            val veglenker = nvdbApiClient.fetchVeglenkerByIdsBlocking(veglenkesekvensider)

            val geometries = veglenker.mapNotNull { veglenke -> veglenke.geometri?.wkt }

            if (geometries.isEmpty()) {
                logger.debug("No geometries found for vegobjekt {}", vegobjekt.id)
                return vegobjekt
            }

            logger.debug("Enriched vegobjekt {} with {} geometries", vegobjekt.id, geometries.size)

            vegobjekt.copy(
                stedfesting = stedfesting.copy(geometries = geometries)
            )
        } catch (e: Exception) {
            logger.warn("Failed to enrich vegobjekt {} with geometry: {}", vegobjekt.id, e.message)
            vegobjekt
        }
    }
}
