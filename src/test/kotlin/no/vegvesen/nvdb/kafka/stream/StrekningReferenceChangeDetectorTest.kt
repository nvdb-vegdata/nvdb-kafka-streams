package no.vegvesen.nvdb.kafka.stream

import no.vegvesen.nvdb.kafka.serialization.kotlinxJsonSerde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.Stores
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import java.util.Properties

class StrekningReferenceChangeDetectorTest {

    private lateinit var testDriver: TopologyTestDriver
    private lateinit var inputTopic: TestInputTopic<Long, Set<StrekningKey>>
    private lateinit var outputTopic: TestOutputTopic<Long, StrekningReferenceDelta>

    @BeforeEach
    fun setup() {
        val topology = Topology()

        topology.addSource(
            "source",
            Serdes.Long().deserializer(),
            kotlinxJsonSerde<Set<StrekningKey>>().deserializer(),
            "input-topic"
        )

        topology.addProcessor(
            "processor",
            ProcessorSupplier { StrekningReferenceChangeDetector() },
            "source"
        )

        val storeBuilder = Stores.keyValueStoreBuilder(
            Stores.inMemoryKeyValueStore(StrekningReferenceChangeDetector.STORE_NAME),
            Serdes.Long(),
            kotlinxJsonSerde<Set<StrekningKey>>()
        ).withLoggingDisabled()

        topology.addStateStore(storeBuilder, "processor")

        topology.addSink(
            "sink",
            "output-topic",
            Serdes.Long().serializer(),
            kotlinxJsonSerde<StrekningReferenceDelta>().serializer(),
            "processor"
        )

        val props = Properties().apply {
            put(StreamsConfig.APPLICATION_ID_CONFIG, "test")
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234")
        }

        testDriver = TopologyTestDriver(topology, props)
        inputTopic = testDriver.createInputTopic(
            "input-topic",
            Serdes.Long().serializer(),
            kotlinxJsonSerde<Set<StrekningKey>>().serializer()
        )
        outputTopic = testDriver.createOutputTopic(
            "output-topic",
            Serdes.Long().deserializer(),
            kotlinxJsonSerde<StrekningReferenceDelta>().deserializer()
        )
    }

    @AfterEach
    fun tearDown() {
        testDriver.close()
    }

    private fun getStateStore(): KeyValueStore<Long, Set<StrekningKey>> =
        testDriver.getKeyValueStore(StrekningReferenceChangeDetector.STORE_NAME)

    @Test
    fun `initial state - adding first references emits additions only`() {
        val veglenkesekvensId = 1L
        val key1 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val key2 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 2)
        val newKeys = setOf(key1, key2)

        inputTopic.pipeInput(veglenkesekvensId, newKeys)

        val output = outputTopic.readKeyValuesToList()
        assertEquals(2, output.size)

        val deltas = output.map { it.value }
        assertEquals(2, deltas.count { !it.fjernet })
        assertEquals(setOf(key1, key2), deltas.map { it.key }.toSet())

