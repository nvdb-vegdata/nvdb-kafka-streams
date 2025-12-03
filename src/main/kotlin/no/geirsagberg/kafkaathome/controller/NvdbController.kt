package no.geirsagberg.kafkaathome.controller

import no.geirsagberg.kafkaathome.api.NvdbApiClient
import no.geirsagberg.kafkaathome.stream.NvdbDataProducer
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

/**
 * REST controller for managing NVDB data ingestion and Kafka operations.
 */
@RestController
@RequestMapping("/api/nvdb")
class NvdbController(
    private val nvdbDataProducer: NvdbDataProducer,
    private val nvdbApiClient: NvdbApiClient
) {

    /**
     * Trigger fetching of speed limits from NVDB and produce to Kafka.
     *
     * @param count Number of records to fetch (default: 100)
     */
    @PostMapping("/fetch/speedlimits")
    fun fetchSpeedLimits(
        @RequestParam(defaultValue = "100") count: Int
    ): Mono<ResponseEntity<FetchResponse>> {
        return nvdbDataProducer.fetchAndProduceVegobjekter(
            NvdbApiClient.TYPE_FARTSGRENSE, 
            count
        ).map { total ->
            ResponseEntity.ok(FetchResponse(
                typeId = NvdbApiClient.TYPE_FARTSGRENSE,
                typeName = "Fartsgrense (Speed Limits)",
                count = total.toInt(),
                status = "success"
            ))
        }
    }

    /**
     * Trigger fetching of road objects of a specific type from NVDB.
     *
     * @param typeId NVDB type ID of the road objects
     * @param count Number of records to fetch (default: 100)
     */
    @PostMapping("/fetch/vegobjekter/{typeId}")
    fun fetchVegobjekter(
        @PathVariable typeId: Int,
        @RequestParam(defaultValue = "100") count: Int
    ): Mono<ResponseEntity<FetchResponse>> {
        return nvdbDataProducer.fetchAndProduceVegobjekter(typeId, count)
            .map { total ->
                ResponseEntity.ok(FetchResponse(
                    typeId = typeId,
                    typeName = getTypeName(typeId),
                    count = total.toInt(),
                    status = "success"
                ))
            }
    }

    /**
     * Get health/status information about the NVDB integration.
     */
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<StatusResponse> {
        return ResponseEntity.ok(StatusResponse(
            status = "running",
            availableTypes = listOf(
                TypeInfo(NvdbApiClient.TYPE_FARTSGRENSE, "Fartsgrense (Speed Limits)"),
                TypeInfo(NvdbApiClient.TYPE_VEGBREDDE, "Vegbredde (Road Width)"),
                TypeInfo(NvdbApiClient.TYPE_FUNKSJONSKLASSE, "Funksjonsklasse (Functional Road Class)"),
                TypeInfo(NvdbApiClient.TYPE_KJØREFELT, "Kjørefelt (Driving Lanes)")
            )
        ))
    }

    private fun getTypeName(typeId: Int): String = when (typeId) {
        NvdbApiClient.TYPE_FARTSGRENSE -> "Fartsgrense (Speed Limits)"
        NvdbApiClient.TYPE_VEGBREDDE -> "Vegbredde (Road Width)"
        NvdbApiClient.TYPE_VEGREFERANSE -> "Vegreferanse (Road Reference)"
        NvdbApiClient.TYPE_KJØREFELT -> "Kjørefelt (Driving Lanes)"
        NvdbApiClient.TYPE_FUNKSJONSKLASSE -> "Funksjonsklasse (Functional Road Class)"
        else -> "Type $typeId"
    }
}

data class FetchResponse(
    val typeId: Int,
    val typeName: String,
    val count: Int,
    val status: String
)

data class StatusResponse(
    val status: String,
    val availableTypes: List<TypeInfo>
)

data class TypeInfo(
    val typeId: Int,
    val name: String
)
