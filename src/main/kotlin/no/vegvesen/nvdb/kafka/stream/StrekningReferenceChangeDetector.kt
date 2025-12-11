package no.vegvesen.nvdb.kafka.stream

import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore

class StrekningReferenceChangeDetector : Processor<Long, Set<StrekningKey>, Long, StrekningReferenceDelta> {
    private lateinit var context: ProcessorContext<Long, StrekningReferenceDelta>
    private lateinit var stateStore: KeyValueStore<Long, Set<StrekningKey>>

    override fun init(context: ProcessorContext<Long, StrekningReferenceDelta>) {
        this.context = context
        this.stateStore = context.getStateStore(STORE_NAME)
    }

    override fun process(record: Record<Long, Set<StrekningKey>>) {
        val veglenkesekvensId = record.key()
        val newKeys = record.value() ?: emptySet()
        val oldKeys = stateStore.get(veglenkesekvensId) ?: emptySet()

        // Emit removals for keys that were removed
        (oldKeys - newKeys).forEach { key ->
            context.forward(
                record.withKey(veglenkesekvensId)
                    .withValue(
                        StrekningReferenceDelta(
                            fjernet = true,
                            key = key,
                            veglenkesekvensId = veglenkesekvensId
                        )
                    )
            )
        }

        // Emit additions for keys that were added
        (newKeys - oldKeys).forEach { key ->
            context.forward(
                record.withKey(veglenkesekvensId)
                    .withValue(
                        StrekningReferenceDelta(
                            fjernet = false,
                            key = key,
                            veglenkesekvensId = veglenkesekvensId
                        )
                    )
            )
        }

        // Update state
        if (newKeys.isEmpty()) {
            stateStore.delete(veglenkesekvensId)
        } else {
            stateStore.put(veglenkesekvensId, newKeys)
        }
    }

    companion object {
        const val STORE_NAME = "strekning-reference-previous-state"
    }
}
