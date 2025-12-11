# Suggested Commands

## Build & Run

### Build the project
```bash
./gradlew build
```

### Run the application
```bash
./gradlew bootRun
```

### Run with NVDB producer enabled
```bash
NVDB_PRODUCER_ENABLED=true ./gradlew bootRun
```

## Testing

### Run all tests
```bash
./gradlew test
```

### Run specific test class (NO wildcards)
```bash
./gradlew test --tests NvdbStreamTopologyTest
```

## Docker & Kafka

### Start local Kafka cluster
```bash
docker compose up -d
```

### Stop Kafka cluster
```bash
docker compose down
```

### View Kafka logs
```bash
docker logs kafka
```

### Check Kafka container status
```bash
docker ps
```

### Inspect Kafka container
```bash
docker inspect kafka
```

### Execute command in Kafka container
```bash
docker exec kafka [command]
```

## API & Documentation

### Access Swagger UI (when running)
http://localhost:8080/swagger-ui.html

### Access API docs (when running)
http://localhost:8080/api-docs

### Access Kafka UI (when Docker is running)
http://localhost:8090

### Check application health
http://localhost:8080/actuator/health

## Code Generation

### Download and generate NVDB Uberiket API models
```bash
./gradlew generateUberiketApi
```

### Just download the OpenAPI spec
```bash
./gradlew downloadUberiketSpec
```

## Gradle Tasks

### List all available tasks
```bash
./gradlew tasks
```

### Clean build artifacts
```bash
./gradlew clean
```

### Build without tests
```bash
./gradlew build -x test
```

## Darwin (macOS) System Commands

### List files
```bash
ls -la
```

### Search in files (use Grep tool instead when possible)
```bash
grep -r "pattern" .
```

### Find files (use Glob tool instead when possible)
```bash
find . -name "*.kt"
```

### View file with line numbers (use Read tool instead)
```bash
cat -n file.kt
```

### Git operations
```bash
git status
git log --oneline -10
git diff
```

## Development Workflow

### Standard development cycle
1. Start Kafka: `docker compose up -d`
2. Make code changes
3. Run tests: `./gradlew test`
4. Run application: `./gradlew bootRun`
5. Check health: curl http://localhost:8080/actuator/health

### With NVDB producer enabled
1. Start Kafka: `docker compose up -d`
2. Run with producer: `NVDB_PRODUCER_ENABLED=true ./gradlew bootRun`
3. Monitor Kafka UI: http://localhost:8090
