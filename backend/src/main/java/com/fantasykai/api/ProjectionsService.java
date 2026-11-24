package com.fantasykai.api;

import com.fantasykai.ingest.IngestProperties;
import com.fantasykai.ingest.InjuryReportIngestor;
import com.fantasykai.ingest.RosterWeekIngestor;
import com.fantasykai.projections.Availability;
import com.fantasykai.projections.AvailabilityRules;
import com.fantasykai.projections.AvailabilityService;
import com.fantasykai.projections.AvailabilityService.RosterEntry;
import com.fantasykai.projections.CorruptHistoryException;
import com.fantasykai.projections.CurrentTargetWeek;
import com.fantasykai.projections.JdbcProjectionInputs;
import com.fantasykai.projections.ProjectionProperties;
import com.fantasykai.projections.ProjectionProvider;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.projections.TargetWeek;
import com.fantasykai.projections.TargetWeekResolver;
import com.fantasykai.query.ScoringPosition;
import com.fantasykai.query.ScoringProfileQueryRepository;
import com.fantasykai.query.ScoringProfileQueryRepository.ProfileRow;
import com.fantasykai.scoring.ExplainedScore;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.ScoringProfiles;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only projections: the stored raw lines of the week being played (or, failing that, the
 * newest earlier week of the same season), scored on request under the caller's ruleset by the
 * unmodified {@link ScoringEngine}.
 * Nothing here writes, and generation never runs inside a request -- the job does that, and is
 * off.
 *
 * <p>Availability is read at request time and decides only whether the board ranks a line
 * ({@code Availability.ranked()}); the line itself is kept whatever the status. "Availability
 * decides the board, never the raw line".
 *
 * <p><strong>Which week, and what state.</strong> The week is the resolver's
 * ({@link CurrentTargetWeek}, decision D1(a) of {@code docs/projections-slice-5-plan.md}): the
 * rule the job projects by, asked per request through a short cache. Against it:
 *
 * <ul>
 *   <li>no week left in the season: {@code NO_UPCOMING_WEEK}, and no rows;</li>
 *   <li>rows stored for that week: {@code READY}, or {@code STALE} if every one of their games
 *       kicked off at least {@link TargetWeekResolver#UNREPORTED_AFTER} ago;</li>
 *   <li>rows stored only for an earlier week of the same season: those rows, as {@code STALE} --
 *       the job has not written the new week yet, or has stopped;</li>
 *   <li>nothing stored for this season, or nothing readable (every stored row is an open row of
 *       a delisted game; a delisted game's locked rows stay readable): {@code UNAVAILABLE}. The
 *       lookup does not consult readability, so a target week whose rows are all hidden is
 *       {@code UNAVAILABLE} even if an earlier week has readable rows.</li>
 * </ul>
 *
 * <p><strong>Who the board left out.</strong> {@link ProjectionBoard.Excluded} counts three
 * kinds, so nobody disappears unexplained. {@code unavailable} is read off the served rows
 * (a stored line the board does not rank). {@code noHistory} and {@code bye} are the players the
 * job's own candidate rule would have projected -- a roster status in
 * {@link AvailabilityRules#PROJECTED_ROSTER_STATUSES}, a skill position -- who have no row, split
 * by the same two questions the player route asks: does his team play in the target week, and has
 * he any history before it, as the job's own reader ({@link JdbcProjectionInputs}) decides. They
 * need the week's roster, so they are known only for {@code READY}; in any other state, or when a
 * stored history is corrupt, they are {@code null}, never zero.
 *
 * <p>Each line is explained in full ({@link ExplainedScore}): its stats as value x rate, and each
 * bonus that bears on it with whether the line reached it.
 *
 * <p>A player's page also carries his line under the three scoring formats ({@link #presets}),
 * and the source it was made by with the baseline's measured error. That completes the contract
 * of {@code docs/projections-v1-architecture.md} section 9 that Slice 5 owes ("as built" there);
 * what remains is the UI (Slice 6) and the live-accuracy report (Slice 7).
 */
@Service
public class ProjectionsService {

    private static final Logger log = LoggerFactory.getLogger(ProjectionsService.class);

    /** The same corrupt-history fault is logged at most this often. */
    private static final Duration FAULT_LOG_EVERY = Duration.ofMinutes(10);

    /** A target week whose every stored game kicked off this long ago is reported {@code STALE}. */
    private static final Duration STALE_AFTER = TargetWeekResolver.UNREPORTED_AFTER;

    /** Under this many games of history a projection is labelled {@code LIMITED}. */
    private static final int ESTABLISHED_GAMES = 4;

    /**
     * The board's order: the unrounded total, highest first, then player id. A total order, so a
     * page boundary never splits a tie differently from one request to the next. The SQL has no
     * ORDER BY, so the order rows arrive in is not promised and nothing here depends on it.
     */
    static final Comparator<ProjectionRow> BOARD_ORDER =
            Comparator.comparingDouble(ProjectionRow::rawPoints).reversed()
                    .thenComparingLong(ProjectionRow::playerId);

    /**
     * The newest stored week of the target's own season at or before the target: the target
     * itself, else an earlier week of that season. Never another season's: last January's week-18
     * lines are not a stale version of this September's week 1.
     */
    private static final String STORED_WEEK = """
            SELECT season, week FROM player_week_projection
             WHERE provider = ? AND season = ? AND week <= ?
             ORDER BY week DESC
             LIMIT 1
            """;

    /**
     * A delisted game's open rows are withdrawn by the job; a locked one is
     * the record of what was projected, so it stays readable.
     */
    private static final String WEEK_LINES = """
            SELECT p.*, who.full_name, who.position, who.external_ids ->> 'espn' AS espn_id,
                   own.abbr AS team, opp.abbr AS opponent, g.kickoff_at,
                   g.home_team_id = p.team_id AS home
              FROM player_week_projection p
              JOIN players who ON who.id = p.player_id
              JOIN games g ON g.id = p.game_id
              JOIN teams own ON own.id = p.team_id
              JOIN teams opp ON opp.id = CASE WHEN g.home_team_id = p.team_id
                                              THEN g.away_team_id ELSE g.home_team_id END
             WHERE p.provider = ? AND p.season = ? AND p.week = ?
               AND (g.delisted_at IS NULL OR p.locked_at IS NOT NULL)
            """;

    private final JdbcTemplate jdbc;
    private final ScoringProfiles profiles;
    private final AvailabilityService availability;
    private final ProjectionProperties config;
    private final ProjectionProvider provider;
    private final Clock clock;
    private final CurrentTargetWeek targets;
    private final JdbcProjectionInputs inputs;
    private final ScoringProfileQueryRepository profileQueries;
    private final AtomicReference<LoggedFault> lastFault = new AtomicReference<>();

    public ProjectionsService(JdbcTemplate jdbc, ScoringProfiles profiles,
            AvailabilityService availability, ProjectionProperties config,
            ProjectionProvider provider, Clock clock, CurrentTargetWeek targets,
            JdbcProjectionInputs inputs, ScoringProfileQueryRepository profileQueries) {
        this.jdbc = jdbc;
        this.profiles = profiles;
        this.availability = availability;
        this.config = config;
        this.provider = provider;
        this.clock = clock;
        this.targets = targets;
        this.inputs = inputs;
        this.profileQueries = profileQueries;
    }

    /**
     * What a request sees.
     *
     * @param target the resolver's week with its games; null with {@code NO_UPCOMING_WEEK}
     * @param week   the week the rows are for: the target's, or an earlier week of its season
     *               when {@code STALE}; null when nothing is stored
     * @param rows   every readable stored line of that week, scored, whoever may see it
     * @param roster that week's roster, read once and shared by the availability classification
     *               and the answers about players with no row; empty when no week is stored
     */
    private record View(String state, TargetWeek target, SeasonWeek week, List<ProjectionRow> rows,
            Map<Long, RosterEntry> roster) {

        static View empty(String state, TargetWeek target) {
            return new View(state, target, null, List.of(), Map.of());
        }

        /** The newest game any row was built from; null with no rows. */
        SeasonWeek dataThrough() {
            return rows.stream().map(ProjectionRow::dataThrough).max(Comparator.naturalOrder()).orElse(null);
        }

        /** When the newest row was written; null with no rows. */
        Instant generatedAt() {
            return rows.stream().map(ProjectionRow::generatedAt).max(Comparator.naturalOrder()).orElse(null);
        }
    }

    public ProjectionBoard board(long profileId, String position, String q, int page, int size,
            Long userId) {
        List<String> positions = ScoringPosition.resolve(position);
        String needle = normalize(q);
        View view = read(profileId, userId);
        // The week's lines for this position, before the member's search: what "excluded" counts.
        List<ProjectionRow> inPosition = view.rows().stream()
                .filter(row -> positions.contains(row.position()))
                .toList();
        List<ProjectionRow> ranked = inPosition.stream()
                .filter(row -> row.availability().ranked())
                .filter(row -> normalize(row.name()).contains(needle))
                .sorted(BOARD_ORDER)
                .toList();
        int start = (int) Math.min((long) page * size, ranked.size());
        int end = Math.min(start + size, ranked.size());
        SeasonWeek goal = view.target() == null ? null : view.target().week();
        return new ProjectionBoard(view.state(),
                goal == null ? null : goal.season(), goal == null ? null : goal.week(),
                provider.source(), view.dataThrough(), view.generatedAt(), availabilityAsOf(),
                excluded(view, inPosition, positions),
                PageResponse.of(ranked.subList(start, end), page, size, ranked.size()));
    }

    /**
     * Who the board left out. The unranked are counted from the served lines; the two kinds
     * with no line at all are the job's candidates (a roster status it projects, a skill
     * position) who have none, split exactly as {@link #missingReason} splits one player.
     */
    private ProjectionBoard.Excluded excluded(View view, List<ProjectionRow> inPosition,
            List<String> positions) {
        int unavailable = (int) inPosition.stream().filter(row -> !row.availability().ranked()).count();
        if (!"READY".equals(view.state())) {
            return new ProjectionBoard.Excluded(unavailable, null, null);
        }
        Set<Long> projected = view.rows().stream().map(ProjectionRow::playerId).collect(Collectors.toSet());
        Set<Integer> playing = teamsPlaying(view.target());
        List<RosterEntry> unprojected = view.roster().values().stream()
                .filter(ProjectionsService::projectable)
                .filter(entry -> positions.contains(entry.position()))
                .filter(entry -> !projected.contains(entry.playerId()))
                .toList();
        int bye = (int) unprojected.stream().filter(entry -> !playing.contains(entry.teamId())).count();
        Integer noHistory = withoutHistory(view.target().week(), unprojected.stream()
                .filter(entry -> playing.contains(entry.teamId())).map(RosterEntry::playerId).toList())
                .map(Set::size).orElse(null);
        return new ProjectionBoard.Excluded(unavailable, noHistory, bye);
    }

    /**
     * The older of the last successful roster pull and the last successful injury pull, or null if
     * either has never succeeded. A skipped or failed run read nothing, so it is not a pull.
     */
    private Instant availabilityAsOf() {
        Map<String, Instant> lastSuccess = new HashMap<>();
        jdbc.query("""
                SELECT source, max(finished_at) AS finished FROM ingest_runs
                 WHERE status = 'SUCCESS' AND source IN (?, ?)
                 GROUP BY source
                """, rs -> {
                    Timestamp finished = rs.getTimestamp("finished");
                    if (finished != null) {
                        lastSuccess.put(rs.getString("source"), finished.toInstant());
                    }
                }, RosterWeekIngestor.SOURCE, InjuryReportIngestor.SOURCE);
        Instant roster = lastSuccess.get(RosterWeekIngestor.SOURCE);
        Instant injuries = lastSuccess.get(InjuryReportIngestor.SOURCE);
        if (roster == null || injuries == null) {
            return null;
        }
        return roster.isBefore(injuries) ? roster : injuries;
    }

    public PlayerProjectionResponse player(long id, long profileId, Long userId) {
        View view = read(profileId, userId);
        ProjectionRow row = view.rows().stream().filter(r -> r.playerId() == id).findFirst().orElse(null);
        if (row != null) {
            return new PlayerProjectionResponse("STALE".equals(view.state()) ? "STALE" : "PROJECTED",
                    provider.source(), row, presets(row, userId));
        }
        if (jdbc.queryForObject("SELECT count(*) FROM players WHERE id = ?", Long.class, id) == 0) {
            throw new PlayerNotFoundException(id);
        }
        // A reason is given only for the week being played, and only with the
        // roster evidence to back it; anything less is plain UNAVAILABLE.
        String missing = switch (view.state()) {
            case "NO_UPCOMING_WEEK" -> "NO_UPCOMING_WEEK";
            case "READY" -> missingReason(id, view);
            default -> "UNAVAILABLE";
        };
        return new PlayerProjectionResponse(missing, provider.source(), null, null);
    }

    /**
     * The scoring formats the preset strip shows, by the names {@code V3} seeds: "0 PPR · Half ·
     * PPR" (decision D2, approved as three). TE Premium is a fourth system preset and is not one of
     * them on purpose: it scores every position as Full PPR does except a tight end, so for a
     * receiver it would repeat the PPR figure, and a member who scores under it already has that
     * number as {@code projection.points}.
     */
    private static final List<String> STRIP_PRESETS = List.of("Standard", "Half PPR", "Full PPR");

    /**
     * The row's stored line scored under each of the {@link #STRIP_PRESETS}, in id order: "same
     * line, different value" without a second scoring path in the browser. Each is the unmodified
     * engine on the line the member was shown, rounded once, so it is the number the board would
     * print for that preset (a test compares them). A system preset only -- a member's own ruleset
     * named "Half PPR" is not one -- and another member's rulesets are not reachable here; the
     * member's own is {@code row.points()}.
     */
    private List<PlayerProjectionResponse.PresetPoints> presets(ProjectionRow row, Long userId) {
        StatLine line = new StatLine(row.position(), row.stats());
        return profileQueries.findVisibleTo(userId).stream()
                .filter(ProfileRow::preset)
                .filter(profile -> STRIP_PRESETS.contains(profile.name()))
                .sorted(Comparator.comparingLong(ProfileRow::id))
                .map(preset -> new PlayerProjectionResponse.PresetPoints(preset.id(), preset.name(),
                        ScoringEngine.roundForDisplay(
                                ScoringEngine.score(line, profiles.byId(preset.id(), userId)))))
                .toList();
    }

    /**
     * Why a rostered or unrostered player has no line this week. It asks the questions
     * {@link #excluded} asks of everyone, in the same order, from the same roster and the same
     * games: is he a player the job projects at all ({@link #projectable}), does his team play in
     * the target week, has he any history before it. So for every candidate, the board's count and
     * the player's own page are one rule, and a test compares them over everyone rostered.
     * A player the job never projects -- practice squad, released, retired, or not at a skill
     * position -- is not "bye" or "no history": he is not a candidate, and says so.
     */
    private String missingReason(long id, View view) {
        RosterEntry entry = view.roster().get(id);
        if (entry == null) {
            return view.roster().isEmpty() ? "UNAVAILABLE" : "NOT_ROSTERED";
        }
        if (!AvailabilityRules.PROJECTED_ROSTER_STATUSES.contains(entry.status())) {
            return "NOT_ROSTERED"; // on a practice squad, released or retired: not on the team's roster for the week
        }
        if (!ScoringPosition.ALL.contains(entry.position())) {
            return "UNAVAILABLE"; // v1 projects QB, RB, WR and TE
        }
        if (!teamsPlaying(view.target()).contains(entry.teamId())) {
            return "BYE";
        }
        Optional<Set<Long>> none = withoutHistory(view.target().week(), List.of(id));
        return none.isPresent() && !none.get().isEmpty() ? "NO_HISTORY" : "UNAVAILABLE";
    }

    /**
     * The job's candidate rule, as far as a roster row decides it: a roster status it projects
     * ({@link AvailabilityRules#PROJECTED_ROSTER_STATUSES}) and a skill position. {@link #excluded}
     * counts by it and {@link #missingReason} answers by it.
     */
    private static boolean projectable(RosterEntry entry) {
        return AvailabilityRules.PROJECTED_ROSTER_STATUSES.contains(entry.status())
                && ScoringPosition.ALL.contains(entry.position());
    }

    /** Teams with a regular-season game in the target week that the schedule still lists. */
    private static Set<Integer> teamsPlaying(TargetWeek target) {
        Set<Integer> teams = new HashSet<>();
        for (TargetWeek.Game game : target.games()) {
            teams.add(game.homeTeamId());
            teams.add(game.awayTeamId());
        }
        return teams;
    }

    /**
     * Of these players, those the job would leave off for having no history: none of the
     * regular-season games before {@code target}. Decided by the job's own reader
     * ({@link JdbcProjectionInputs#histories(SeasonWeek, Collection)}), not by a second statement
     * of the same rule, so a game on the target week itself (a rookie whose debut has been
     * ingested while the week is still being played), a playoff game and a player who is not at a
     * skill position are settled exactly as the job settles them.
     *
     * <p>Empty when the reader refuses a corrupt history (a player with two games in one week). The
     * job fails its whole run on that, by owner decision; a request must not, so who has no
     * history is then not known: the fault is logged by name and the caller says null.
     */
    private Optional<Set<Long>> withoutHistory(SeasonWeek target, Collection<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return Optional.of(Set.of());
        }
        try {
            Set<Long> withHistory = inputs.histories(target, playerIds).keySet();
            return Optional.of(playerIds.stream().filter(id -> !withHistory.contains(id))
                    .collect(Collectors.toSet()));
        } catch (CorruptHistoryException fault) {
            logCorruptHistory(target, fault);
            return Optional.empty();
        }
    }

    /** One fault, and when it was last logged: the same fault is not repeated inside {@link #FAULT_LOG_EVERY}. */
    private record LoggedFault(String message, Instant at) {}

    /**
     * A corrupt history is logged by name, but once per distinct fault per {@link #FAULT_LOG_EVERY},
     * not once per request: every member reading the board while it stands would otherwise write the
     * same line at their read limit, a log flood from one bad row. The job's own run fails and says
     * so loudly once a day; this is the request path's quieter echo.
     */
    private void logCorruptHistory(SeasonWeek target, CorruptHistoryException fault) {
        Instant now = clock.instant();
        LoggedFault last = lastFault.get();
        if (last != null && last.message().equals(fault.getMessage())
                && now.isBefore(last.at().plus(FAULT_LOG_EVERY))) {
            return;
        }
        lastFault.set(new LoggedFault(fault.getMessage(), now));
        log.warn("projections: who has no history before {} is not known, a history is corrupt: {}",
                target, fault.getMessage());
    }

    /** The state and every stored line of the week it is for, scored under the caller's ruleset. */
    private View read(long profileId, Long userId) {
        // Through the ownership filter first: someone else's profile is a 404.
        ResolvedRuleset rules = profiles.byId(profileId, userId);
        Instant now = clock.instant();
        Optional<TargetWeek> found = targets.resolve(IngestProperties.seasonFor(LocalDate.now(clock)), now);
        if (found.isEmpty()) {
            return View.empty("NO_UPCOMING_WEEK", null);
        }
        TargetWeek target = found.get();
        SeasonWeek goal = target.week();
        List<SeasonWeek> stored = jdbc.query(STORED_WEEK,
                (rs, n) -> new SeasonWeek(rs.getInt("season"), rs.getInt("week")),
                config.provider(), goal.season(), goal.week());
        if (stored.isEmpty()) {
            return View.empty("UNAVAILABLE", target);
        }
        SeasonWeek week = stored.getFirst();
        Map<Long, RosterEntry> roster = availability.roster(week);
        Map<Long, Availability> statuses = availability.forWeek(week, roster);
        List<ProjectionRow> rows = jdbc.query(WEEK_LINES, (rs, n) -> scored(rs, rules, week, statuses, now),
                config.provider(), week.season(), week.week());
        if (rows.isEmpty()) {
            return new View("UNAVAILABLE", target, week, rows, roster);
        }
        return new View(week.equals(goal) ? readyOrStale(rows, now) : "STALE", target, week, rows, roster);
    }

    private ProjectionRow scored(ResultSet rs, ResolvedRuleset rules, SeasonWeek week,
            Map<Long, Availability> statuses, Instant now) throws SQLException {
        String position = rs.getString("position");
        double[] stats = new double[StatKey.COUNT];
        for (StatKey key : StatKey.values()) {
            stats[key.index()] = rs.getDouble(key.json());
        }
        ExplainedScore explained = ExplainedScore.of(new StatLine(position, stats), rules);
        double points = explained.total();

        long id = rs.getLong("player_id");
        Timestamp kickoffAt = rs.getTimestamp("kickoff_at");
        Instant kickoff = kickoffAt == null ? null : kickoffAt.toInstant();
        boolean locked = rs.getTimestamp("locked_at") != null
                || kickoff != null && !kickoff.isAfter(now);
        int gamesUsed = rs.getInt("games_used");
        return new ProjectionRow(id, rs.getString("full_name"), position, rs.getString("team"),
                rs.getString("opponent"), rs.getBoolean("home"), kickoff, locked,
                ScoringEngine.roundForDisplay(points), gamesUsed,
                gamesUsed < ESTABLISHED_GAMES ? "LIMITED" : "ESTABLISHED",
                statuses.getOrDefault(id, Availability.unknown(week)), rs.getString("espn_id"),
                rs.getTimestamp("generated_at").toInstant(), explained.parts(), explained.bonuses(),
                week.season(), week.week(), points,
                new SeasonWeek(rs.getInt("data_through_season"), rs.getInt("data_through_week")), stats);
    }

    /** {@code STALE} once every game of the target week's rows kicked off at least {@link #STALE_AFTER} ago. */
    private static String readyOrStale(List<ProjectionRow> rows, Instant now) {
        Instant cutoff = now.minus(STALE_AFTER);
        // The resolver lets a game go at exactly 48 h (kickoff_at > now - 48h holds it), so the
        // board calls a row old from exactly 48 h too.
        boolean allOld = rows.stream()
                .allMatch(row -> row.kickoff() != null && !row.kickoff().isAfter(cutoff));
        return allOld ? "STALE" : "READY";
    }

    /** Accents and punctuation dropped, so "jamarr" finds Ja'Marr Chase, as on the board. */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }
}
