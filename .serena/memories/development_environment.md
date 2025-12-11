# Development Environment

## System Requirements

- **Operating System**: macOS (Darwin) - currently running on Darwin 24.6.0
- **Java**: Version 21 or later (JVM toolchain 21)
- **Docker**: For running local Kafka cluster
- **Docker Compose**: For orchestrating Kafka and Kafka UI

## IDE Setup

- **Kotlin LSP**: Configured via `opencode.json` with command `kotlin-lsp --stdio`
- **File Extensions**: `.kt`, `.kts`
- **IntelliJ IDEA**: Project uses `.idea` directory for IntelliJ configuration

## Local Development Setup

### 1. Start Kafka Infrastructure

```bash
docker compose up -d
```

This starts:

- **Apache Kafka 4.1.1** on port 9092 (localhost) and 29092 (internal)
- **Kafka UI** on port 8090 (http://localhost:8090)

Kafka configuration:

- Single node KRaft mode (no ZooKeeper)
- Auto-create topics enabled
- 5 partitions per topic
- Compaction enabled with 1-hour segment rotation
- Replication factor: 1 (single broker)

### 2. Run the Application

```bash
./gradlew bootRun
```

The application starts on port 8080 (configurable via `SERVER_PORT` env var).

### 3. Access Services

- **Application**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health
- **Kafka UI**: http://localhost:8090
- **Metrics**: http://localhost:8080/actuator/metrics

## Environment Variables

### Required for Custom Configuration

| Variable                  | Default                                                 | Purpose                                |
|---------------------------|---------------------------------------------------------|----------------------------------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`                                        | Kafka broker connection                |
| `NVDB_API_BASE_URL`       | `https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/` | NVDB API endpoint                      |
| `NVDB_PRODUCER_ENABLED`   | `true`                                                  | Enable/disable scheduled data fetching |
| `SERVER_PORT`             | `8080`                                                  | Application HTTP port                  |
| `SQLITE_DB_PATH`          | `./data/nvdb-kafka-streams.db`                          | SQLite database location               |

### Optional Configuration

| Variable                         | Default | Purpose                                 |
|----------------------------------|---------|-----------------------------------------|
| `KAFKA_TOPIC_PARTITIONS`         | `5`     | Number of partitions for created topics |
| `KAFKA_TOPIC_REPLICAS`           | `1`     | Replication factor                      |
| `KAFKA_TOPICS_READINESS_ENABLED` | `true`  | Wait for topics on startup              |
| `NVDB_BACKFILL_BATCH_SIZE`       | `100`   | Batch size for backfill mode            |
| `NVDB_UPDATES_BATCH_SIZE`        | `100`   | Batch size for updates mode             |

## Database

- **Type**: SQLite (embedded)
- **Location**: `./data/nvdb-kafka-streams.db`
- **Purpose**: Tracks producer progress (last fetched object IDs, timestamps)
- **Initialization**: Automatic via Spring `sql.init.mode=always`

## Testing Environment

- Tests use **embedded Kafka** broker (no Docker needed for tests)
- Test configuration: `src/test/resources/application-test.yml`
- Test database is in-memory or temporary location

## Build Artifacts

- Build output: `build/` directory
- Generated OpenAPI models: `build/generated/openapi/uberiket/`
- Compiled classes: `build/classes/kotlin/main/`
- Test reports: `build/reports/tests/`

## Git Repository

- Current branch: `main`
- Remote: GitHub repository
- `.gitignore` excludes build artifacts, IDE files, and data directory
