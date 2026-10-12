package com.fantasykai.projections;

import com.fantasykai.query.ScoringPosition;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Projects next week: the one place a {@link ProjectionProvider} is run.
 *
 * <p>Once a day, after the ingest ({@code ingest.DailyPull}), in one transaction:
 *
 * <ol>
 *   <li>lock every row whose game has kicked off -- from then on it is the record of what was
 *       projected before the game, and nothing rewrites it;</li>
 *   <li>find the target week ({@link TargetWeekResolver});</li>
 *   <li>project every candidate for the week's games that have not kicked off, from his stored
 *       history ({@link JdbcProjectionInputs});</li>
 *   <li>replace the week's unlocked rows with the new set.</li>
 * </ol>
 *
 * <p>Deterministic and idempotent: the same data at the same instant writes the same rows. It
 * regenerates every day because stats get corrected and rosters change. A failure anywhere
 * rolls the whole run back and leaves the previous rows in place.
 *
 * <p>Raw stats only. Nothing here scores, ranks or rounds.
 */
@Component
public class ProjectionJob {

    /** Its {@code ingest_runs.source}. */
    public static final String SOURCE = "fantasykai.projections";

    /**
     * A transaction-scoped advisory lock, so two runs against one database -- the scheduler and
     * a hand-run {@code ingest-once} -- take turns instead of interleaving their deletes and
     * inserts. Any constant works; it has to be the same everywhere the job runs.
     */
    static final long ADVISORY_LOCK = 0x50524f4a4b4149L; // "PROJKAI"

    private static final Logger log = LoggerFactory.getLogger(ProjectionJob.class);

    private static final String POSITIONS =
            ScoringPosition.ALL.stream().map(position -> "?").collect(Collectors.joining(", "));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TargetWeekResolver resolver;
    private final JdbcProjectionInputs inputs;
    private final ProjectionRepository repository;
    private final ProjectionProvider provider;
    private final Clock clock;

    public ProjectionJob(JdbcTemplate jdbc, TransactionTemplate transaction, TargetWeekResolver resolver,
            JdbcProjectionInputs inputs, ProjectionRepository repository, ProjectionProvider provider,
            Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.resolver = resolver;
        this.inputs = inputs;
        this.repository = repository;
        this.provider = provider;
        this.clock = clock;
    }

    /** Projects the next week of {@code season}, as of the clock's now. */
    public ProjectionRun run(int season) {
        Instant now = clock.instant();
        ProjectionRun run = transaction.execute(status -> runLocked(season, now));
        log.info("projections {}: week {}, {} candidates, {} written, {} locked at kickoff",
                provider.source().id(), run.week().map(SeasonWeek::toString).orElse("none"),
                run.candidates(), run.written(), run.locked());
        return run;
    }

    private ProjectionRun runLocked(int season, Instant now) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> {}, ADVISORY_LOCK);
        int locked = repository.lockKickedOff(now);

        Optional<TargetWeek> target = resolver.resolve(season, now);
        if (target.isEmpty()) {
            return new ProjectionRun(season, Optional.empty(), 0, 0, locked);
        }
        SeasonWeek week = target.get().week();
        String source = provider.source().id();

        // The open games, reached through either side. A game that has kicked off is not here:
        // its rows are locked, and a team on a bye has no game at all.
        Map<Integer, TargetWeek.Game> openGameByTeam = new HashMap<>();
        for (TargetWeek.Game game : target.get().games()) {
            if (!game.kickedOffBy(now)) {
                openGameByTeam.put(game.homeTeamId(), game);
                openGameByTeam.put(game.awayTeamId(), game);
            }
        }

        List<Candidate> candidates = candidates(openGameByTeam.keySet());
        Set<Long> alreadyLocked = repository.lockedPlayers(week, source);
        Map<Long, PlayerHistory> histories = inputs.histories(week);

        List<ProjectionRepository.ProjectedRow> rows = new ArrayList<>();
        for (Candidate candidate : candidates) {
            // A player traded after his old team already played this week keeps that row.
            if (alreadyLocked.contains(candidate.playerId())) {
                continue;
            }
            PlayerHistory history = histories.get(candidate.playerId());
            if (history == null) {
                continue; // no NFL history, no projection -- nothing is fabricated
            }
            TargetWeek.Game game = openGameByTeam.get(candidate.teamId());
            provider.project(history).ifPresent(projection -> rows.add(new ProjectionRepository.ProjectedRow(
                    candidate.playerId(), game.id(), candidate.teamId(), projection)));
        }

        repository.replaceOpen(week, source, rows, now);
        return new ProjectionRun(season, Optional.of(week), candidates.size(), rows.size(), locked);
    }

    private record Candidate(long playerId, int teamId) {}

    /**
     * Who might play: a skill player whose team plays one of the open games.
     *
     * <p><strong>Interim, until Slice 4.</strong> The team is {@code players.team_id} --
     * nflverse's {@code latest_team} -- which a retired or released player keeps, so this set is
     * too wide. Slice 4 replaces it with the weekly roster: who is on which team that week, and
     * whether he is active. Until then the daily pull does not run this job at all
     * ({@code fantasykai.projections.job-enabled=false}, owner decision 2026-10-12).
     */
    private List<Candidate> candidates(Set<Integer> teams) {
        if (teams.isEmpty()) {
            return List.of(); // "IN ()" is a syntax error, and there is nothing to ask
        }
        String sql = "SELECT id, team_id FROM players WHERE position IN (" + POSITIONS + ")"
                + " AND team_id IN (" + teams.stream().map(team -> "?").collect(Collectors.joining(", "))
                + ") ORDER BY id";
        Object[] args = Stream.concat(ScoringPosition.ALL.stream(), teams.stream().sorted()).toArray();
        return jdbc.query(sql, (rs, n) -> new Candidate(rs.getLong("id"), rs.getInt("team_id")), args);
    }
}
