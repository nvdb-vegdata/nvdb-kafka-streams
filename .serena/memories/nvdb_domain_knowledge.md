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
- Tracks progress via last fetched object ID in SQLite

#### 2. Updates Mode
- Fetches recent changes/events
- Used for incremental updates after backfill
- Tracks progress via last event ID

### Object Structure
Road objects typically include:
- **ID**: Unique identifier for the object
- **Type**: Object type ID (e.g., 915 for road system)
- **Geometry**: Geographic location/shape
- **Properties**: Type-specific attributes (e.g., road number)
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

## Related NVDB Projects
- **nvdb-tnits-public**: TN-ITS export from NVDB (GitHub: nvdb-vegdata/nvdb-tnits-public)

### Progress Tracking
- **SQLite Database** stores producer progress
- Tracks last fetched object ID for each type
- Tracks last event timestamp for updates mode
- Prevents duplicate processing
- Enables resume after restarts

## Working with NVDB Data

### Data Transformation
- Raw objects are enriched/transformed via Kafka Streams
- Transformations defined in `NvdbStreamTopology`
