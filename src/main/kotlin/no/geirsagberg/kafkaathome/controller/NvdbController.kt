package no.geirsagberg.kafkaathome.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import no.geirsagberg.kafkaathome.api.NvdbApiClient
import no.geirsagberg.kafkaathome.stream.NvdbDataProducer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * REST controller for managing NVDB backfill and monitoring.
 */
@RestController
@RequestMapping("/api/nvdb")
@Tag(name = "NVDB Backfill Control", description = "Endpoints for controlling and monitoring NVDB data backfill")
class NvdbController(
    @Autowired(required = false) private val nvdbDataProducer: NvdbDataProducer?
) {

    /**
     * Start backfill for a specific type (915 or 916).
     */
    @Operation(
        summary = "Start backfill",
        description = "Initiates backfill process for a specific vegobjekttype. Queries the latest hendelse ID before starting to ensure no events are missed."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Backfill started successfully"),
            ApiResponse(responseCode = "400", description = "Failed to start backfill"),
            ApiResponse(responseCode = "503", description = "Producer is disabled")
        ]
    )
    @PostMapping("/backfill/{typeId}/start")
    fun startBackfill(
        @Parameter(description = "NVDB vegobjekttype ID (915 for Vegsystem, 916 for Strekning)", example = "915")
        @PathVariable typeId: Int
    ): Mono<ResponseEntity<BackfillControlResponse>> {
        return if (nvdbDataProducer != null) {
            Mono.fromCallable {
                runBlocking { nvdbDataProducer.startBackfill(typeId) }
                ResponseEntity.ok(BackfillControlResponse(
                    typeId = typeId,
                    action = "started",
                    message = "Backfill started for type $typeId"
                ))
            }.onErrorResume { error ->
                Mono.just(ResponseEntity.badRequest().body(BackfillControlResponse(
                    typeId = typeId,
                    action = "start_failed",
                    message = "Failed to start backfill: ${error.message}"
                )))
            }
        } else {
            Mono.just(ResponseEntity.status(503).body(BackfillControlResponse(
                typeId = typeId,
                action = "unavailable",
                message = "Producer is disabled"
            )))
        }
    }

    /**
     * Stop/pause backfill for a specific type.
     */
    @Operation(
        summary = "Stop backfill",
        description = "Stops the backfill process by deleting the progress record. Processing will stop on the next scheduled run."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Backfill stopped successfully"),
            ApiResponse(responseCode = "503", description = "Producer is disabled")
        ]
    )
    @PostMapping("/backfill/{typeId}/stop")
    fun stopBackfill(
        @Parameter(description = "NVDB vegobjekttype ID", example = "915")
        @PathVariable typeId: Int
    ): Mono<ResponseEntity<BackfillControlResponse>> {
        return if (nvdbDataProducer != null) {
            Mono.fromCallable {
                nvdbDataProducer.stopBackfill(typeId)
                ResponseEntity.ok(BackfillControlResponse(
                    typeId = typeId,
                    action = "stopped",
                    message = "Backfill stopped for type $typeId"
                ))
            }
        } else {
            Mono.just(ResponseEntity.status(503).body(BackfillControlResponse(
                typeId = typeId,
                action = "unavailable",
                message = "Producer is disabled"
            )))
        }
    }

    /**
     * Reset backfill: delete progress and restart from beginning.
     */
    @Operation(
        summary = "Reset backfill",
        description = "Deletes existing progress and restarts backfill from the beginning. Queries a new hendelse ID before starting."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Backfill reset successfully"),
            ApiResponse(responseCode = "400", description = "Failed to reset backfill"),
            ApiResponse(responseCode = "503", description = "Producer is disabled")
        ]
    )
    @PostMapping("/backfill/{typeId}/reset")
    fun resetBackfill(
        @Parameter(description = "NVDB vegobjekttype ID", example = "915")
        @PathVariable typeId: Int
    ): Mono<ResponseEntity<BackfillControlResponse>> {
        return if (nvdbDataProducer != null) {
            Mono.fromCallable {
                runBlocking { nvdbDataProducer.resetBackfill(typeId) }
                ResponseEntity.ok(BackfillControlResponse(
                    typeId = typeId,
                    action = "reset",
                    message = "Backfill reset and restarted for type $typeId"
                ))
            }.onErrorResume { error ->
                Mono.just(ResponseEntity.badRequest().body(BackfillControlResponse(
                    typeId = typeId,
                    action = "reset_failed",
                    message = "Failed to reset backfill: ${error.message}"
                )))
            }
        } else {
            Mono.just(ResponseEntity.status(503).body(BackfillControlResponse(
                typeId = typeId,
                action = "unavailable",
                message = "Producer is disabled"
            )))
        }
    }

    /**
     * Get status for a specific type.
     */
    @Operation(
        summary = "Get type status",
        description = "Retrieves detailed status information for a specific vegobjekttype including mode, progress, and error information."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
            ApiResponse(responseCode = "503", description = "Producer is disabled")
        ]
    )
    @GetMapping("/status/{typeId}")
    fun getTypeStatus(
        @Parameter(description = "NVDB vegobjekttype ID", example = "915")
        @PathVariable typeId: Int
    ): Mono<ResponseEntity<TypeStatusResponse>> {
        return if (nvdbDataProducer != null) {
            Mono.fromCallable {
                val progress = nvdbDataProducer.getStatus(typeId)
                if (progress != null) {
                    ResponseEntity.ok(TypeStatusResponse(
                        typeId = typeId,
                        mode = progress.mode.name,
                        lastProcessedId = progress.lastProcessedId,
                        hendelseId = progress.hendelseId,
                        backfillStartTime = progress.backfillStartTime,
                        backfillCompletionTime = progress.backfillCompletionTime,
                        lastError = progress.lastError,
                        updatedAt = progress.updatedAt
                    ))
                } else {
                    ResponseEntity.ok(TypeStatusResponse(
                        typeId = typeId,
                        mode = "NOT_INITIALIZED",
                        lastProcessedId = null,
                        hendelseId = null,
                        backfillStartTime = null,
                        backfillCompletionTime = null,
                        lastError = null,
                        updatedAt = null
                    ))
                }
            }
        } else {
            Mono.just(ResponseEntity.status(503).body(TypeStatusResponse(
                typeId = typeId,
                mode = "PRODUCER_DISABLED",
                lastProcessedId = null,
                hendelseId = null,
                backfillStartTime = null,
                backfillCompletionTime = null,
                lastError = null,
                updatedAt = null
            )))
        }
    }

    /**
     * Get overall status for all configured types.
     */
    @Operation(
        summary = "Get overall status",
        description = "Retrieves status summary for all configured vegobjekttyper (915 and 916)."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Status retrieved successfully")
        ]
    )
    @GetMapping("/status")
    fun getAllStatus(): ResponseEntity<OverallStatusResponse> {
        return if (nvdbDataProducer != null) {
            val type915 = nvdbDataProducer.getStatus(NvdbApiClient.TYPE_VEGSYSTEM)
            val type916 = nvdbDataProducer.getStatus(NvdbApiClient.TYPE_STREKNING)

            ResponseEntity.ok(OverallStatusResponse(
                status = "running",
                types = listOf(
                    TypeSummary(
                        typeId = NvdbApiClient.TYPE_VEGSYSTEM,
                        name = "Vegsystem",
                        mode = type915?.mode?.name ?: "NOT_INITIALIZED",
                        topic = "nvdb-vegobjekter-915"
                    ),
                    TypeSummary(
                        typeId = NvdbApiClient.TYPE_STREKNING,
                        name = "Strekning",
                        mode = type916?.mode?.name ?: "NOT_INITIALIZED",
                        topic = "nvdb-vegobjekter-916"
                    )
                )
            ))
        } else {
            ResponseEntity.ok(OverallStatusResponse(
                status = "disabled",
                types = emptyList()
            ))
        }
    }
}

data class BackfillControlResponse(
    val typeId: Int,
    val action: String,
    val message: String
)

data class TypeStatusResponse(
    val typeId: Int,
    val mode: String,
    val lastProcessedId: Long?,
    val hendelseId: Long?,
    val backfillStartTime: Instant?,
    val backfillCompletionTime: Instant?,
    val lastError: String?,
    val updatedAt: Instant?
)

data class OverallStatusResponse(
    val status: String,
    val types: List<TypeSummary>
)

data class TypeSummary(
    val typeId: Int,
    val name: String,
    val mode: String,
    val topic: String
)
