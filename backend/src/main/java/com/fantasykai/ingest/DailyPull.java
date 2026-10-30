package com.fantasykai.ingest;

import com.fantasykai.projections.ProjectionJob;
import com.fantasykai.projections.ProjectionProperties;
import com.fantasykai.projections.ProjectionRun;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The daily job: the current season's ingest, then next week's projections.
 *
 * <p>The one entrypoint both schedules share -- {@link IngestScheduler} in the deployed JVM and
 * {@link IngestOnceRunner} for a one-shot run -- so they cannot drift into doing different
 * things.
 *
 * <p>The projection job runs <em>even when the ingest failed</em>. It reads only what is
 * stored, and the ingest is deliberately non-transactional ({@link IngestService}): a failed
 * pull leaves every row it wrote and the rest as they were, so projecting from that is
 * projecting from what the board shows, and tomorrow's run repairs both. The ingest's failure
 * is still the one thrown -- a projection failure rides along as suppressed -- so the process
 * exits non-zero and {@code ingest_runs} carries both.
 *
 * <p>The projection run gets its own {@code ingest_runs} row, {@value ProjectionJob#SOURCE}, so
 * {@code ingestFreshness} reports a failed or stopped job with no change of its own.
 */
@Component
public class DailyPull {

    private static final Logger log = LoggerFactory.getLogger(DailyPull.class);

    private final IngestService ingest;
    private final ProjectionJob projections;
    private final ProjectionProperties projectionProperties;
    private final IngestRunRecorder recorder;
    private final IngestProperties ingestProperties;
    private final Clock clock;

    public DailyPull(IngestService ingest, ProjectionJob projections,
            ProjectionProperties projectionProperties, IngestRunRecorder recorder,
            IngestProperties ingestProperties, Clock clock) {
        this.ingest = ingest;
        this.projections = projections;
        this.projectionProperties = projectionProperties;
        this.recorder = recorder;
        this.ingestProperties = ingestProperties;
        this.clock = clock;
    }

    public void run() {
        RuntimeException failure = null;
        try {
            ingest.ingestCurrentSeason();
        } catch (RuntimeException e) {
            failure = e;
        }

        if (!projectionProperties.jobEnabled()) {
            log.info("projection job off (fantasykai.projections.job-enabled=false); not run");
        } else {
            try {
                project();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    /** One projection run, bookended in {@code ingest_runs} like every ingest source. */
    IngestResult project() {
        int season = ingestProperties.currentSeason(clock);
        long runId = recorder.start(ProjectionJob.SOURCE);
        try {
            ProjectionRun run = projections.run(season);
            if (run.week().isEmpty()) {
                String reason = "no regular-season week left to project in " + season;
                log.info("{}: {}", ProjectionJob.SOURCE, reason);
                recorder.skip(runId, reason);
                return new IngestResult(ProjectionJob.SOURCE, 0, 0, 0);
            }
            IngestResult result = IngestResult.of(ProjectionJob.SOURCE, run.candidates(), run.written());
            recorder.succeed(runId, result);
            return result;
        } catch (RuntimeException e) {
            log.error("projection run failed", e);
            recorder.fail(runId, e);
            throw e;
        }
    }
}
