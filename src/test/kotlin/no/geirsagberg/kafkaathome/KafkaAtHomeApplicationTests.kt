package no.geirsagberg.kafkaathome

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = [
        "listeners=PLAINTEXT://localhost:9093",
        "port=9093"
    ],
    topics = ["nvdb-vegobjekter-raw", "nvdb-vegobjekter-transformed", "nvdb-fartsgrenser"]
)
@ActiveProfiles("test")
class KafkaAtHomeApplicationTests {

    @Test
    fun contextLoads() {
        // Verify that the Spring context loads successfully
    }
}
