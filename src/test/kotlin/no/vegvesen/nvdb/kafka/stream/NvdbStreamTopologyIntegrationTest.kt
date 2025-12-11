package no.vegvesen.nvdb.kafka.stream

import no.vegvesen.nvdb.kafka.model.VegobjektDelta
import no.vegvesen.nvdb.kafka.serialization.kotlinxJsonSerde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.state.KeyValueStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.Properties

class NvdbStreamTopologyIntegrationTest {

    private lateinit var testDriver: TopologyTestDriver
    private lateinit var vegsystemTopic: TestInputTopic<Long, VegobjektDelta>
    private lateinit var strekningTopic: TestInputTopic<Long, VegobjektDelta>

    @BeforeEach
    fun setup() {
        val builder = StreamsBuilder()
        val topologyConfig = NvdbStreamTopology()
        topologyConfig.vegobjektTopology(builder)

        val props = Properties().apply {
            put(StreamsConfig.APPLICATION_ID_CONFIG, "test")
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234")
            put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Long()::class.java.name)
            put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray()::class.java.name)
            put(StreamsConfig.STATE_DIR_CONFIG, "build/kafka-streams-test")
        }

        testDriver = TopologyTestDriver(builder.build(), props)

        vegsystemTopic = testDriver.createInputTopic(
            "nvdb-vegobjekter-915",
            Serdes.Long().serializer(),
            kotlinxJsonSerde<VegobjektDelta>().serializer()
        )

        strekningTopic = testDriver.createInputTopic(
            "nvdb-vegobjekter-916",
            Serdes.Long().serializer(),
            kotlinxJsonSerde<VegobjektDelta>().serializer()
        )
    }

    @AfterEach
    fun tearDown() {
        testDriver.close()
    }

    private fun getStrekningreferanserStore(): KeyValueStore<StrekningKey, Set<Long>> =
        testDriver.getKeyValueStore("strekningreferanser-store")

    @Test
    fun `single vegsystem with single strekning - overlapping extents create reference`() {
        val veglenkesekvensId = 1L

        val vegsystem = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.0, slutt = 1.0))
        )

        val strekning = vegobjekt(
            id = 2L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.0, slutt = 1.0))
        )

        vegsystemTopic.pipeInput(1L, vegobjektDelta(after = vegsystem))
        strekningTopic.pipeInput(2L, vegobjektDelta(after = strekning))

        val key = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val store = getStrekningreferanserStore()

        assertEquals(setOf(veglenkesekvensId), store.get(key))
    }

    @Test
    fun `non-overlapping extents - no references created`() {
        val veglenkesekvensId = 1L

        val vegsystem = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.0, slutt = 0.5))
        )

        val strekning = vegobjekt(
            id = 2L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.6, slutt = 1.0))
        )

        vegsystemTopic.pipeInput(1L, vegobjektDelta(after = vegsystem))
        strekningTopic.pipeInput(2L, vegobjektDelta(after = strekning))

        val key = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val store = getStrekningreferanserStore()

        assertNull(store.get(key))
    }

    @Test
    fun `stedfesting added - new reference appears`() {
        val veglenkesekvensId1 = 1L
        val veglenkesekvensId2 = 2L

        val vegsystemV1 = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(utstrekning(veglenkesekvensId1, start = 0.0, slutt = 1.0))
        )

        val vegsystemV2 = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(
                utstrekning(veglenkesekvensId1, start = 0.0, slutt = 1.0),
                utstrekning(veglenkesekvensId2, start = 0.0, slutt = 1.0)
            )
        )

        val strekning = vegobjekt(
            id = 2L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(
                utstrekning(veglenkesekvensId1, start = 0.0, slutt = 1.0),
                utstrekning(veglenkesekvensId2, start = 0.0, slutt = 1.0)
            )
        )

        vegsystemTopic.pipeInput(1L, vegobjektDelta(after = vegsystemV1))
        strekningTopic.pipeInput(2L, vegobjektDelta(after = strekning))

        val key = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val store = getStrekningreferanserStore()

        assertEquals(setOf(veglenkesekvensId1), store.get(key))

        vegsystemTopic.pipeInput(1L, vegobjektDelta(before = vegsystemV1, after = vegsystemV2))

        assertEquals(setOf(veglenkesekvensId1, veglenkesekvensId2), store.get(key))
    }

    @Test
    fun `stedfesting removed - reference removed`() {
        val veglenkesekvensId1 = 1L
        val veglenkesekvensId2 = 2L

        val vegsystemV1 = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(
                utstrekning(veglenkesekvensId1, start = 0.0, slutt = 1.0),
                utstrekning(veglenkesekvensId2, start = 0.0, slutt = 1.0)
            )
        )

        val vegsystemV2 = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(utstrekning(veglenkesekvensId1, start = 0.0, slutt = 1.0))
        )

        val strekning = vegobjekt(
            id = 2L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(
                utstrekning(veglenkesekvensId1, start = 0.0, slutt = 1.0),
                utstrekning(veglenkesekvensId2, start = 0.0, slutt = 1.0)
            )
        )

        vegsystemTopic.pipeInput(1L, vegobjektDelta(after = vegsystemV1))
        strekningTopic.pipeInput(2L, vegobjektDelta(after = strekning))

        val key = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val store = getStrekningreferanserStore()

        assertEquals(setOf(veglenkesekvensId1, veglenkesekvensId2), store.get(key))

        vegsystemTopic.pipeInput(1L, vegobjektDelta(before = vegsystemV1, after = vegsystemV2))

        assertEquals(setOf(veglenkesekvensId1), store.get(key))
    }

    @Test
    fun `complete vegobjekt deletion - all references removed`() {
        val veglenkesekvensId = 1L

        val vegsystem = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.0, slutt = 1.0))
        )

        val strekning = vegobjekt(
            id = 2L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.0, slutt = 1.0))
        )

        vegsystemTopic.pipeInput(1L, vegobjektDelta(after = vegsystem))
        strekningTopic.pipeInput(2L, vegobjektDelta(after = strekning))

        val key = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val store = getStrekningreferanserStore()

        assertEquals(setOf(veglenkesekvensId), store.get(key))

        vegsystemTopic.pipeInput(1L, vegobjektDelta(before = vegsystem, after = null))

        assertEquals(emptySet<Long>(), store.get(key))
    }

    @Test
    fun `multiple veglenkesekvensIds for same StrekningKey`() {
        val veglenkesekvensId1 = 1L
        val veglenkesekvensId2 = 2L

        val vegsystem1 = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(utstrekning(veglenkesekvensId1, start = 0.0, slutt = 1.0))
        )

        val vegsystem2 = vegobjekt(
            id = 2L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(utstrekning(veglenkesekvensId2, start = 0.0, slutt = 1.0))
        )

        val strekning1 = vegobjekt(
            id = 3L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(utstrekning(veglenkesekvensId1, start = 0.0, slutt = 1.0))
        )

        val strekning2 = vegobjekt(
            id = 4L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(utstrekning(veglenkesekvensId2, start = 0.0, slutt = 1.0))
        )

        vegsystemTopic.pipeInput(1L, vegobjektDelta(after = vegsystem1))
        vegsystemTopic.pipeInput(2L, vegobjektDelta(after = vegsystem2))
        strekningTopic.pipeInput(3L, vegobjektDelta(after = strekning1))
        strekningTopic.pipeInput(4L, vegobjektDelta(after = strekning2))

        val key = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val store = getStrekningreferanserStore()

        assertEquals(setOf(veglenkesekvensId1, veglenkesekvensId2), store.get(key))
    }

    @Test
    fun `extent boundaries - exact start and end matching`() {
        val veglenkesekvensId = 1L

        val vegsystem = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.0, slutt = 0.5))
        )

        val strekning = vegobjekt(
            id = 2L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.5, slutt = 1.0))
        )

        vegsystemTopic.pipeInput(1L, vegobjektDelta(after = vegsystem))
        strekningTopic.pipeInput(2L, vegobjektDelta(after = strekning))

        val key = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val store = getStrekningreferanserStore()

        assertNull(store.get(key))
    }

    @Test
    fun `missing egenskaper - no crash, no reference created`() {
        val veglenkesekvensId = 1L

        val vegsystem = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = emptyMap(),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.0, slutt = 1.0))
        )

        val strekning = vegobjekt(
            id = 2L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.0, slutt = 1.0))
        )

        assertDoesNotThrow {
            vegsystemTopic.pipeInput(1L, vegobjektDelta(after = vegsystem))
            strekningTopic.pipeInput(2L, vegobjektDelta(after = strekning))
        }

        val key = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val store = getStrekningreferanserStore()

        assertNull(store.get(key))
    }

    @Test
    fun `vegsystem arrives before strekning - reference appears after both present`() {
        val veglenkesekvensId = 1L

        val vegsystem = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.0, slutt = 1.0))
        )

        val strekning = vegobjekt(
            id = 2L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(utstrekning(veglenkesekvensId, start = 0.0, slutt = 1.0))
        )

        vegsystemTopic.pipeInput(1L, vegobjektDelta(after = vegsystem))

        val key = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val store = getStrekningreferanserStore()

        assertNull(store.get(key))

        strekningTopic.pipeInput(2L, vegobjektDelta(after = strekning))

        assertEquals(setOf(veglenkesekvensId), store.get(key))
    }

    @Test
    fun `multiple overlaps from single vegsystem and strekning pair`() {
        val veglenkesekvensId1 = 1L
        val veglenkesekvensId2 = 2L

        val vegsystem = vegobjekt(
            id = 1L,
            typeId = 915,
            egenskaper = vegsystemEgenskaper(vegnummer = 6),
            stedfestinger = listOf(
                utstrekning(veglenkesekvensId1, start = 0.0, slutt = 1.0),
                utstrekning(veglenkesekvensId2, start = 0.0, slutt = 1.0)
            )
        )

        val strekning = vegobjekt(
            id = 2L,
            typeId = 916,
            egenskaper = strekningEgenskaper(strekning = 1, delstrekning = 1),
            stedfestinger = listOf(
                utstrekning(veglenkesekvensId1, start = 0.0, slutt = 1.0),
                utstrekning(veglenkesekvensId2, start = 0.0, slutt = 1.0)
            )
        )

        vegsystemTopic.pipeInput(1L, vegobjektDelta(after = vegsystem))
        strekningTopic.pipeInput(2L, vegobjektDelta(after = strekning))

        val key = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val store = getStrekningreferanserStore()

        assertEquals(setOf(veglenkesekvensId1, veglenkesekvensId2), store.get(key))
    }
}