        assertEquals(newKeys, getStateStore().get(veglenkesekvensId))
    }

    @Test
    fun `no changes - same keys produces no deltas`() {
        val veglenkesekvensId = 1L
        val keys = setOf(strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1))

        inputTopic.pipeInput(veglenkesekvensId, keys)
        outputTopic.readKeyValuesToList()

        inputTopic.pipeInput(veglenkesekvensId, keys)

        val output = outputTopic.readKeyValuesToList()
        assertEquals(0, output.size)

        assertEquals(keys, getStateStore().get(veglenkesekvensId))
    }

    @Test
    fun `pure additions - new keys added to existing set`() {
        val veglenkesekvensId = 1L
        val key1 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val key2 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 2)
        val key3 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 3)

        inputTopic.pipeInput(veglenkesekvensId, setOf(key1, key2))
        outputTopic.readKeyValuesToList()

        inputTopic.pipeInput(veglenkesekvensId, setOf(key1, key2, key3))

        val output = outputTopic.readKeyValuesToList()
        assertEquals(1, output.size)
        assertEquals(
            StrekningReferenceDelta(fjernet = false, key = key3, veglenkesekvensId = veglenkesekvensId),
            output[0].value
        )

        assertEquals(setOf(key1, key2, key3), getStateStore().get(veglenkesekvensId))
    }

    @Test
    fun `pure removals - keys removed from existing set`() {
        val veglenkesekvensId = 1L
        val key1 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val key2 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 2)
        val key3 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 3)

        inputTopic.pipeInput(veglenkesekvensId, setOf(key1, key2, key3))
        outputTopic.readKeyValuesToList()

        inputTopic.pipeInput(veglenkesekvensId, setOf(key1))

        val output = outputTopic.readKeyValuesToList()
        assertEquals(2, output.size)

        val removedKeys = output.map { it.value.key }.toSet()
        assertEquals(setOf(key2, key3), removedKeys)

        output.forEach {
            assertEquals(true, it.value.fjernet)
            assertEquals(veglenkesekvensId, it.value.veglenkesekvensId)
        }

        assertEquals(setOf(key1), getStateStore().get(veglenkesekvensId))
    }

    @Test
    fun `mixed add and remove - some keys added, some removed`() {
        val veglenkesekvensId = 1L
        val key1 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val key2 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 2)
        val key3 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 3)

        inputTopic.pipeInput(veglenkesekvensId, setOf(key1, key2))
        outputTopic.readKeyValuesToList()

        inputTopic.pipeInput(veglenkesekvensId, setOf(key1, key3))

        val output = outputTopic.readKeyValuesToList()
        assertEquals(2, output.size)

        val removed = output.filter { it.value.fjernet }
        val added = output.filter { !it.value.fjernet }

        assertEquals(1, removed.size)
        assertEquals(key2, removed[0].value.key)

        assertEquals(1, added.size)
        assertEquals(key3, added[0].value.key)

        assertEquals(setOf(key1, key3), getStateStore().get(veglenkesekvensId))
    }

    @Test
    fun `complete removal - all keys removed, store entry deleted`() {
        val veglenkesekvensId = 1L
        val key1 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val key2 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 2)

        inputTopic.pipeInput(veglenkesekvensId, setOf(key1, key2))
        outputTopic.readKeyValuesToList()

        inputTopic.pipeInput(veglenkesekvensId, emptySet())

        val output = outputTopic.readKeyValuesToList()
        assertEquals(2, output.size)

        output.forEach {
            assertEquals(true, it.value.fjernet)
        }

        assertNull(getStateStore().get(veglenkesekvensId))
    }

    @Test
    fun `empty set handling - empty input after non-empty state`() {
        val veglenkesekvensId = 1L
        val keys = setOf(strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1))

        inputTopic.pipeInput(veglenkesekvensId, keys)
        outputTopic.readKeyValuesToList()

        inputTopic.pipeInput(veglenkesekvensId, emptySet())

        val output = outputTopic.readKeyValuesToList()
        assertEquals(1, output.size)
        assertEquals(true, output[0].value.fjernet)

        assertNull(getStateStore().get(veglenkesekvensId))
    }

    @Test
    fun `multiple veglenkesekvensIds - state isolation between different keys`() {
        val veglenkesekvensId1 = 1L
        val veglenkesekvensId2 = 2L
        val key1 = strekningKey(vegnummer = 6, strekning = 1, delstrekning = 1)
        val key2 = strekningKey(vegnummer = 6, strekning = 2, delstrekning = 1)

        inputTopic.pipeInput(veglenkesekvensId1, setOf(key1))
        outputTopic.readKeyValuesToList()

        inputTopic.pipeInput(veglenkesekvensId2, setOf(key2))

        val output = outputTopic.readKeyValuesToList()
        assertEquals(1, output.size)
        assertEquals(veglenkesekvensId2, output[0].value.veglenkesekvensId)

        assertEquals(setOf(key1), getStateStore().get(veglenkesekvensId1))
        assertEquals(setOf(key2), getStateStore().get(veglenkesekvensId2))
    }
}
