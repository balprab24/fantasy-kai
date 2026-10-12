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
 *   <li>withdraw every open row whose game has left the schedule ({@code games.delisted_at});</li>
 *   <li>lock every row whose game has kicked off -- from then on it is the record of what was
 *       projected before the game, and normal application code never rewrites it;</li>
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

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TargetWeekResolver resolver;
    private final JdbcProjectionInputs inputs;
    private final ProjectionRepository repository;
    private final ProjectionProvider provider;
    private final AvailabilityService availability;
    private final Clock clock;

    public ProjectionJob(JdbcTemplate jdbc, TransactionTemplate transaction, TargetWeekResolver resolver,
            JdbcProjectionInputs inputs, ProjectionRepository repository, ProjectionProvider provider,
            AvailabilityService availability, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.resolver = resolver;
        this.inputs = inputs;
        this.repository = repository;
        this.provider = provider;
        this.availability = availability;
        this.clock = clock;
    }

    /** Projects the next week of {@code season}, as of the clock's now. */
    public ProjectionRun run(int season) {
        Instant now = clock.instant();
        ProjectionRun run = transaction.execute(status -> runLocked(season, now));
        log.info("projections {}: week {}, {} candidates, {} written, {} locked at kickoff, {} withdrawn"
                + " (game left the schedule)", provider.source().id(),
                run.week().map(SeasonWeek::toString).orElse("none"), run.candidates(), run.written(),
                run.locked(), run.withdrawn());
        return run;
    }

    private ProjectionRun runLocked(int season, Instant now) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> {}, ADVISORY_LOCK);
        // Withdraw before locking: a game that left the schedule before the job saw it kick off
        // is not a game anyone projected, and must not become part of the record.
        int withdrawn = repository.withdrawDelisted();
        int locked = repository.lockKickedOff(now);

        Optional<TargetWeek> target = resolver.resolve(season, now);
        if (target.isEmpty()) {
            return new ProjectionRun(season, Optional.empty(), 0, 0, locked, withdrawn);
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

        List<Candidate> candidates = candidates(week, openGameByTeam.keySet());
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
        return new ProjectionRun(season, Optional.of(week), candidates.size(), rows.size(), locked, withdrawn);
    }

    private record Candidate(long playerId, int teamId) {}

    /**
     * Who gets a projection: a skill player on the roster of a team that plays one of the open
     * games -- active, or on a reserve or exempt list ({@link AvailabilityRules#PROJECTED_ROSTER_STATUSES}).
     *
     * <p>Rostered, not available: an Out or injured-reserve player still gets his raw line, which
     * his page can show; whether the board ranks him is availability's question, asked later and
     * never stored here. Practice squad, released and retired players get none -- the interim rule
     * this replaces, {@code players.team_id}, kept a retired player's last team for good.
     *
     * <p>Each team's roster is its latest published week up to the target
     * ({@link AvailabilityService#roster}), so a week whose rosters are not out yet uses the
     * previous one rather than projecting nobody. A season with no roster at all yet projects no
     * one, and says so: there is nothing true to project from.
     */
    private List<Candidate> candidates(SeasonWeek week, Set<Integer> openTeams) {
        Map<Long, AvailabilityService.RosterEntry> roster = availability.roster(week);
        if (roster.isEmpty()) {
            log.warn("projections: no weekly roster for {} yet -- no candidates", week.season());
        }
        return roster.values().stream()
                .filter(entry -> openTeams.contains(entry.teamId()))
                .filter(entry -> AvailabilityRules.PROJECTED_ROSTER_STATUSES.contains(entry.status()))
                .filter(entry -> ScoringPosition.ALL.contains(entry.position()))
                .map(entry -> new Candidate(entry.playerId(), entry.teamId()))
                .sorted(java.util.Comparator.comparingLong(Candidate::playerId))
                .toList();
    }
}
