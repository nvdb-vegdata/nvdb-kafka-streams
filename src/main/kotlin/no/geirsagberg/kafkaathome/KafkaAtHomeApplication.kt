package no.geirsagberg.kafkaathome

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KafkaAtHomeApplication

fun main(args: Array<String>) {
    runApplication<KafkaAtHomeApplication>(*args)
}
