# NVDB Producer Architecture

## Overview

The NVDB Producer system fetches road data from the Norwegian Road Database (NVDB) and produces it to Kafka topics. It operates in two modes:

1. **BACKFILL Mode**: Initial bulk fetch of all vegobjekter for a type
2. **UPDATES Mode**: Continuous polling of hendelser (events) for incremental updates

## Supported Types

- **Type 915 (Vegsystem)**: Road system objects → Topic: `nvdb-vegobjekter-915`
- **Type 916 (Strekning)**: Road section objects → Topic: `nvdb-vegobjekter-916`

## Architecture

### Mode Transitions

```
[NOT_INITIALIZED]
       ↓ (POST /api/nvdb/backfill/{typeId}/start)
   [BACKFILL MODE]
       ↓ (fetch all vegobjekter with pagination)
       ↓ (when batch < batch_size)
   [UPDATES MODE]
       ↓ (continuous polling of hendelser)
       ↓ (fetch full vegobjekt for each hendelse)
```

### Backfill Mode

1. **Initialize**: Query latest hendelse ID from `/hendelser/vegobjekter/{typeId}` and store it for later
2. **Paginate**: Fetch vegobjekter using `/vegobjekter/{typeId}/stream` with pagination
3. **Produce**: Send each vegobjekt to type-specific Kafka topic
4. **Track Progress**: Save progress after each batch (enables resume capability)
5. **Transition**: When batch size < configured size, automatically switch to UPDATES mode

### Updates Mode

1. **Poll**: Query `/hendelser/vegobjekter/{typeId}` starting from stored hendelse ID
2. **Fetch Details**: For each hendelse, fetch full vegobjekt using `/vegobjekter/{typeId}/{objectId}`
3. **Produce**: Send full vegobjekt to Kafka topic
4. **Update Progress**: Save last processed hendelse ID after each batch
5. **Repeat**: Continue polling on schedule (every minute by default)

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS producer_progress (
    type_id INTEGER PRIMARY KEY,
    mode TEXT NOT NULL CHECK(mode IN ('BACKFILL', 'UPDATES')),
    last_processed_id INTEGER,           -- Vegobjekt ID (for backfill pagination)
    hendelse_id INTEGER,                 -- Hendelse ID (for updates polling)
    backfill_start_time TEXT,
    backfill_completion_time TEXT,
    last_error TEXT,
    updated_at TEXT NOT NULL
);
```

## REST API Endpoints

### Interactive API Documentation

The API is documented using OpenAPI 3.0 with Swagger UI:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs
- **OpenAPI YAML**: http://localhost:8080/api-docs.yaml

The Swagger UI provides an interactive interface to explore and test all endpoints.

### Endpoints

### Start Backfill
```
POST /api/nvdb/backfill/{typeId}/start

Response:
{
  "typeId": 915,
  "action": "started",
  "message": "Backfill started for type 915"
}
```

### Stop Backfill
```
POST /api/nvdb/backfill/{typeId}/stop

Response:
{
  "typeId": 915,
  "action": "stopped",
  "message": "Backfill stopped for type 915"
}
```

### Reset Backfill
```
POST /api/nvdb/backfill/{typeId}/reset

Response:
{
  "typeId": 915,
  "action": "reset",
  "message": "Backfill reset and restarted for type 915"
}
```

### Get Type Status
```
GET /api/nvdb/status/{typeId}

Response:
{
  "typeId": 915,
  "mode": "BACKFILL",
  "lastProcessedId": 12345,
  "hendelseId": 188455359,
  "backfillStartTime": "2025-12-04T10:00:00Z",
  "backfillCompletionTime": null,
  "lastError": null,
  "updatedAt": "2025-12-04T10:30:00Z"
}
```

### Get Overall Status
```
GET /api/nvdb/status

