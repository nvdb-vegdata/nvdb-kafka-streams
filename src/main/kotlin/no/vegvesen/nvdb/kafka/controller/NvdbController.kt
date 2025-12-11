package no.vegvesen.nvdb.kafka.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.vegvesen.nvdb.kafka.api.NvdbApiClient
import no.vegvesen.nvdb.kafka.stream.NvdbDataProducer
import no.vegvesen.nvdb.kafka.stream.PartialStrekningKey
import no.vegvesen.nvdb.kafka.stream.StrekningKey
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*

/**
 * REST controller for managing NVDB backfill and monitoring.
 */
@RestController
@RequestMapping("/api/nvdb")
@Tag(name = "NVDB Backfill Control", description = "Endpoints for controlling and monitoring NVDB data backfill")
class NvdbController(
    private val nvdbDataProducer: NvdbDataProducer,
    @org.springframework.context.annotation.Lazy private val kafkaStreams: KafkaStreams,
    @Value("\${spring.kafka.streams.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${spring.kafka.streams.application-id}") private val applicationId: String
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
    suspend fun startBackfill(
        @Parameter(description = "NVDB vegobjekttype ID (915 for Vegsystem, 916 for Strekning)", example = "915")
        @PathVariable typeId: Int
    ): ResponseEntity<BackfillControlResponse> {
        return try {
            nvdbDataProducer.startBackfill(typeId)
            ResponseEntity.ok(
                BackfillControlResponse(
                    typeId = typeId,
                    action = "started",
                    message = "Backfill started for type $typeId"
                )
            )
        } catch (exception: Exception) {
            ResponseEntity.badRequest().body(
                BackfillControlResponse(
                    typeId = typeId,
                    action = "start_failed",
                    message = "Failed to start backfill: ${exception.message}"
                )
            )
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
    ): ResponseEntity<BackfillControlResponse> {
        nvdbDataProducer.stopBackfill(typeId)
        return ResponseEntity.ok(
            BackfillControlResponse(
                typeId = typeId,
                action = "stopped",
                message = "Backfill stopped for type $typeId"
            )
        )
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
    suspend fun resetBackfill(
        @Parameter(description = "NVDB vegobjekttype ID", example = "915")
        @PathVariable typeId: Int
    ): ResponseEntity<BackfillControlResponse> {
        return try {
            nvdbDataProducer.resetBackfill(typeId)
            ResponseEntity.ok(
                BackfillControlResponse(
                    typeId = typeId,
                    action = "reset",
                    message = "Backfill reset and restarted for type $typeId"
                )
            )
        } catch (error: Exception) {
            ResponseEntity.badRequest().body(
                BackfillControlResponse(
                    typeId = typeId,
                    action = "reset_failed",
                    message = "Failed to reset backfill: ${error.message}"
                )
            )
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
    ): ResponseEntity<TypeStatusResponse> {
        val progress = nvdbDataProducer.getStatus(typeId)
        return if (progress != null) {
            ResponseEntity.ok(
                TypeStatusResponse(
                    typeId = typeId,
                    mode = progress.mode.name,
                    lastProcessedId = progress.lastProcessedId,
                    hendelseId = progress.hendelseId,
                    backfillStartTime = progress.backfillStartTime,
                    backfillCompletionTime = progress.backfillCompletionTime,
                    lastError = progress.lastError,
                    updatedAt = progress.updatedAt
                )
            )
        } else {
            ResponseEntity.ok(
                TypeStatusResponse(
                    typeId = typeId,
                    mode = "NOT_INITIALIZED",
                    lastProcessedId = null,
                    hendelseId = null,
                    backfillStartTime = null,
                    backfillCompletionTime = null,
                    lastError = null,
                    updatedAt = null
                )
            )
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
        val type915 = nvdbDataProducer.getStatus(NvdbApiClient.TYPE_VEGSYSTEM)
        val type916 = nvdbDataProducer.getStatus(NvdbApiClient.TYPE_STREKNING)

        return ResponseEntity.ok(
            OverallStatusResponse(
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
            )
        )
    }

    @Operation(
        summary = "Query veglenkesekvenser by strekning pattern",
        description = """
            Retrieves all veglenkesekvensIds matching a strekning pattern.

            Strict hierarchy: vegkategori, fase, and vegnummer are always required.

            Supported formats:
            - 'EV6': E-road 6 in use (V = in use), all sections/subsections
            - 'EV6 S3': E-road 6 section 3, all subsections
            - 'EV6 S3D1': Exact match (E-road 6, section 3, subsection 1)
            - 'E V 6 3 1': Space-separated format (backward compatible)

            Valid vegkategori: E, R, F, K, P, S
            Valid fase: V (in use), P (planned), A (under construction), F (temporary)
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Successfully retrieved matches"),
        ApiResponse(responseCode = "400", description = "Invalid key pattern"),
        ApiResponse(responseCode = "404", description = "No matches found"),
        ApiResponse(responseCode = "503", description = "Kafka Streams not available")
    )
    @GetMapping("/strekninger")
    fun findVeglenkesekvenserByStrekning(
        @Parameter(description = "StrekningKey pattern (e.g., 'EV6', 'EV6 S3', 'EV6 S3D1')", required = true)
        @RequestParam key: String
    ): ResponseEntity<StrekningQueryResponse> {
        val state = kafkaStreams.state()
        if (state != KafkaStreams.State.RUNNING) {
            return ResponseEntity.status(503).body(
                StrekningQueryResponse(
                    queryPattern = key,
                    matchedKeys = emptyList(),
                    matchCount = 0,
                    veglenkesekvensIds = emptySet(),
                    error = "Kafka Streams not running (state: $state)"
                )
            )
        }

        try {
            // Parse the flexible input format
            val partialKey = StrekningKey.parseFlexible(key)
            val (fromKey, toKey) = partialKey.toRange()

            val store: ReadOnlyKeyValueStore<StrekningKey, Set<Long>> = kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(
                    "strekningreferanser-store",
                    QueryableStoreTypes.keyValueStore()
                )
            )

            // Perform range query
            val matchedKeys = mutableListOf<String>()
            val allVeglenkesekvensIds = mutableSetOf<Long>()

            store.range(fromKey, toKey).use { iterator ->
                iterator.forEach { keyValue ->
                    matchedKeys.add(keyValue.key.toString())
                    allVeglenkesekvensIds.addAll(keyValue.value)
                }
            }

            return if (matchedKeys.isEmpty()) {
                ResponseEntity.status(404).body(
                    StrekningQueryResponse(
                        queryPattern = key,
                        matchedKeys = emptyList(),
                        matchCount = 0,
                        veglenkesekvensIds = emptySet(),
                        error = "No matches found for pattern: $key"
                    )
                )
            } else {
                ResponseEntity.ok(
                    StrekningQueryResponse(
                        queryPattern = key,
                        matchedKeys = matchedKeys,
                        matchCount = matchedKeys.size,
                        veglenkesekvensIds = allVeglenkesekvensIds,
                        error = null
                    )
                )
            }
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(
                StrekningQueryResponse(
                    queryPattern = key,
                    matchedKeys = emptyList(),
                    matchCount = 0,
                    veglenkesekvensIds = emptySet(),
                    error = e.message ?: "Invalid key format"
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.status(500).body(
                StrekningQueryResponse(
                    queryPattern = key,
                    matchedKeys = emptyList(),
                    matchCount = 0,
                    veglenkesekvensIds = emptySet(),
                    error = "Error querying store: ${e.message}"
                )
            )
        }
    }

    @Operation(
        summary = "Check streams processing lag",
        description = "Returns the consumer lag for each topic partition to determine if processing is up-to-date"
    )
    @GetMapping("/streams/lag")
    fun getStreamsLag(): ResponseEntity<StreamsLagResponse> {
        val state = kafkaStreams.state()
        if (state != KafkaStreams.State.RUNNING) {
            return ResponseEntity.ok(
                StreamsLagResponse(
                    state = state.name,
                    isUpToDate = false,
                    topics = emptyMap(),
                    error = "Kafka Streams not running"
                )
            )
        }

        try {
            val consumerProps = Properties().apply {
                put("bootstrap.servers", bootstrapServers)
                put("group.id", applicationId)
                put("key.deserializer", ByteArrayDeserializer::class.java.name)
                put("value.deserializer", ByteArrayDeserializer::class.java.name)
            }

            val topicLags = mutableMapOf<String, TopicLagInfo>()
            val topics = listOf("nvdb-vegobjekter-915", "nvdb-vegobjekter-916")

            KafkaConsumer<ByteArray, ByteArray>(consumerProps).use { consumer ->
                for (topic in topics) {
                    val partitions = consumer.partitionsFor(topic).map {
                        TopicPartition(it.topic(), it.partition())
                    }

                    // Get committed offsets for the consumer group
                    val committed = consumer.committed(partitions.toSet())

                    // Get end offsets (high water marks)
                    val endOffsets = consumer.endOffsets(partitions)

                    val partitionLags = partitions.map { partition ->
                        val currentOffset = committed[partition]?.offset() ?: 0L
                        val endOffset = endOffsets[partition] ?: 0L
                        val lag = endOffset - currentOffset

                        PartitionLagInfo(
                            partition = partition.partition(),
                            currentOffset = currentOffset,
                            endOffset = endOffset,
                            lag = lag
                        )
                    }

                    val totalLag = partitionLags.sumOf { it.lag }
                    topicLags[topic] = TopicLagInfo(
                        partitions = partitionLags,
                        totalLag = totalLag
                    )
                }
            }

            val isUpToDate = topicLags.values.all { it.totalLag == 0L }

            return ResponseEntity.ok(
                StreamsLagResponse(
                    state = state.name,
                    isUpToDate = isUpToDate,
                    topics = topicLags,
                    error = null
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.status(500).body(
                StreamsLagResponse(
                    state = state.name,
                    isUpToDate = false,
                    topics = emptyMap(),
                    error = "Error checking lag: ${e.message}"
                )
            )
        }
    }
}

data class StrekningQueryResponse(
    val queryPattern: String,           // Input pattern
    val matchedKeys: List<String>,      // Matched StrekningKeys (toString() format)
    val matchCount: Int,                // Number of keys matched
    val veglenkesekvensIds: Set<Long>,  // Aggregated veglenkesekvensIds
    val error: String? = null
)

data class StreamsLagResponse(
    val state: String,
    val isUpToDate: Boolean,
    val topics: Map<String, TopicLagInfo>,
    val error: String? = null
)

data class TopicLagInfo(
    val partitions: List<PartitionLagInfo>,
    val totalLag: Long
)

data class PartitionLagInfo(
    val partition: Int,
    val currentOffset: Long,
    val endOffset: Long,
    val lag: Long
)

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
