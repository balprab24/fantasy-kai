package com.fantasykai.ingest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Answers "did the pipeline actually run", which nothing asked before.
 *
 * <p>{@code ingest_runs} has been written since Phase 1 and read by nothing.
 * That is the wrong half of an observability story: the table records a stopped
 * pipeline perfectly and then no one looks. The daily job runs under launchd on
 * a laptop that sleeps, so gaps are expected -- the point is that a gap is
 * visible rather than silent.
 *
 * <p>Out of season this reports UP with a note. The scheduled pull deliberately
 * no-ops from March to August ({@link IngestProperties#inSeason}), so a
 * staleness check that ignored the calendar would sit DOWN for six months and
 * train everyone to ignore it.
 *
 * <p><strong>Do not put this component behind a platform liveness probe.</strong>
 * A missed 06:00 ingest is degraded, not down -- the API still serves six
 * seasons correctly. {@code application.yml} defines a {@code liveness} health
 * group that excludes it for exactly this reason; when Phase 5 deploys, the
 * host's health check points at that group, not at {@code /actuator/health}.
 */
@Component
public class IngestFreshnessHealthIndicator implements HealthIndicator {

    /**
     * One missed 06:00 is tolerated; two is a stopped pipeline. A laptop that
     * slept through one morning should not read the same as a broken job.
     */
    private static final Duration STALE_AFTER = Duration.ofHours(36);

    private static final String LATEST_PER_SOURCE = """
            SELECT DISTINCT ON (source) source, status, started_at, error
              FROM ingest_runs
             ORDER BY source, started_at DESC
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public IngestFreshnessHealthIndicator(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Health health() {
        List<Run> runs = jdbc.query(LATEST_PER_SOURCE, (rs, n) -> new Run(
                rs.getString("source"),
                rs.getString("status"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getString("error")));

        Map<String, Object> details = new LinkedHashMap<>();
        boolean inSeason = IngestProperties.inSeason(LocalDate.now(clock));
        details.put("inSeason", inSeason);

        if (runs.isEmpty()) {
            details.put("reason", "ingest_runs is empty -- the pipeline has never run");
            return Health.down().withDetails(details).build();
        }

        Instant now = Instant.now(clock);
        Instant newest = runs.stream().map(Run::startedAt).max(Instant::compareTo).orElseThrow();
        Duration age = Duration.between(newest, now);

        details.put("lastRunAt", newest.toString());
        details.put("ageHours", age.toHours());
        details.put("staleAfterHours", STALE_AFTER.toHours());
        runs.forEach(run -> details.put(run.source(), run.describe()));

        List<String> failed = runs.stream()
                .filter(run -> "FAILED".equals(run.status()))
                .map(Run::source)
                .toList();

        // A source whose most recent run failed is loud regardless of the
        // calendar: it means the last attempt broke, not that none was due.
        if (!failed.isEmpty()) {
            details.put("reason", "most recent run failed for " + String.join(", ", failed));
            return Health.down().withDetails(details).build();
        }

        if (inSeason && age.compareTo(STALE_AFTER) > 0) {
            details.put("reason", "no ingest in %d hours, in season".formatted(age.toHours()));
            return Health.down().withDetails(details).build();
        }

        return Health.up().withDetails(details).build();
    }

    private record Run(String source, String status, Instant startedAt, String error) {

        String describe() {
            return error == null || error.isBlank()
                    ? "%s at %s".formatted(status, startedAt)
                    : "%s at %s (%s)".formatted(status, startedAt, error);
        }
    }
}
