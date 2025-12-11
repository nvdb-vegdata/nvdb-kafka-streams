package no.vegvesen.nvdb.kafka.config

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaStreams
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.KafkaStreamsConfiguration
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin

@Configuration
@EnableKafkaStreams
class KafkaStreamsConfig {

    @Value($$"${spring.kafka.streams.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value($$"${spring.kafka.streams.application-id}")
    private lateinit var applicationId: String

    @Value($$"${kafka.topics.partitions:5}")
    private var topicPartitions: Int = 5

    @Value($$"${kafka.topics.replicas:1}")
    private var topicReplicas: Int = 1

    @Bean(name = [KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME])
    fun kafkaStreamsConfig(): KafkaStreamsConfiguration {
        val props = mutableMapOf<String, Any>()
        props[StreamsConfig.APPLICATION_ID_CONFIG] = applicationId
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers

        // Increase max message size for producer (default is 1MB)
        props["max.request.size"] = 10 * 1024 * 1024 // 10MB

        // Configure processing guarantees
        props[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = StreamsConfig.EXACTLY_ONCE_V2

        // Add better error handling
        props[StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG] =
            "org.apache.kafka.streams.errors.LogAndContinueExceptionHandler"

        return KafkaStreamsConfiguration(props)
    }

    @Bean
    fun kafkaAdmin(): KafkaAdmin {
        val configs = mutableMapOf<String, Any>()
        configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        return KafkaAdmin(configs)
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    fun kafkaStreams(streamsBuilderFactoryBean: StreamsBuilderFactoryBean): KafkaStreams =
        streamsBuilderFactoryBean.kafkaStreams!!

    @Bean
    fun vegsystemTopic(): NewTopic {
        return TopicBuilder.name("nvdb-vegobjekter-915")
            .partitions(topicPartitions)
            .compact()
            .replicas(topicReplicas)
            .config("retention.ms", "604800000")  // 7 days
            .config("segment.ms", "86400000")      // 1 day
            .config("max.message.bytes", "10485760") // 10MB
            .build()
    }

    @Bean
    fun strekningTopic(): NewTopic {
        return TopicBuilder.name("nvdb-vegobjekter-916")
            .partitions(topicPartitions)
            .compact()
            .replicas(topicReplicas)
            .config("retention.ms", "604800000")  // 7 days
            .config("segment.ms", "86400000")      // 1 day
            .config("max.message.bytes", "10485760") // 10MB
            .build()
    }

//    @Bean
//    fun vegsystemTable(builder: StreamsBuilder): KTable<String, String> =
//        builder.table("nvdb-vegobjekter-915")
//
//    @Bean
//    fun strekningTable(builder: StreamsBuilder): KTable<String, String> =
//        builder.table("nvdb-vegobjekter-916")
//
//    @Bean
//    fun vegsystemStream(builder: StreamsBuilder): KStream<String, String> =
//        builder.stream("nvdb-vegobjekter-915")
//
//    @Bean
//    fun strekningStream(builder: StreamsBuilder): KStream<String, String> =
//        builder.stream("nvdb-vegobjekter-916")

}
