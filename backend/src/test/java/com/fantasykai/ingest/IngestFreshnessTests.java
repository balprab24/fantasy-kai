package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code ingest_runs} has been written since Phase 1 and read by nothing, so a
 * stopped pipeline was invisible -- which is exactly what happened: the launchd
 * job was never loaded and nobody noticed for four days.
 *
 * <p>Each of these drives the clock rather than the data, because the thing
 * under test is an age comparison and the calendar is half of it.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "fantasykai.ingest.scheduled-enabled=false")
class IngestFreshnessTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** A Tuesday in November: in season. */
    private static final Instant IN_SEASON = Instant.parse("2025-11-04T12:00:00Z");
    /** A Tuesday in May: the scheduled pull deliberately no-ops. */
    private static final Instant OFF_SEASON = Instant.parse("2025-05-06T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearRuns() {
        jdbc.update("TRUNCATE ingest_runs");
    }

    @Test
    @Transactional
    void reportsUpWhenTheDailyPullRanThisMorning() {
        record("nflverse.stats_player_week", "SUCCESS", IN_SEASON.minusSeconds(6 * 3600));

        Health health = indicatorAt(IN_SEASON).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("ageHours", 6L).containsEntry("inSeason", true);
    }

    @Test
    @Transactional
    void toleratesOneMissedMorningButNotTwo() {
        record("nflverse.stats_player_week", "SUCCESS", IN_SEASON.minusSeconds(30 * 3600));
        assertThat(indicatorAt(IN_SEASON).health().getStatus())
                .as("30 hours is one missed 06:00 on a laptop that slept")
                .isEqualTo(Status.UP);

        jdbc.update("TRUNCATE ingest_runs");
        record("nflverse.stats_player_week", "SUCCESS", IN_SEASON.minusSeconds(40 * 3600));
        assertThat(indicatorAt(IN_SEASON).health().getStatus())
                .as("40 hours is a stopped pipeline")
                .isEqualTo(Status.DOWN);
    }

    /**
     * The whole reason for the calendar gate. Without it this component sits
     * DOWN from March to August and everyone learns to ignore it.
     */
    @Test
    @Transactional
    void staleIsNotAFailureOutOfSeason() {
        record("nflverse.stats_player_week", "SUCCESS", OFF_SEASON.minusSeconds(90 * 24 * 3600));

        Health health = indicatorAt(OFF_SEASON).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("inSeason", false);
    }

    /** A SKIPPED source still proves the job fired; that is what freshness asks. */
    @Test
    @Transactional
    void aSkippedSourceStillCountsAsHavingRun() {
        record("nflverse.stats_player_week", "SKIPPED", IN_SEASON.minusSeconds(3600));

        assertThat(indicatorAt(IN_SEASON).health().getStatus()).isEqualTo(Status.UP);
    }

    /** A failure is loud regardless of the calendar -- the last attempt broke. */
    @Test
    @Transactional
    void aFailedSourceIsDownEvenOutOfSeasonAndEvenWhenRecent() {
        record("nflverse.stats_player_week", "FAILED", OFF_SEASON.minusSeconds(600));

        Health health = indicatorAt(OFF_SEASON).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat((String) health.getDetails().get("reason")).contains("nflverse.stats_player_week");
    }

    @Test
    @Transactional
    void saysSoWhenThePipelineHasNeverRunAtAll() {
        Health health = indicatorAt(IN_SEASON).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat((String) health.getDetails().get("reason")).contains("never run");
    }

    /** Only the newest run per source is considered, not the whole history. */
    @Test
    @Transactional
    void readsTheNewestRunPerSourceRatherThanTheOldest() {
        record("nflverse.stats_player_week", "FAILED", IN_SEASON.minusSeconds(48 * 3600));
        record("nflverse.stats_player_week", "SUCCESS", IN_SEASON.minusSeconds(3600));

        assertThat(indicatorAt(IN_SEASON).health().getStatus())
                .as("yesterday's failure was corrected by this morning's success")
                .isEqualTo(Status.UP);
    }

    private void record(String source, String status, Instant startedAt) {
        jdbc.update("INSERT INTO ingest_runs (source, started_at, status) VALUES (?, ?, ?)",
                source, java.sql.Timestamp.from(startedAt), status);
    }

    private IngestFreshnessHealthIndicator indicatorAt(Instant now) {
        return new IngestFreshnessHealthIndicator(
                jdbc, Clock.fixed(now, ZoneId.of("America/New_York")));
    }
}