Response:
{
  "status": "running",
  "types": [
    {
      "typeId": 915,
      "name": "Vegsystem",
      "mode": "BACKFILL",
      "topic": "nvdb-vegobjekter-915"
    },
    {
      "typeId": 916,
      "name": "Strekning",
      "mode": "UPDATES",
      "topic": "nvdb-vegobjekter-916"
    }
  ]
}
```

## Configuration

```yaml
nvdb:
  producer:
    enabled: true
    backfill:
      batch-size: 100          # Vegobjekter per batch in backfill
    updates:
      batch-size: 100          # Hendelser per batch in updates
    schedule:
      type915: 60000           # Schedule interval for type 915 (ms)
      type916: 60000           # Schedule interval for type 916 (ms)
```

### Environment Variables
- `NVDB_PRODUCER_ENABLED` (default: false)
- `NVDB_BACKFILL_BATCH_SIZE` (default: 100)
- `NVDB_UPDATES_BATCH_SIZE` (default: 100)
- `NVDB_SCHEDULE_TYPE915` (default: 60000ms = 1 minute)
- `NVDB_SCHEDULE_TYPE916` (default: 60000ms = 1 minute)

## Error Handling

### Network Errors
- All API calls wrapped in try-catch
- Errors logged with context (typeId, object ID, hendelse ID)
- Last error saved to database for debugging
- Processing continues on next scheduled invocation
- No retry logic within single invocation (rely on schedule)

### Partial Batch Failures
- In updates mode: Skip failed vegobjekt fetches, continue with remaining hendelser
- Progress saved with last successful hendelse ID
- Failed items logged but don't block batch

### Database Failures
- Log errors but don't crash
- Next invocation will reprocess same batch (idempotent)

### Kafka Production Failures
- Use `CompletableFuture.whenComplete()` to log errors
- Don't stop batch processing on individual failures
- Monitor via Kafka metrics

### Mode Transition Failures
- If `getLatestHendelseId()` fails, log error and stay in BACKFILL mode
- Manual intervention via reset endpoint if needed

## Parallel Processing

Each type (915, 916) is processed independently:
- **Separate scheduled tasks**: `@Scheduled` methods for each type
- **Separate database records**: Independent progress tracking
- **Separate Kafka topics**: `nvdb-vegobjekter-915` and `nvdb-vegobjekter-916`
- **AtomicBoolean locks**: Prevent concurrent runs of same type

## Performance Considerations

### Backfill Mode
- Batch size: 100 vegobjekter (configurable)
- Frequency: Every minute per type by default
- Database write per batch (atomic progress updates)
- Can increase schedule frequency for faster backfill

### Updates Mode
- Polls every minute by default
- Batch size: 100 hendelser
- N+1 API calls: 1 for hendelser, N for individual vegobjekter
- Consider adding batch vegobjekt fetch if API supports it

### Database
- SQLite suitable for this workload (2 concurrent writes max)
- Consider PostgreSQL for production with high-frequency updates
- Primary key on `type_id` provides fast lookups

## Monitoring

Monitor via:
1. **REST API**: `GET /api/nvdb/status` endpoint
2. **Database**: Queries on `producer_progress` table
3. **Application logs**: INFO level for progress, DEBUG for details
4. **Kafka metrics**: Consumer lag on topics `nvdb-vegobjekter-915` and `nvdb-vegobjekter-916`

### Key Metrics to Track
- Backfill progress: `lastProcessedId` vs total objects
- Updates lag: Time between hendelse creation and processing
- Error rate: Check `last_error` field
- Processing rate: Objects/hendelser processed per minute

## Deployment Guide

### Initial Deployment

1. **Stop Producer** (if upgrading from old version):
   ```bash
   export NVDB_PRODUCER_ENABLED=false
   ```

2. **Deploy Application**: Deploy new code with schema changes

3. **Verify Schema**: Check that new table structure is created:
   ```sql
   .schema producer_progress
   ```

4. **Initialize Backfill**: Start backfill for both types:
   ```bash
   curl -X POST http://localhost:8080/api/nvdb/backfill/915/start
   curl -X POST http://localhost:8080/api/nvdb/backfill/916/start
   ```

5. **Enable Producer**:
   ```bash
   export NVDB_PRODUCER_ENABLED=true
   ```

6. **Monitor Progress**:
   ```bash
   curl http://localhost:8080/api/nvdb/status
   ```

   Or use the interactive Swagger UI at http://localhost:8080/swagger-ui.html

### Upgrading from Old Schema

The new schema is incompatible with the old version. Options:

**Option A: Drop and Recreate (Lose Progress)**
```sql
DROP TABLE producer_progress;
-- Restart application to recreate table
```

**Option B: Migrate Data (Preserve Progress)**
```sql
-- Backup
CREATE TABLE producer_progress_backup AS SELECT * FROM producer_progress;

