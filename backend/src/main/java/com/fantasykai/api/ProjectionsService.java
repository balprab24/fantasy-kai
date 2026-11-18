package com.fantasykai.api;

import com.fantasykai.ingest.IngestProperties;
import com.fantasykai.projections.Availability;
import com.fantasykai.projections.AvailabilityService;
import com.fantasykai.projections.AvailabilityService.RosterEntry;
import com.fantasykai.projections.CurrentTargetWeek;
import com.fantasykai.projections.ProjectionProperties;
import com.fantasykai.projections.ProjectionProvider;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.projections.TargetWeek;
import com.fantasykai.projections.TargetWeekResolver;
import com.fantasykai.query.ScoringPosition;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only projections: the stored raw lines of the latest week the configured provider has
 * stored, scored on request under the caller's ruleset by the unmodified {@link ScoringEngine}.
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
 *       kicked off over {@link TargetWeekResolver#UNREPORTED_AFTER} ago;</li>
 *   <li>rows stored only for an earlier week: those rows, as {@code STALE} -- the job has not
 *       written the new week yet, or has stopped;</li>
 *   <li>nothing stored at all, or nothing readable: {@code UNAVAILABLE}.</li>
 * </ul>
 *
 * <p>Not yet the full slice 5/6 contract in {@code docs/projections-v1-architecture.md}:
 * bonuses are explained as one remainder (the total less the linear parts), not one
 * line each. The plan lists what is owed.
 */
@Service
public class ProjectionsService {

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

    /** The newest stored week at or before the target: the target itself, else an earlier one. */
    private static final String STORED_WEEK = """
            SELECT season, week FROM player_week_projection
             WHERE provider = ? AND (season < ? OR (season = ? AND week <= ?))
             ORDER BY season DESC, week DESC
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

    public ProjectionsService(JdbcTemplate jdbc, ScoringProfiles profiles,
            AvailabilityService availability, ProjectionProperties config,
            ProjectionProvider provider, Clock clock, CurrentTargetWeek targets) {
        this.jdbc = jdbc;
        this.profiles = profiles;
        this.availability = availability;
        this.config = config;
        this.provider = provider;
        this.clock = clock;
        this.targets = targets;
    }

    /**
     * What a request sees: the state, the week the rows are for (null when there are none), and
     * the rows, scored.
     */
    private record View(String state, SeasonWeek week, List<ProjectionRow> rows) {}

    public ProjectionBoard board(long profileId, String position, String q, int page, int size,
            Long userId) {
        List<String> positions = ScoringPosition.resolve(position);
        String needle = normalize(q);
        View view = read(profileId, userId);
        List<ProjectionRow> ranked = view.rows().stream()
                .filter(row -> positions.contains(row.position()))
                .filter(row -> row.availability().ranked())
                .filter(row -> normalize(row.name()).contains(needle))
                .sorted(BOARD_ORDER)
                .toList();
        int start = (int) Math.min((long) page * size, ranked.size());
        int end = Math.min(start + size, ranked.size());
        return new ProjectionBoard(view.state(), provider.source(),
                PageResponse.of(ranked.subList(start, end), page, size, ranked.size()));
    }

    public PlayerProjectionResponse player(long id, long profileId, Long userId) {
        View view = read(profileId, userId);
        ProjectionRow row = view.rows().stream().filter(r -> r.playerId() == id).findFirst().orElse(null);
        if (row != null) {
            return new PlayerProjectionResponse("STALE".equals(view.state()) ? "STALE" : "PROJECTED",
                    provider.source(), row);
        }
        if (jdbc.queryForObject("SELECT count(*) FROM players WHERE id = ?", Long.class, id) == 0) {
            throw new PlayerNotFoundException(id);
        }
        // A reason is given only for the week being played, and only with the
        // roster evidence to back it; anything less is plain UNAVAILABLE.
        String missing = switch (view.state()) {
            case "NO_UPCOMING_WEEK" -> "NO_UPCOMING_WEEK";
            case "READY" -> missingReason(id, view.week());
            default -> "UNAVAILABLE";
        };
        return new PlayerProjectionResponse(missing, provider.source(), null);
    }

    private String missingReason(long id, SeasonWeek target) {
        Map<Long, RosterEntry> roster = availability.roster(target);
        RosterEntry entry = roster.get(id);
        if (entry == null) {
            return roster.isEmpty() ? "UNAVAILABLE" : "NOT_ROSTERED";
        }
        Long games = jdbc.queryForObject("""
                SELECT count(*) FROM games
                 WHERE season = ? AND week = ? AND season_type = 'REG' AND delisted_at IS NULL
                   AND (home_team_id = ? OR away_team_id = ?)
                """, Long.class, target.season(), target.week(), entry.teamId(), entry.teamId());
        if (games == 0) {
            return "BYE";
        }
        Long appearances = jdbc.queryForObject(
                "SELECT count(*) FROM player_game_appearances WHERE player_id = ?", Long.class, id);
        Long statLines = jdbc.queryForObject(
                "SELECT count(*) FROM player_game_stats WHERE player_id = ?", Long.class, id);
        return appearances == 0 && statLines == 0 ? "NO_HISTORY" : "UNAVAILABLE";
    }

    /** The state and every stored line of the week it is for, scored under the caller's ruleset. */
    private View read(long profileId, Long userId) {
        // Through the ownership filter first: someone else's profile is a 404.
        ResolvedRuleset rules = profiles.byId(profileId, userId);
        Instant now = clock.instant();
        Optional<TargetWeek> target = targets.resolve(IngestProperties.seasonFor(LocalDate.now(clock)), now);
        if (target.isEmpty()) {
            return new View("NO_UPCOMING_WEEK", null, List.of());
        }
        SeasonWeek goal = target.get().week();
        List<SeasonWeek> stored = jdbc.query(STORED_WEEK,
                (rs, n) -> new SeasonWeek(rs.getInt("season"), rs.getInt("week")),
                config.provider(), goal.season(), goal.season(), goal.week());
        if (stored.isEmpty()) {
            return new View("UNAVAILABLE", null, List.of());
        }
        SeasonWeek week = stored.getFirst();
        Map<Long, Availability> statuses = availability.forWeek(week);
        List<ProjectionRow> rows = jdbc.query(WEEK_LINES, (rs, n) -> scored(rs, rules, week, statuses, now),
                config.provider(), week.season(), week.week());
        if (rows.isEmpty()) {
            return new View("UNAVAILABLE", week, rows);
        }
        return new View(week.equals(goal) ? readyOrStale(rows, now) : "STALE", week, rows);
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
                rs.getTimestamp("generated_at").toInstant(), explained.parts(), explained.bonusPoints(),
                week.season(), week.week(), points);
    }

    /** {@code STALE} once every game of the target week's rows kicked off over {@link #STALE_AFTER} ago. */
    private static String readyOrStale(List<ProjectionRow> rows, Instant now) {
        Instant cutoff = now.minus(STALE_AFTER);
        boolean allOld = rows.stream()
                .allMatch(row -> row.kickoff() != null && row.kickoff().isBefore(cutoff));
        return allOld ? "STALE" : "READY";
    }

    /** Accents and punctuation dropped, so "jamarr" finds Ja'Marr Chase, as on the board. */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }
}
