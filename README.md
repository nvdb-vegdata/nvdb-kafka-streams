# nvdb-kafka-streams

A Spring Boot 4 application using Kotlin and Kafka Streams to consume and transform road data from the NVDB (Norwegian
Road Database) Uberiket API.

## Overview

The goal of the application is to combine features (vegobjekter) of types 915 Vegsystem and 916 Strekning into
"strekningsreferanser", to be able to query for overlapping road links.

- **Consumes** road data from
  the [NVDB Uberiket API](https://nvdbapiles.atlas.vegvesen.no/swagger-ui/index.html?urls.primaryName=Uberiket+API)
- **Transforms** the data using Kafka Streams
- **Services** endpoints for querying veglenkesekvenser by strekningsreferanse

### Supported Data Types

The application supports fetching various road object types from NVDB:

- **Vegsystem (915)**
- **Strekning (916)**

## Technology Stack

- **Spring Boot 4** - Application framework
- **Kotlin** - Programming language
- **Gradle (Kotlin DSL)** - Build tool
- **Apache Kafka Streams** - Stream processing
- **Ktor Client** - HTTP client for NVDB API

## Getting Started

### Prerequisites

- Java 21 or later
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

## Testing

Run the tests:

```bash
./gradlew test
```

The tests use an embedded Kafka broker for integration testing.

## Related Projects

- [nvdb-tnits-public](https://github.com/nvdb-vegdata/nvdb-tnits-public) - TN-ITS export from NVDB (reference project)

## License

MIT License - see [LICENSE](LICENSE) for details
