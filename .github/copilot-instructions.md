# Copilot Instructions for nvdb-kafka-streams

This repository contains a Spring Boot 3 application using Kotlin and Kafka Streams to consume and transform road data
from the NVDB (Norwegian Road Database) Uberiket API.

## Technology Stack

- **Spring Boot 3.2** with Kotlin
- **Gradle** (Kotlin DSL) as build tool
- **Apache Kafka Streams** for stream processing
- **Spring WebFlux** for reactive HTTP client
- **Java 17** as the target JVM version

## Build and Test Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the application locally
./gradlew bootRun

# Start local Kafka infrastructure
docker compose up -d
```

## Project Structure

```
src/main/kotlin/no/geirsagberg/kafkaathome/
├── KafkaAtHomeApplication.kt       # Main application entry point
├── api/                            # External API clients
│   └── NvdbApiClient.kt            # NVDB API client
├── config/                         # Configuration classes
│   ├── KafkaStreamsConfig.kt       # Kafka Streams configuration
│   ├── NvdbApiProperties.kt        # NVDB API configuration properties
│   └── WebClientConfig.kt          # WebClient configuration
├── controller/                     # REST API endpoints
│   └── NvdbController.kt           # NVDB data endpoints
├── model/                          # Data models
│   └── NvdbModels.kt               # NVDB domain objects
└── stream/                         # Kafka Streams processing
    ├── NvdbDataProducer.kt         # Produces data to Kafka
    └── NvdbStreamTopology.kt       # Stream processing topology
```

## Coding Conventions

- Use Kotlin idioms and conventions
- Follow Spring Boot best practices
- Use constructor injection for dependencies
- Prefer data classes for DTOs and domain models
- Use KDoc comments for public APIs
- Use SLF4J for logging via `LoggerFactory`
- Test Kafka Streams topologies using `TopologyTestDriver`

## Kafka Topics

| Topic                          | Description                           |
| ------------------------------ | ------------------------------------- |
| `nvdb-vegobjekter-raw`         | Raw road object data from NVDB API    |
| `nvdb-vegobjekter-transformed` | Transformed/enriched road object data |
| `nvdb-fartsgrenser`            | Speed limit data (filtered)           |

## NVDB Domain Knowledge

The application works with Norwegian road data types:

- **Fartsgrense (105)**: Speed limits
- **Vegbredde (583)**: Road width
- **Kjørefelt (616)**: Driving lanes
- **Funksjonsklasse (821)**: Functional road class

## Testing

- Unit tests use JUnit 5 with Spring Boot Test
- Kafka Streams tests use `kafka-streams-test-utils` with `TopologyTestDriver`
- Test configuration is in `src/test/resources/application-test.yml`
