# NVDB Domain Knowledge

## About NVDB
**NVDB** (Nasjonal Vegdatabank / Norwegian Road Database) is Norway's national road database maintained by the Norwegian Public Roads Administration (Statens vegvesen).

## NVDB Uberiket API
- **Base URL**: https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/
- **API Documentation**: https://nvdbapiles.atlas.vegvesen.no/api-docs/uberiket
- **Purpose**: Provides access to road object data and events (hendelser) for the entire Norwegian road network

## Key Concepts

### Vegobjekter (Road Objects)
Road objects represent physical or logical entities on the road network. Each object type has a unique numeric ID.

#### Supported Object Types in This Application
| Type ID | Name (Norwegian) | Name (English) | Description |
|---------|------------------|----------------|-------------|
| 105 | Fartsgrense | Speed Limit | Speed limit regulations |
| 583 | Vegbredde | Road Width | Width measurements of road segments |
| 616 | Kjørefelt | Driving Lane | Individual driving lanes |
| 821 | Funksjonsklasse | Functional Road Class | Classification by road function |
| 915 | *(Type 915)* | - | Scheduled type (60s interval) |
| 916 | *(Type 916)* | - | Scheduled type (60s interval) |

### Hendelser (Events)
Events represent changes or incidents related to road objects:
- **Creation** of new road objects
- **Updates** to existing objects
- **Deletion** of objects
- Temporal validity changes

### Data Fetching Modes

#### 1. Backfill Mode
- Fetches historical/existing road objects in batches
- Used to populate Kafka topics with current state
- Batch size: configurable (default 100)
- Tracks progress via last fetched object ID in SQLite

#### 2. Updates Mode
- Fetches recent changes/events
- Used for incremental updates after backfill
- Batch size: configurable (default 100)
- Tracks progress via last event timestamp

### Object Structure
Road objects typically include:
- **ID**: Unique identifier for the object
- **Type**: Object type ID (e.g., 105 for speed limits)
- **Geometry**: Geographic location/shape
- **Properties**: Type-specific attributes (e.g., speed limit value)
- **Metadata**: Versioning, validity periods, data quality indicators
- **Utstrekning** (Extent): Road network reference (which road segment(s))

## API Integration Details

### OpenAPI Code Generation
- API models are auto-generated from the OpenAPI spec
- Generation task: `./gradlew generateUberiketApi`
- Generated package: `no.vegvesen.nvdb.api.uberiket.model`
- Uses kotlinx-datetime for temporal types
- Uses kotlinx-serialization for JSON handling

### API Client (`NvdbApiClient`)
- Built with Ktor HTTP client
- Coroutine-based for reactive/async operations
- Content negotiation with JSON
- Request logging enabled
- Handles pagination for large datasets

### Rate Limiting & Throttling
- The NVDB API has rate limits
- Application implements scheduling to respect limits
- Configurable intervals (default: 60 seconds for types 915/916)

## Related NVDB Projects
- **nvdb-tnits-public**: TN-ITS export from NVDB (GitHub: nvdb-vegdata/nvdb-tnits-public)
- **nvdb-api-client**: Official Java client for NVDB API (GitHub: nvdb-vegdata/nvdb-api-client)

## Data Pipeline in This Application

### Pipeline Flow
```
NVDB Uberiket API
    ↓ (HTTP/Ktor)
NvdbApiClient
    ↓ (Kotlin coroutines)
NvdbDataProducer
    ↓ (Kafka Producer)
nvdb-vegobjekter-raw (Kafka Topic)
    ↓ (Kafka Streams)
NvdbStreamTopology
    ↓ (transformations)
nvdb-vegobjekter-transformed (Kafka Topic)
    ↓ (filtering)
nvdb-fartsgrenser (Kafka Topic, speed limits only)
```

### Progress Tracking
- **SQLite Database** stores producer progress
- Tracks last fetched object ID for each type
- Tracks last event timestamp for updates mode
- Prevents duplicate processing
- Enables resume after restarts

## Working with NVDB Data

### Common Operations
1. **Fetch specific object type**: POST `/api/nvdb/fetch/vegobjekter/{typeId}?count=100`
2. **Fetch speed limits**: POST `/api/nvdb/fetch/speedlimits?count=100`
3. **Check status**: GET `/api/nvdb/status`

### Data Transformation
- Raw objects are enriched/transformed via Kafka Streams
- Transformations defined in `NvdbStreamTopology`
- Filtered outputs for specific use cases (e.g., speed limits only)

## Configuration
```yaml
nvdb:
  api:
    base-url: https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/
  producer:
    enabled: true  # Enable scheduled fetching
    backfill:
      batch-size: 100
    updates:
      batch-size: 100
    schedule:
      type915: 60000  # milliseconds
      type916: 60000
```
