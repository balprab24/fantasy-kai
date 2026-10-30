package com.fantasykai.ingest;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a pull-parse-upsert cycle and records every step in ingest_runs.
 *
 * <p>Each source gets its own run row, so "how many rows did the pipeline move"
 * is answerable per source rather than as one opaque total.
 *
 * <p>Deliberately not transactional. Upserts are idempotent, so a mid-run
 * failure leaves the rows already written in place and a FAILED row in
 * ingest_runs -- degraded rather than down, and a re-run repairs it.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final TeamIngestor teams;
    private final PlayerIngestor players;
    private final GameIngestor games;
    private final StatIngestor stats;
    private final SnapCountIngestor snaps;
    private final RosterWeekIngestor rosters;
    private final InjuryReportIngestor injuries;
    private final SleeperCrosswalk sleeper;
    private final IntegrityChecks integrity;
    private final IngestRunRecorder recorder;
    private final IngestProperties props;
    private final Clock clock;

    public IngestService(TeamIngestor teams, PlayerIngestor players, GameIngestor games,
            StatIngestor stats, SnapCountIngestor snaps, RosterWeekIngestor rosters,
            InjuryReportIngestor injuries, SleeperCrosswalk sleeper,
            IntegrityChecks integrity, IngestRunRecorder recorder,
            IngestProperties props, Clock clock) {
        this.teams = teams;
        this.players = players;
        this.games = games;
        this.stats = stats;
        this.snaps = snaps;
        this.rosters = rosters;
        this.injuries = injuries;
        this.sleeper = sleeper;
        this.integrity = integrity;
        this.recorder = recorder;
        this.props = props;
        this.clock = clock;
    }

    /** Every season from the configured first season through the current one. */
    public List<IngestResult> backfill() {
        List<Integer> seasons = IntStream
                .rangeClosed(props.firstSeason(), props.currentSeason(clock))
                .boxed()
                .toList();
        log.info("backfilling seasons {}", seasons);
        return ingest(seasons);
    }

    /** The in-season daily pull: reference data plus the current season only. */
    public List<IngestResult> ingestCurrentSeason() {
        return ingest(List.of(props.currentSeason(clock)));
    }

    private List<IngestResult> ingest(List<Integer> seasons) {
        List<IngestResult> results = new ArrayList<>();
        results.add(record(TeamIngestor.SOURCE, teams::ingest));
        results.add(record(PlayerIngestor.SOURCE, players::ingest));
        results.add(record(GameIngestor.SOURCE, () -> games.ingest(seasons)));
        for (int season : seasons) {
            results.add(record(StatIngestor.SOURCE, () -> stats.ingest(season)));
            results.add(record(SnapCountIngestor.SOURCE, () -> snaps.ingest(season)));
        }
        // Availability is current information: who is on which roster, and this week's injury
        // report. Only the season being played is read -- by the daily pull and by a backfill
        // alike -- because nothing in production needs another (architecture section 6). After
        // the player master, which the rows are tied to, and before DailyPull projects.
        int current = props.currentSeason(clock);
        if (seasons.contains(current)) {
            results.add(record(RosterWeekIngestor.SOURCE, () -> rosters.ingest(current)));
            results.add(record(InjuryReportIngestor.SOURCE, () -> injuries.ingest(current)));
        }
        results.add(record(SleeperCrosswalk.SOURCE, sleeper::ingest));

        results.forEach(r -> log.info("ingested {}: read={} upserted={} skipped={}",
                r.source(), r.rowsRead(), r.rowsUpserted(), r.rowsSkipped()));
        integrity.verifyAfterIngest();
        return results;
    }

    private IngestResult record(String source, Supplier<IngestResult> work) {
        long runId = recorder.start(source);
        try {
            IngestResult result = work.get();
            recorder.succeed(runId, result);
            return result;
        } catch (AssetNotPublishedException e) {
            // The season has not started, or this week is not out yet. Carry on.
            log.info("{} has nothing to ingest yet: {}", source, e.getMessage());
            recorder.skip(runId, e.getMessage());
            return new IngestResult(source, 0, 0, 0);
        } catch (RuntimeException e) {
            log.error("ingest failed for {}", source, e);
            recorder.fail(runId, e);
            throw e;
        }
    }
}