-- Drop old table
DROP TABLE producer_progress;

-- Recreate with new schema (restart application or run schema.sql)

-- Migrate data (assumes BACKFILL mode for existing progress)
INSERT INTO producer_progress (
    type_id, mode, last_processed_id, hendelse_id,
    backfill_start_time, backfill_completion_time, last_error, updated_at
)
SELECT
    type_id,
    'BACKFILL' as mode,
    last_processed_id,
    NULL as hendelse_id,
    NULL as backfill_start_time,
    NULL as backfill_completion_time,
    NULL as last_error,
    updated_at
FROM producer_progress_backup;
```

## Troubleshooting

### Backfill Stuck
**Symptoms**: `lastProcessedId` not advancing
**Solutions**:
1. Check logs for errors
2. Verify API connectivity: `curl https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegobjekter/915/stream?antall=1`
3. Check database for `last_error`: `SELECT * FROM producer_progress WHERE type_id=915;`
4. Reset if necessary: `POST /api/nvdb/backfill/915/reset`

### Updates Mode Not Polling
**Symptoms**: Mode is UPDATES but no new data
**Solutions**:
1. Check `hendelseId` is set: `SELECT * FROM producer_progress WHERE type_id=915;`
2. Verify hendelser endpoint: `curl https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/hendelser/vegobjekter/915?antall=1`
3. Check schedule is running: Look for "Processing type 915 in UPDATES mode" in logs

### High Error Rate
**Symptoms**: Many errors in logs or `last_error` field
**Solutions**:
1. Check network connectivity to NVDB API
2. Verify API rate limits are not exceeded
3. Check Kafka broker connectivity
4. Review error patterns in logs

### Database Locked
**Symptoms**: "Database is locked" errors
**Solutions**:
1. SQLite limitation - only one writer at a time
2. Consider PostgreSQL for production
3. Reduce schedule frequency if contention is high

## Best Practices

1. **Start Small**: Begin backfill with high schedule interval (e.g., 5 minutes) to verify system stability
2. **Monitor Closely**: Watch the first hour of backfill for any issues
3. **Scale Gradually**: Decrease schedule interval once stable
4. **Backup Database**: Regular backups of SQLite file before major changes
5. **Log Retention**: Keep at least 7 days of logs for debugging
6. **Alert on Errors**: Set up alerts for `last_error` != null
7. **Track Completion**: Monitor backfill completion time to estimate total duration

## Example Usage

### Check Current Status
```bash
curl http://localhost:8080/api/nvdb/status
```

### Start Backfill for Type 915
```bash
curl -X POST http://localhost:8080/api/nvdb/backfill/915/start
```

### Monitor Type 915 Progress
```bash
watch -n 5 'curl -s http://localhost:8080/api/nvdb/status/915 | jq .'
```

### Reset Type 916
```bash
curl -X POST http://localhost:8080/api/nvdb/backfill/916/reset
```

### Stop All Processing
```bash
curl -X POST http://localhost:8080/api/nvdb/backfill/915/stop
curl -X POST http://localhost:8080/api/nvdb/backfill/916/stop
```
