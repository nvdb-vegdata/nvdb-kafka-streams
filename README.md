# kafka-at-home

A Spring Boot 3 application using Kotlin and Kafka Streams to consume and transform road data from the NVDB (Norwegian Road Database) Uberiket API.

## Overview

This application:
- **Consumes** road data from the [NVDB Uberiket API](https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/)
- **Transforms** the data using Kafka Streams
- **Produces** enriched data to output Kafka topics

### Supported Data Types

The application supports fetching various road object types from NVDB:
- **Fartsgrense (105)**: Speed limits
- **Vegbredde (583)**: Road width
- **Kjørefelt (616)**: Driving lanes
- **Funksjonsklasse (821)**: Functional road class

## Technology Stack

- **Spring Boot 3.2** - Application framework
- **Kotlin** - Programming language
- **Gradle (Kotlin DSL)** - Build tool
- **Apache Kafka Streams** - Stream processing
- **Spring WebFlux** - Reactive HTTP client for NVDB API

## Getting Started

### Prerequisites

- Java 17 or later
- Docker and Docker Compose (for local Kafka)

### Running Locally

1. **Start Kafka** using Docker Compose:
   ```bash
   docker compose up -d
   ```

2. **Build and run** the application:
   ```bash
   ./gradlew bootRun
   ```

3. **Access the Kafka UI** at http://localhost:8090

### API Endpoints

The application exposes REST endpoints for triggering data fetching:

- `GET /api/nvdb/status` - Get status and available data types
- `POST /api/nvdb/fetch/speedlimits?count=100` - Fetch speed limits from NVDB
- `POST /api/nvdb/fetch/vegobjekter/{typeId}?count=100` - Fetch road objects by type ID

### Kafka Topics

| Topic | Description |
|-------|-------------|
| `nvdb-vegobjekter-raw` | Raw road object data from NVDB API |
| `nvdb-vegobjekter-transformed` | Transformed/enriched road object data |
| `nvdb-fartsgrenser` | Speed limit data (filtered) |

## Configuration

Key configuration properties in `application.yml`:

```yaml
nvdb:
  api:
    base-url: https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/
  producer:
    enabled: false  # Set to true to enable scheduled fetching
    batch-size: 100
    interval-ms: 3600000  # 1 hour

spring:
  kafka:
    bootstrap-servers: localhost:9092
    streams:
      application-id: kafka-at-home-streams
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `NVDB_API_BASE_URL` | NVDB Uberiket API URL | Base URL for NVDB API |
| `NVDB_PRODUCER_ENABLED` | `false` | Enable scheduled data fetching |
| `SERVER_PORT` | `8080` | Application server port |

## Testing

Run the tests:
```bash
./gradlew test
```

The tests use an embedded Kafka broker for integration testing.

## Project Structure

```
src/
├── main/kotlin/no/geirsagberg/kafkaathome/
│   ├── KafkaAtHomeApplication.kt       # Main application entry point
│   ├── api/
│   │   └── NvdbApiClient.kt            # NVDB API client
│   ├── config/
│   │   ├── KafkaStreamsConfig.kt       # Kafka Streams configuration
│   │   ├── NvdbApiProperties.kt        # NVDB API configuration properties
│   │   └── WebClientConfig.kt          # WebClient configuration
│   ├── controller/
│   │   └── NvdbController.kt           # REST API endpoints
│   ├── model/
│   │   └── NvdbModels.kt               # Data models for NVDB objects
│   └── stream/
│       ├── NvdbDataProducer.kt         # Produces NVDB data to Kafka
│       └── NvdbStreamTopology.kt       # Kafka Streams topology
└── test/
    └── kotlin/no/geirsagberg/kafkaathome/
        ├── KafkaAtHomeApplicationTests.kt
        └── stream/
            └── NvdbStreamTopologyTest.kt
```

## Related Projects

- [nvdb-tnits-public](https://github.com/nvdb-vegdata/nvdb-tnits-public) - TN-ITS export from NVDB (reference project)
- [nvdb-api-client](https://github.com/nvdb-vegdata/nvdb-api-client) - Java client for NVDB API

## License

MIT License - see [LICENSE](LICENSE) for details
