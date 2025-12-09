package no.vegvesen.nvdb.kafka

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@EmbeddedKafka(
    partitions = 5,
    topics = ["nvdb-vegobjekter-915", "nvdb-vegobjekter-916"]
)
@ActiveProfiles("test")
class KafkaAtHomeApplicationTests {

    @Test
    fun contextLoads() {
        // Verify that the Spring context loads successfully
    }
}
