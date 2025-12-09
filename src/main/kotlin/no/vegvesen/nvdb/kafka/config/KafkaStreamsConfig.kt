package no.vegvesen.nvdb.kafka.config

import no.vegvesen.nvdb.kafka.serialization.KotlinxJsonSerializer
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KTable
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaStreams
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.KafkaStreamsConfiguration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import kotlin.jvm.java

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
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String().javaClass.name
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String().javaClass.name
        props[StreamsConfig.STATE_DIR_CONFIG] = "/tmp/kafka-streams"
        return KafkaStreamsConfiguration(props)
    }

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = mutableMapOf<String, Any>()
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KotlinxJsonSerializer::class.java
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory())
    }

    @Bean
    fun kafkaAdmin(): KafkaAdmin {
        val configs = mutableMapOf<String, Any>()
        configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        return KafkaAdmin(configs)
    }

    @Bean
    fun vegsystemTopic(): NewTopic {
        return TopicBuilder.name("nvdb-vegobjekter-915")
            .partitions(topicPartitions)
            .compact()
            .replicas(topicReplicas)
            .build()
    }

    @Bean
    fun strekningTopic(): NewTopic {
        return TopicBuilder.name("nvdb-vegobjekter-916")
            .partitions(topicPartitions)
            .compact()
            .replicas(topicReplicas)
            .build()
    }

    @Bean
    fun vegsystemTable(builder: StreamsBuilder): KTable<String, String> =
        builder.table("nvdb-vegobjekter-915")

    @Bean
    fun strekningTable(builder: StreamsBuilder): KTable<String, String> =
        builder.table("nvdb-vegobjekter-916")

    @Bean
    fun vegsystemStream(builder: StreamsBuilder): KStream<String, String> =
        builder.stream("nvdb-vegobjekter-915")

    @Bean
    fun strekningStream(builder: StreamsBuilder): KStream<String, String> =
        builder.stream("nvdb-vegobjekter-916")

}
