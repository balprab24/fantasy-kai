package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantasykai.projections.ProjectionDatabase;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** Explicitly run with -Dtest=CrosswalkCoverageMeasurement and snapshot paths. No live DB writes. */
class CrosswalkCoverageMeasurement {
    @Test void measureAgainstExportedPopulation() throws Exception {
        var json = new ObjectMapper();
        var players = json.readTree(Path.of(System.getProperty("crosswalk.players")).toFile());
        var snapshot = json.readTree(Path.of(System.getProperty("crosswalk.sleeper")).toFile());
        var db = ProjectionDatabase.get();
        db.reset();
        List<Object[]> rows = new ArrayList<>();
        List<Long> current = new ArrayList<>();
        players.forEach(p -> {
            rows.add(new Object[] {p.path("id").asLong(), p.path("gsis_id").asText(null),
                    p.path("full_name").asText(), p.path("position").asText(),
                    p.path("birth_date").asText(null), p.path("external_ids").toString()});
            if (p.path("current").asBoolean()) current.add(p.path("id").asLong());
        });
        db.jdbc.batchUpdate("""
                INSERT INTO players (id, gsis_id, full_name, position, birth_date, external_ids)
                OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, CAST(? AS date), CAST(? AS jsonb))
                """, rows);
        var crosswalk = new SleeperCrosswalk(json, db.jdbc, null, mock(PlatformTransactionManager.class));
        long start = System.nanoTime();
        crosswalk.attach(snapshot);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        int matched = 0;
        for (Long id : current) {
            String sleeper = db.jdbc.queryForObject("SELECT external_ids->>'sleeper' FROM players WHERE id = ?", String.class, id);
            if (sleeper != null) matched++;
            else System.out.println("UNMATCHED " + db.jdbc.queryForObject("SELECT full_name FROM players WHERE id = ?", String.class, id));
        }
        System.out.printf("COVERAGE %d/%d = %.2f%%; attach %d ms%n", matched, current.size(), 100.0 * matched / current.size(), elapsed);
        assertThat((double) matched / current.size()).isGreaterThanOrEqualTo(0.98);
    }
}
