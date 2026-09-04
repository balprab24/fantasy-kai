package com.fantasykai.ingest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes the ingest_runs row that bookends every pull.
 *
 * <p>Deliberately outside any surrounding transaction: a failed ingest must
 * still leave a FAILED row behind, which is the whole point of the table.
 */
@Component
public class IngestRunRecorder {

    private static final int ERROR_MAX = 4000;

    private final JdbcTemplate jdbc;

    public IngestRunRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long start(String source) {
        Long id = jdbc.queryForObject("""
                INSERT INTO ingest_runs (source, started_at, status)
                VALUES (?, now(), 'RUNNING') RETURNING id
                """, Long.class, source);
        if (id == null) {
            throw new IngestException("could not open an ingest_runs row for " + source);
        }
        return id;
    }

    public void succeed(long runId, IngestResult result) {
        jdbc.update("""
                UPDATE ingest_runs
                   SET finished_at = now(), rows_read = ?, rows_upserted = ?, status = 'SUCCESS'
                 WHERE id = ?
                """, result.rowsRead(), result.rowsUpserted(), runId);
    }

    /** The source had nothing to give yet. Not a success, not a failure. */
    public void skip(long runId, String reason) {
        jdbc.update("""
                UPDATE ingest_runs
                   SET finished_at = now(), rows_read = 0, rows_upserted = 0,
                       status = 'SKIPPED', error = ?
                 WHERE id = ?
                """, reason, runId);
    }

    public void fail(long runId, Throwable cause) {
        String message = cause.toString();
        jdbc.update("""
                UPDATE ingest_runs
                   SET finished_at = now(), status = 'FAILED', error = ?
                 WHERE id = ?
                """, message.length() > ERROR_MAX ? message.substring(0, ERROR_MAX) : message, runId);
    }
}
