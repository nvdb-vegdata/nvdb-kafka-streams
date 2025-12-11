package no.vegvesen.nvdb.kafka.stream

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration

@Configuration
class NvdbStreamTopology {
    private val logger = LoggerFactory.getLogger(NvdbStreamTopology::class.java)

    private val vegsystemTopic = "nvdb-vegobjekter-915"
    private val strekningTopic = "nvdb-vegobjekter-916"


//    @Bean
//    fun nvdbStreamsTopology(streamsBuilder: StreamsBuilder): KStream<String, String> {
//        logger.info("Building NVDB Kafka Streams topology")
//
////        TODO()
////        val inputStream: KStream<String, String> = streamsBuilder.stream(
////            inputTopic,
////            Consumed.with(Serdes.String(), Serdes.String())
////        )
////
////        // Transform and filter the data
////        val transformedStream = inputStream
////            .peek { key, value -> logger.debug("Processing record with key: {}", key) }
////            .mapValues { value -> parseVegobjekt(value) }
////            .filter { _, vegobjekt -> vegobjekt != null }
////            .mapValues { vegobjekt -> transformVegobjekt(vegobjekt) }
////            .filter { _, value -> value != null }
////            .mapValues { value -> value!! }
////
////        // Produce to the main output topic
////        transformedStream.to(
////            outputTopic,
////            Produced.with(Serdes.String(), Serdes.String())
////        )
////
////        // Branch speed limits to a dedicated topic
////        val speedLimitsStream = transformedStream
////            .filter { _, value -> isSpeedLimit(value) }
////
////        speedLimitsStream.to(
////            speedLimitsTopic,
////            Produced.with(Serdes.String(), Serdes.String())
////        )
////
////        logger.info("NVDB Kafka Streams topology built successfully")
////        return transformedStream
//    }
}
