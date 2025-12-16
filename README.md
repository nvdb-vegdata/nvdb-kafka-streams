# nvdb-kafka-streams

A Spring Boot 4 application using Kotlin and Kafka Streams to consume and transform road data from the NVDB (Norwegian
Road Database) Uberiket API.

This is a **proof of concept** application.

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

## Kafka Streams Topology

The application uses Apache Kafka Streams to process and join road data from two input topics:

- **Input Topics**: 
  - `nvdb-vegobjekter-915` (Vegsystem - road system objects)
  - `nvdb-vegobjekter-916` (Strekning - road section objects)

- **Output Store**: 
  - `strekningreferanser-store` - A queryable KTable mapping `StrekningKey` → `Set<Long>` (veglenkesekvens IDs)

- **Processing Guarantees**:
  - Exactly-once semantics (v2)
  - Deserialization error handling with `LogAndContinueExceptionHandler`

### Topology Overview

The following flowchart shows the high-level topology structure:

```mermaid
flowchart TD
    A[nvdb-vegobjekter-915<br/>Vegsystem Topic] --> B1[buildUtstrekningTable]
    C[nvdb-vegobjekter-916<br/>Strekning Topic] --> B2[buildUtstrekningTable]
    
    B1 --> D1[KTable&lt;Long, Set&lt;VegobjektUtstrekning&gt;&gt;<br/>nvdb-vegobjekter-915-utstrekninger]
    B2 --> D2[KTable&lt;Long, Set&lt;VegobjektUtstrekning&gt;&gt;<br/>nvdb-vegobjekter-916-utstrekninger]
    
    D1 --> E[Inner Join<br/>on veglenkesekvensId]
    D2 --> E
    
    E --> F[findOverlappingReferences<br/>Check spatial overlap]
    
    F --> G[KTable&lt;Long, Set&lt;StrekningKey&gt;&gt;<br/>Joined references]
    
    G --> H[StrekningReferenceChangeDetector<br/>State Store: strekning-reference-previous-state]
    
    H --> I[KStream&lt;Long, StrekningReferenceDelta&gt;<br/>Add/Remove events]
    
    I --> J[Group by StrekningKey]
    
    J --> K[Aggregate<br/>Add/Remove veglenkesekvensId]
    
    K --> L[strekningreferanser-store<br/>KTable&lt;StrekningKey, Set&lt;Long&gt;&gt;]
    
    style A fill:#e1f5ff
    style C fill:#e1f5ff
    style L fill:#d4edda
    style H fill:#fff3cd
```

### Delta Processing Flow

The following sequence diagram illustrates how delta events are processed:

```mermaid
sequenceDiagram
    participant Topic as Kafka Topic<br/>(nvdb-vegobjekter-915/916)
    participant Stream as KStream
    participant FlatMap as flatMapToUtstrekningDeltas
    participant Aggregate as Aggregate to KTable
    participant Join as Join Tables
    participant Detector as StrekningReferenceChangeDetector
    participant Store as State Store<br/>(strekning-reference-previous-state)
    participant GroupBy as Group by StrekningKey
    participant FinalAgg as Final Aggregation
    participant QueryStore as strekningreferanser-store

    Topic->>Stream: VegobjektDelta<br/>(before, after)
    Stream->>FlatMap: Process delta
    
    Note over FlatMap: before.toUtstrekninger()<br/>after.toUtstrekninger()
    Note over FlatMap: fjernet = before - after<br/>lagtTil = after - before
    
    FlatMap->>FlatMap: Emit VegobjektUtstrekningDelta<br/>(fjernet=true, utstrekning)<br/>for each removed
    FlatMap->>FlatMap: Emit VegobjektUtstrekningDelta<br/>(fjernet=false, utstrekning)<br/>for each added
    
    FlatMap->>Aggregate: KeyValue(veglenkesekvensId,<br/>VegobjektUtstrekningDelta)
    
    Aggregate->>Aggregate: Aggregate by veglenkesekvensId<br/>if fjernet: acc - utstrekning<br/>else: acc + utstrekning
    
    Aggregate->>Join: KTable[Long → Set[VegobjektUtstrekning]]
    
    Join->>Join: Inner join vegsystem & strekning<br/>on veglenkesekvensId
    
    Note over Join: findOverlappingReferences:<br/>Check spatial overlap<br/>Create Set[StrekningKey]
    
    Join->>Detector: KTable[Long → Set[StrekningKey]]
    
    Detector->>Store: Get previous Set[StrekningKey]<br/>for veglenkesekvensId
    Store-->>Detector: oldKeys
    
    Note over Detector: Compute deltas:<br/>removed = oldKeys - newKeys<br/>added = newKeys - oldKeys
    
    Detector->>Detector: Emit StrekningReferenceDelta<br/>(fjernet=true, key, veglenkesekvensId)<br/>for each removed
    Detector->>Detector: Emit StrekningReferenceDelta<br/>(fjernet=false, key, veglenkesekvensId)<br/>for each added
    
    Detector->>Store: Update state with newKeys
    
    Detector->>GroupBy: KStream[Long → StrekningReferenceDelta]
    
    GroupBy->>FinalAgg: Group by delta.key<br/>(StrekningKey)
    
    FinalAgg->>FinalAgg: if delta.fjernet:<br/>acc - veglenkesekvensId<br/>else:<br/>acc + veglenkesekvensId
    
    FinalAgg->>QueryStore: KTable[StrekningKey → Set[Long]]
```

### Key Components

1. **buildUtstrekningTable**: Converts delta events to spatial extents (utstrekninger) and aggregates them per veglenkesekvens
2. **Join**: Inner joins vegsystem and strekning tables to find overlapping road objects
3. **findOverlappingReferences**: Checks spatial overlap using position ranges and creates StrekningKey references
4. **StrekningReferenceChangeDetector**: Tracks changes in reference sets and emits add/remove events
5. **Final Aggregation**: Groups by StrekningKey and maintains the set of veglenkesekvens IDs for each strekning reference

## Related Projects

- [nvdb-tnits-public](https://github.com/nvdb-vegdata/nvdb-tnits-public) - TN-ITS export from NVDB (reference project)

## License

MIT License - see [LICENSE](LICENSE) for details
