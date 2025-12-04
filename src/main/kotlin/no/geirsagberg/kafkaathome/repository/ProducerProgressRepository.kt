package no.geirsagberg.kafkaathome.repository

import no.geirsagberg.kafkaathome.model.ProducerMode
import no.geirsagberg.kafkaathome.model.ProducerProgress
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ProducerProgressRepository(private val jdbcTemplate: JdbcTemplate) {

    fun findByTypeId(typeId: Int): ProducerProgress? {
        return jdbcTemplate.query(
            """SELECT type_id, mode, last_processed_id, hendelse_id,
               backfill_start_time, backfill_completion_time, last_error, updated_at
               FROM producer_progress WHERE type_id = ?""",
            { rs, _ ->
                ProducerProgress(
                    typeId = rs.getInt("type_id"),
                    mode = ProducerMode.valueOf(rs.getString("mode")),
                    lastProcessedId = rs.getLong("last_processed_id").takeIf { !rs.wasNull() },
                    hendelseId = rs.getLong("hendelse_id").takeIf { !rs.wasNull() },
                    backfillStartTime = rs.getString("backfill_start_time")?.let { Instant.parse(it) },
                    backfillCompletionTime = rs.getString("backfill_completion_time")?.let { Instant.parse(it) },
                    lastError = rs.getString("last_error"),
                    updatedAt = Instant.parse(rs.getString("updated_at"))
                )
            },
            typeId
        ).firstOrNull()
    }

    fun save(progress: ProducerProgress) {
        val exists = (jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM producer_progress WHERE type_id = ?",
            Int::class.java,
            progress.typeId
        ) ?: 0) > 0

        if (exists) {
            jdbcTemplate.update(
                """UPDATE producer_progress
                   SET mode = ?, last_processed_id = ?, hendelse_id = ?,
                       backfill_start_time = ?, backfill_completion_time = ?,
                       last_error = ?, updated_at = ?
                   WHERE type_id = ?""",
                progress.mode.name,
                progress.lastProcessedId,
                progress.hendelseId,
                progress.backfillStartTime?.toString(),
                progress.backfillCompletionTime?.toString(),
                progress.lastError,
                progress.updatedAt.toString(),
                progress.typeId
            )
        } else {
            jdbcTemplate.update(
                """INSERT INTO producer_progress
                   (type_id, mode, last_processed_id, hendelse_id,
                    backfill_start_time, backfill_completion_time, last_error, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                progress.typeId,
                progress.mode.name,
                progress.lastProcessedId,
                progress.hendelseId,
                progress.backfillStartTime?.toString(),
                progress.backfillCompletionTime?.toString(),
                progress.lastError,
                progress.updatedAt.toString()
            )
        }
    }

    fun deleteByTypeId(typeId: Int) {
        jdbcTemplate.update("DELETE FROM producer_progress WHERE type_id = ?", typeId)
    }

    fun findAll(): List<ProducerProgress> {
        return jdbcTemplate.query(
            """SELECT type_id, mode, last_processed_id, hendelse_id,
               backfill_start_time, backfill_completion_time, last_error, updated_at
               FROM producer_progress""",
            { rs, _ ->
                ProducerProgress(
                    typeId = rs.getInt("type_id"),
                    mode = ProducerMode.valueOf(rs.getString("mode")),
                    lastProcessedId = rs.getLong("last_processed_id").takeIf { !rs.wasNull() },
                    hendelseId = rs.getLong("hendelse_id").takeIf { !rs.wasNull() },
                    backfillStartTime = rs.getString("backfill_start_time")?.let { Instant.parse(it) },
                    backfillCompletionTime = rs.getString("backfill_completion_time")?.let { Instant.parse(it) },
                    lastError = rs.getString("last_error"),
                    updatedAt = Instant.parse(rs.getString("updated_at"))
                )
            }
        )
    }
}
