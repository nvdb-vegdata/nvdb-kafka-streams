package no.geirsagberg.kafkaathome.stream

import com.fasterxml.jackson.databind.ObjectMapper
import no.geirsagberg.kafkaathome.model.Egenskap
import no.geirsagberg.kafkaathome.model.Geometri
import no.geirsagberg.kafkaathome.model.Vegobjekt
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class NvdbStreamTopologyTest {

    private lateinit var testDriver: TopologyTestDriver
    private lateinit var inputTopic: TestInputTopic<String, String>
    private lateinit var outputTopic: TestOutputTopic<String, String>
    private lateinit var speedLimitsTopic: TestOutputTopic<String, String>
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        val props = Properties()
        props[StreamsConfig.APPLICATION_ID_CONFIG] = "test-app"
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String().javaClass.name
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String().javaClass.name

        val builder = StreamsBuilder()
        
        // Build topology manually for testing
        val inputStream = builder.stream<String, String>("nvdb-vegobjekter-raw")
        
        val transformedStream = inputStream
            .mapValues { value -> transformVegobjekt(value) }
            .filter { _, value -> value != null }
            .mapValues { value -> value!! }

        transformedStream.to("nvdb-vegobjekter-transformed")
        
        transformedStream
            .filter { _, value -> isSpeedLimit(value) }
            .to("nvdb-fartsgrenser")

        val topology = builder.build()
        testDriver = TopologyTestDriver(topology, props)

        inputTopic = testDriver.createInputTopic(
            "nvdb-vegobjekter-raw",
            Serdes.String().serializer(),
            Serdes.String().serializer()
        )

        outputTopic = testDriver.createOutputTopic(
            "nvdb-vegobjekter-transformed",
            Serdes.String().deserializer(),
            Serdes.String().deserializer()
        )

        speedLimitsTopic = testDriver.createOutputTopic(
            "nvdb-fartsgrenser",
            Serdes.String().deserializer(),
            Serdes.String().deserializer()
        )
    }

    @AfterEach
    fun tearDown() {
        testDriver.close()
    }

    @Test
    fun `should transform valid vegobjekt`() {
        // Given
        val vegobjekt = Vegobjekt(
            id = 12345L,
            typeId = 105,
            versjon = 1,
            startdato = "2023-01-01",
            egenskaper = mapOf("Fartsgrense" to 80),
            geometri = Geometri(wkt = "POINT(10.0 60.0)", srid = 4326)
        )
        val inputJson = objectMapper.writeValueAsString(vegobjekt)

        // When
        inputTopic.pipeInput("12345", inputJson)

        // Then
        val output = outputTopic.readValue()
        assertNotNull(output)
        
        val outputNode = objectMapper.readTree(output)
        assertEquals(12345L, outputNode.get("id").asLong())
        assertEquals(105, outputNode.get("typeId").asInt())
        assertNotNull(outputNode.get("processedAt"))
    }

    @Test
    fun `should route speed limits to dedicated topic`() {
        // Given
        val speedLimit = Vegobjekt(
            id = 99999L,
            typeId = 105, // Speed limit type
            versjon = 1,
            startdato = "2023-01-01",
            egenskaper = mapOf("Fartsgrense" to 60)
        )
        val inputJson = objectMapper.writeValueAsString(speedLimit)

        // When
        inputTopic.pipeInput("99999", inputJson)

        // Then
        val speedLimitOutput = speedLimitsTopic.readValue()
        assertNotNull(speedLimitOutput)
        
        val outputNode = objectMapper.readTree(speedLimitOutput)
        assertEquals(105, outputNode.get("typeId").asInt())
    }

    @Test
    fun `should filter out invalid records`() {
        // Given
        val invalidJson = "{ this is not valid json }"

        // When
        inputTopic.pipeInput("invalid", invalidJson)

        // Then
        assertTrue(outputTopic.isEmpty)
    }

    @Test
    fun `should not route non-speed-limit objects to speed limits topic`() {
        // Given
        val roadWidth = Vegobjekt(
            id = 88888L,
            typeId = 583, // Road width type (not speed limit)
            versjon = 1,
            startdato = "2023-01-01",
            egenskaper = mapOf("Bredde" to 7.5)
        )
        val inputJson = objectMapper.writeValueAsString(roadWidth)

        // When
        inputTopic.pipeInput("88888", inputJson)

        // Then
        // Should be in output topic
        val output = outputTopic.readValue()
        assertNotNull(output)
        
        // Should NOT be in speed limits topic
        assertTrue(speedLimitsTopic.isEmpty)
    }

    private fun transformVegobjekt(jsonValue: String): String? {
        return try {
            val vegobjekt = objectMapper.readValue(jsonValue, Vegobjekt::class.java)

            val enriched = mapOf(
                "id" to vegobjekt.id,
                "typeId" to vegobjekt.typeId,
                "versjon" to vegobjekt.versjon,
                "startdato" to vegobjekt.startdato,
                "sluttdato" to vegobjekt.sluttdato,
                "egenskaper" to vegobjekt.egenskaper,
                "stedfesting" to vegobjekt.stedfesting,
                "geometri" to vegobjekt.geometri?.wkt,
                "processedAt" to System.currentTimeMillis()
            )

            objectMapper.writeValueAsString(enriched)
        } catch (e: Exception) {
            null
        }
    }

    private fun isSpeedLimit(jsonValue: String): Boolean {
        return try {
            val node = objectMapper.readTree(jsonValue)
            node.get("typeId")?.asInt() == 105
        } catch (e: Exception) {
            false
        }
    }

    @Test
    fun `should preserve stedfesting with geometries field`() {
        val stedfesting = no.geirsagberg.kafkaathome.model.Stedfesting(
            type = "punkt",
            veglenkesekvensider = listOf(
                no.geirsagberg.kafkaathome.model.VeglenkeStedfesting(
                    veglenkesekvensId = 1L,
                    startposisjon = 0.0,
                    sluttposisjon = 1.0
                )
            ),
            geometries = listOf("LINESTRING(10.0 60.0, 10.1 60.1)")
        )

        val vegobjekt = Vegobjekt(
            id = 12345L,
            typeId = 105,
            versjon = 1,
            stedfesting = stedfesting
        )

        val vegobjektJson = objectMapper.writeValueAsString(vegobjekt)

        inputTopic.pipeInput("12345", vegobjektJson)

        val output = outputTopic.readValue()
        assertNotNull(output)

        val outputNode = objectMapper.readTree(output)
        val stedfestingNode = outputNode.get("stedfesting")
        assertNotNull(stedfestingNode)

        val geometries = stedfestingNode.get("geometries")
        assertNotNull(geometries)
        assertTrue(geometries.isArray)
        assertEquals(1, geometries.size())
        assertEquals("LINESTRING(10.0 60.0, 10.1 60.1)", geometries.get(0).asText())
    }
}
