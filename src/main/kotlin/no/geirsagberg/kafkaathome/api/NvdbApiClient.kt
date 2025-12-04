package no.geirsagberg.kafkaathome.api

import no.geirsagberg.kafkaathome.model.Vegobjekt
import no.geirsagberg.kafkaathome.model.VegobjekterResponse
import no.geirsagberg.kafkaathome.model.Veglenke
import no.geirsagberg.kafkaathome.model.Veglenkesekvens
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Flux

/**
 * Client for the NVDB Uberiket API.
 * Provides methods to fetch road network data including road link sequences
 * and road objects like speed limits.
 */
@Service
class NvdbApiClient(private val nvdbWebClient: WebClient) {

    private val logger = LoggerFactory.getLogger(NvdbApiClient::class.java)

    /**
     * Stream road link sequences (veglenkesekvenser) from the NVDB API.
     *
     * @param antall Maximum number of records to fetch per request
     * @return Flux of Veglenkesekvens objects
     */
    suspend fun streamVeglenkesekvenser(antall: Int = 1000): List<Veglenkesekvens> {
        logger.info("Fetching veglenkesekvenser from NVDB API")
        return nvdbWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("vegnett/veglenkesekvenser/stream")
                    .queryParam("antall", antall)
                    .build()
            }
            .retrieve()
            .awaitBody()
    }

    /**
     * Stream road objects (vegobjekter) of a specific type from the NVDB API.
     * Uses the NDJSON stream endpoint.
     *
     * @param typeId The type ID of the road object (e.g., 105 for speed limits)
     * @param antall Maximum number of records to fetch per request
     * @return Flux of Vegobjekt objects
     */
    fun streamVegobjekter(typeId: Int, antall: Int = 1000): Flux<Vegobjekt> {
        logger.info("Fetching vegobjekter of type {} from NVDB API", typeId)
        return nvdbWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("vegobjekter/$typeId/stream")
                    .queryParam("antall", antall)
                    .build()
            }
            .accept(org.springframework.http.MediaType.APPLICATION_NDJSON)
            .retrieve()
            .bodyToFlux(Vegobjekt::class.java)
    }

    /**
     * Fetch a single road object by its ID.
     *
     * @param typeId The type ID of the road object
     * @param objectId The ID of the specific road object
     * @return The Vegobjekt if found
     */
    suspend fun getVegobjekt(typeId: Int, objectId: Long): Vegobjekt {
        logger.debug("Fetching vegobjekt {} of type {}", objectId, typeId)
        return nvdbWebClient.get()
            .uri("vegobjekter/$typeId/$objectId")
            .retrieve()
            .awaitBody()
    }

    /**
     * Fetch veglenker by their IDs with pagination support.
     * Returns all veglenker across all pages for the given IDs.
     *
     * @param ider List of veglenkesekvens IDs
     * @param antall Records per page (default: 1000)
     * @return List of all Veglenke objects
     */
    suspend fun fetchVeglenkerByIds(ider: List<Long>, antall: Int = 1000): List<Veglenke> {
        if (ider.isEmpty()) return emptyList()

        logger.debug("Fetching veglenker for {} IDs", ider.size)
        val allVeglenker = mutableListOf<Veglenke>()
        var start: String? = null

        do {
            val currentStart = start
            val response: no.geirsagberg.kafkaathome.model.VeglenkesekvensResponse = nvdbWebClient.get()
                .uri { uriBuilder ->
                    val builder = uriBuilder.path("vegnett/veglenker/stream")
                    ider.forEach { id -> builder.queryParam("ider", id) }
                    builder.queryParam("antall", antall)
                    if (currentStart != null) {
                        builder.queryParam("start", currentStart)
                    }
                    builder.build()
                }
                .retrieve()
                .awaitBody()

            response.objekter.forEach { sekvens ->
                allVeglenker.addAll(sekvens.veglenker)
            }

            start = response.metadata?.neste?.start
        } while (start != null)

        logger.debug("Fetched {} veglenker total", allVeglenker.size)
        return allVeglenker
    }

    /**
     * Blocking version for use in Kafka Streams topology.
     * Leverages virtual threads for efficient blocking.
     */
    fun fetchVeglenkerByIdsBlocking(ider: List<Long>, antall: Int = 1000): List<Veglenke> {
        return kotlinx.coroutines.runBlocking {
            fetchVeglenkerByIds(ider, antall)
        }
    }

    companion object {
        // Common road object type IDs from NVDB
        const val TYPE_FARTSGRENSE = 105 // Speed limits
        const val TYPE_VEGBREDDE = 583 // Road width
        const val TYPE_VEGREFERANSE = 532 // Road reference
        const val TYPE_KJØREFELT = 616 // Driving lanes
        const val TYPE_FUNKSJONSKLASSE = 821 // Functional road class
    }
}
