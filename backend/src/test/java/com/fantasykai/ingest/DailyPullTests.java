package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fantasykai.projections.ProjectionDatabase;
import com.fantasykai.projections.ProjectionJob;
import com.fantasykai.projections.ProjectionProperties;
import com.fantasykai.projections.ProjectionRun;
import com.fantasykai.projections.SeasonWeek;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * The daily job's order and its record: ingest, then projections -- even after a failed ingest
 * -- each step in {@code ingest_runs}, and nothing at all while the job is switched off.
 *
 * <p>The ingest and the projection job are mocks; the recorder is real, on a real
 * {@code ingest_runs}, because the row it leaves is what {@code ingestFreshness} reads.
 */
class DailyPullTests {

    private static final Clock OCTOBER =
            Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("America/New_York"));

    private final ProjectionDatabase db = ProjectionDatabase.get();
    private final IngestService ingest = mock(IngestService.class);
    private final ProjectionJob job = mock(ProjectionJob.class);

    @BeforeEach
    void clear() {
        db.reset();
    }

    @Test
    void ingestsThenProjectsAndRecordsTheProjectionRun() {
        when(job.run(2026)).thenReturn(new ProjectionRun(2026, Optional.of(new SeasonWeek(2026, 6)), 480, 455, 12));

        pull(true, OCTOBER).run();

        InOrder order = inOrder(ingest, job);
        order.verify(ingest).ingestCurrentSeason();
        order.verify(job).run(2026);
        assertThat(projectionRuns()).singleElement().satisfies(run -> assertThat(run)
                .containsEntry("status", "SUCCESS")
                .containsEntry("rows_read", 480)
                .containsEntry("rows_upserted", 455));
    }

    /** After week 18 the job has nothing to do. That is not a failure, and it still ran. */
    @Test
    void noWeekLeftToProjectIsRecordedAsSkipped() {
        when(job.run(2026)).thenReturn(new ProjectionRun(2026, Optional.empty(), 0, 0, 0));

        pull(true, OCTOBER).run();

        assertThat(projectionRuns()).singleElement().satisfies(run -> {
            assertThat(run).containsEntry("status", "SKIPPED");
            assertThat((String) run.get("error")).contains("no regular-season week left", "2026");
        });
    }

    /** Off until Slice 4 (owner decision 2026-10-12): not run, not recorded, the ingest unchanged. */
    @Test
    void switchedOffTheJobIsNeverRunAndLeavesNoRow() {
        pull(false, OCTOBER).run();

        verify(ingest).ingestCurrentSeason();
        verifyNoInteractions(job);
        assertThat(projectionRuns()).isEmpty();
    }

    /**
     * The projection reads only what is stored, so it runs after a failed ingest too -- and the
     * ingest's failure is still the one the process exits on.
     */
    @Test
    void projectsEvenWhenTheIngestFailedAndStillThrowsTheIngestFailure() {
        IngestException ingestFailure = new IngestException("snap_counts header changed");
        doThrow(ingestFailure).when(ingest).ingestCurrentSeason();
        when(job.run(2026)).thenReturn(new ProjectionRun(2026, Optional.of(new SeasonWeek(2026, 6)), 480, 455, 0));

        assertThatThrownBy(() -> pull(true, OCTOBER).run()).isSameAs(ingestFailure);

        verify(job).run(2026);
        assertThat(projectionRuns()).singleElement().satisfies(run -> assertThat(run)
                .containsEntry("status", "SUCCESS"));
    }

    @Test
    void whenBothFailTheIngestFailureCarriesTheProjectionFailure() {
        IngestException ingestFailure = new IngestException("stats file 500");
        IllegalArgumentException projectionFailure = new IllegalArgumentException("two games in 2026 week 4");
        doThrow(ingestFailure).when(ingest).ingestCurrentSeason();
        when(job.run(anyInt())).thenThrow(projectionFailure);

        assertThatThrownBy(() -> pull(true, OCTOBER).run())
                .isSameAs(ingestFailure)
                .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(projectionFailure));
        assertThat(projectionRuns()).singleElement().satisfies(run -> {
            assertThat(run).containsEntry("status", "FAILED");
            assertThat((String) run.get("error")).contains("two games in 2026 week 4");
        });
    }

    @Test
    void aProjectionFailureAloneIsThrownAndRecorded() {
        IllegalStateException projectionFailure = new IllegalStateException("database went away");
        when(job.run(anyInt())).thenThrow(projectionFailure);

        assertThatThrownBy(() -> pull(true, OCTOBER).run()).isSameAs(projectionFailure);
        assertThat(projectionRuns()).singleElement().satisfies(run -> assertThat(run)
                .containsEntry("status", "FAILED"));
    }

    /** A season is named for the year it starts in: February 2027 is still the 2026 season. */
    @Test
    void theSeasonProjectedIsTheCurrentOneByTheClock() {
        when(job.run(anyInt())).thenReturn(new ProjectionRun(2026, Optional.empty(), 0, 0, 0));

        pull(true, Clock.fixed(Instant.parse("2027-02-03T11:00:00Z"), ZoneId.of("America/New_York"))).run();

        verify(job).run(2026);
    }

    private DailyPull pull(boolean jobEnabled, Clock clock) {
        return new DailyPull(ingest, job, new ProjectionProperties("weighted-average-v1", jobEnabled),
                new IngestRunRecorder(db.jdbc),
                new IngestProperties("https://example.invalid", "https://example.invalid", 2020,
                        "0 0 6 * * *", false, false, false),
                clock);
    }

    private List<Map<String, Object>> projectionRuns() {
        return db.jdbc.queryForList("SELECT * FROM ingest_runs WHERE source = ? ORDER BY id",
                ProjectionJob.SOURCE);
    }
}
