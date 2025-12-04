package no.geirsagberg.kafkaathome.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import no.geirsagberg.kafkaathome.model.Vegobjekt
import no.geirsagberg.kafkaathome.model.Veglenke
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToFlow

/**
 * Client for the NVDB Uberiket API.
 * Provides methods to fetch road network data including road link sequences
 * and road objects like speed limits.
 */
@Service
class NvdbApiClient(private val nvdbWebClient: WebClient) {

    private val logger = LoggerFactory.getLogger(NvdbApiClient::class.java)

    /**
     * Stream road objects (vegobjekter) of a specific type from the NVDB API.
     * Uses the NDJSON stream endpoint.
     *
     * @param typeId The type ID of the road object (e.g., 105 for speed limits)
     * @param antall Maximum number of records to fetch per request
     * @param start Optional starting object ID for pagination (fetch objects after this ID)
     * @return Flow of Vegobjekt objects
     */
    fun streamVegobjekter(typeId: Int, antall: Int = 1000, start: Long? = null): Flow<Vegobjekt> {
        logger.info("Fetching vegobjekter of type {} from NVDB API (start: {})", typeId, start)
        return nvdbWebClient.get()
            .uri { uriBuilder ->
                val builder = uriBuilder
                    .path("vegobjekter/$typeId/stream")
                    .queryParam("antall", antall)
                if (start != null) {
                    builder.queryParam("start", start)
                }
                builder.build()
            }
            .accept(org.springframework.http.MediaType.APPLICATION_NDJSON)
            .retrieve()
            .bodyToFlow<Vegobjekt>()
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
     * Fetch veglenker by veglenkesekvens IDs using the streaming API.
     * The stream endpoint returns flattened Veglenke objects directly.
     * One veglenkesekvens contains many veglenker (identified by veglenkenummer).
     *
     * Pagination: The start parameter should be "[veglenkesekvensId]-[veglenkenummer]"
     * from the last veglenke received.
     *
     * @param veglenkesekvensIds List of veglenkesekvens IDs
     * @param antall Records per page (default: 1000)
     * @return List of all Veglenke objects from the given veglenkesekvenser
     */
    suspend fun fetchVeglenkerByVeglenkesekvensIds(veglenkesekvensIds: List<Long>, antall: Int = 1000): List<Veglenke> {
        if (veglenkesekvensIds.isEmpty()) return emptyList()

        logger.debug("Fetching veglenker for {} veglenkesekvens IDs", veglenkesekvensIds.size)
        val allVeglenker = mutableListOf<Veglenke>()
        var start: String? = null

        do {
            val currentStart = start
            val batch = nvdbWebClient.get()
                .uri { uriBuilder ->
                    val builder = uriBuilder.path("vegnett/veglenker/stream")
                    veglenkesekvensIds.forEach { id -> builder.queryParam("ider", id) }
                    builder.queryParam("antall", antall)
                    if (currentStart != null) {
                        builder.queryParam("start", currentStart)
                    }
                    builder.build()
                }
                .retrieve()
                .bodyToFlow<Veglenke>()
                .toList()

            allVeglenker.addAll(batch)

            // Set start parameter as "[veglenkesekvensId]-[veglenkenummer]" from last veglenke
            start = if (batch.size == antall) {
                val lastVeglenke = batch.last()
                val sekvensId = lastVeglenke.veglenkesekvensId
                val veglenkenr = lastVeglenke.veglenkenummer
                if (sekvensId != null && veglenkenr != null) {
                    "$sekvensId-$veglenkenr"
                } else {
                    null
                }
            } else {
                null
            }
        } while (start != null)

        logger.debug("Fetched {} veglenker total", allVeglenker.size)
        return allVeglenker
    }

    /**
     * Blocking version for use in Kafka Streams topology.
     * Leverages virtual threads for efficient blocking.
     */
    fun fetchVeglenkerByVeglenkesekvensIdsBlocking(veglenkesekvensIds: List<Long>, antall: Int = 1000): List<Veglenke> {
        return kotlinx.coroutines.runBlocking {
            fetchVeglenkerByVeglenkesekvensIds(veglenkesekvensIds, antall)
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
