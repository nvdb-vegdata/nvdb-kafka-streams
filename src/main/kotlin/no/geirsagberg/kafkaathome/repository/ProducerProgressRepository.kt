package no.geirsagberg.kafkaathome.repository

import no.geirsagberg.kafkaathome.model.ProducerProgress
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ProducerProgressRepository(private val jdbcTemplate: JdbcTemplate) {

    fun findByTypeId(typeId: Int): ProducerProgress? {
        return jdbcTemplate.query(
            "SELECT type_id, last_processed_id, updated_at FROM producer_progress WHERE type_id = ?",
            { rs, _ ->
                ProducerProgress(
                    typeId = rs.getInt("type_id"),
                    lastProcessedId = rs.getLong("last_processed_id"),
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
                "UPDATE producer_progress SET last_processed_id = ?, updated_at = ? WHERE type_id = ?",
                progress.lastProcessedId,
                progress.updatedAt.toString(),
                progress.typeId
            )
        } else {
            jdbcTemplate.update(
                "INSERT INTO producer_progress (type_id, last_processed_id, updated_at) VALUES (?, ?, ?)",
                progress.typeId,
                progress.lastProcessedId,
                progress.updatedAt.toString()
            )
        }
    }

    fun deleteByTypeId(typeId: Int) {
        jdbcTemplate.update("DELETE FROM producer_progress WHERE type_id = ?", typeId)
    }
}
