# Project Structure

## Root Directory Layout

```
nvdb-kafka-streams/
├── src/                          # Source code
├── gradle/                       # Gradle wrapper files
├── data/                         # SQLite database storage
├── docs/                         # Documentation
├── .github/                      # GitHub workflows/actions
├── .claude/                      # Claude Code configuration
├── .opencode/                    # OpenCode IDE configuration
├── build.gradle.kts             # Gradle build configuration
├── settings.gradle.kts          # Gradle settings
├── docker-compose.yml           # Local Kafka setup
├── CLAUDE.md                    # Project-specific Claude instructions
└── README.md                    # Project documentation
```

## Source Code Structure (src/main/kotlin/no/vegvesen/nvdb/kafka/)

```
├── KafkaApplication.kt          # Main application entry point
├── api/
│   └── NvdbApiClient.kt         # Client for NVDB Uberiket API
├── config/
│   ├── HttpClientConfig.kt      # Ktor HTTP client configuration
│   ├── KafkaStreamsConfig.kt    # Kafka Streams setup
│   ├── KafkaTopicProperties.kt  # Topic configuration properties
│   ├── KotlinKafkaConfig.kt     # Kotlin-kafka configuration
│   ├── NvdbApiProperties.kt     # NVDB API configuration binding
│   └── KafkaTopicReadinessListener.kt # Kafka topic initialization
├── controller/
│   └── NvdbController.kt        # REST API endpoints
├── health/
│   └── KafkaTopicHealthIndicator.kt # Kafka health checks
├── model/
│   ├── HendelseModels.kt        # Event/hendelse data models
│   ├── ProducerProgress.kt      # Producer progress tracking
│   └── VegobjektUtstrekning.kt  # Road object extent models
├── repository/
│   └── ProducerProgressRepository.kt # SQLite repository for progress tracking
├── serialization/
│   ├── KotlinxJsonSerializer.kt # Custom Kafka serializer
│   └── ContextualSerializers.kt # Serialization helpers
├── service/
│   └── KafkaTopicReadinessService.kt # Topic readiness management
├── stream/
│   ├── NvdbDataProducer.kt      # Produces NVDB data to Kafka topics
│   └── NvdbStreamTopology.kt    # Kafka Streams topology definition
└── extensions/
    └── IterableExtensions.kt    # Kotlin extension functions
```

## Test Structure (src/test/kotlin/no/vegvesen/nvdb/kafka/)

```
├── KafkaAtHomeApplicationTests.kt  # Application context tests
└── stream/
    └── NvdbStreamTopologyTest.kt   # Stream topology tests
```

## Generated Code

- OpenAPI models are generated in `build/generated/openapi/uberiket/` from the NVDB Uberiket API spec
- Generated models are in package `no.vegvesen.nvdb.api.uberiket.model`

## Resources

- Configuration: `src/main/resources/application.yml`
- Test configuration: `src/test/resources/application-test.yml`
