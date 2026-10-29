package com.fantasykai.api;

import com.fantasykai.projections.Availability;
import com.fantasykai.projections.AvailabilityService;
import com.fantasykai.projections.AvailabilityService.RosterEntry;
import com.fantasykai.projections.ProjectionProperties;
import com.fantasykai.projections.ProjectionProvider;
import com.fantasykai.projections.ProjectionSource;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.query.ScoringPosition;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.ScoringProfiles;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// No @Validated: see RankingsController.

/**
 * Read-only projections: the stored raw lines of the latest week the
 * configured provider has stored, scored on request under the caller's
 * ruleset by the unmodified {@link ScoringEngine}. Nothing here writes, and
 * generation never runs inside a request -- the job does that, and is off.
 *
 * <p>Availability is read at request time and decides only whether the board
 * ranks a line ({@code Availability.ranked()}); the line itself is kept
 * whatever the status. "Availability decides the board, never the raw line".
 *
 * <p>Not yet the full slice 5/6 contract in {@code docs/projections-v1-architecture.md}:
 * the target is the latest stored week, not a persisted run's target, and
 * bonuses are explained as one expected-value total. The 2026-10-28 handoff
 * lists what is owed.
 */
@RestController
@RequestMapping("/api/v1")
public class ProjectionController {

    /** A stored week whose every game kicked off this long ago is reported {@code STALE}. */
    private static final Duration STALE_AFTER = Duration.ofHours(48);

    /** Under this many games of history a projection is labelled {@code LIMITED}. */
    private static final int ESTABLISHED_GAMES = 4;

    private static final String TARGET_WEEK = """
            SELECT season, week FROM player_week_projection
             WHERE provider = ?
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

    public ProjectionController(JdbcTemplate jdbc, ScoringProfiles profiles,
            AvailabilityService availability, ProjectionProperties config,
            ProjectionProvider provider, Clock clock) {
        this.jdbc = jdbc;
        this.profiles = profiles;
        this.availability = availability;
        this.config = config;
        this.provider = provider;
        this.clock = clock;
    }

    /** One stat's share of a projected total: {@code value} x {@code rate} = {@code points}, unrounded. */
    public record Part(String stat, double value, double rate, double points) {}

    /**
     * A projected line, scored.
     *
     * @param points      the scored total, rounded once, here
     * @param history     {@code LIMITED} under four games, else {@code ESTABLISHED}
     * @param bonusPoints the threshold bonuses' expected value: the total less
     *                    its linear parts, unrounded
     */
    public record Row(long playerId, String name, String position, String team, String opponent,
            boolean home, Instant kickoff, boolean locked, double points, int gamesUsed,
            String history, Availability availability, String espnId, Instant generatedAt,
            List<Part> parts, double bonusPoints, int season, int week) {}

    /** {@code state}: {@code READY}, {@code STALE} or {@code UNAVAILABLE}. */
    public record Board(String state, ProjectionSource source, PageResponse<Row> page) {}

    /**
     * {@code state}: {@code PROJECTED} or {@code STALE} with a projection;
     * otherwise {@code UNAVAILABLE}, {@code BYE}, {@code NO_HISTORY} or
     * {@code NOT_ROSTERED}, and none.
     */
    public record Detail(String state, ProjectionSource source, Row projection) {}

    @GetMapping("/projections")
    public Board board(
            @RequestParam long profileId,
            @RequestParam(required = false) String position,
            @RequestParam(defaultValue = "") @Size(max = 60) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.MAX_SIZE)
            @Min(1) @Max(PageResponse.MAX_SIZE) int size,
            @AuthenticationPrincipal Long userId) {
        List<String> positions = ScoringPosition.resolve(position);
        String needle = normalize(q);
        List<Row> all = read(profileId, userId);
        List<Row> ranked = all.stream()
                .filter(row -> positions.contains(row.position()))
                .filter(row -> row.availability().ranked())
                .filter(row -> normalize(row.name()).contains(needle))
                .sorted(Comparator.comparingDouble(Row::points).reversed()
                        .thenComparingLong(Row::playerId))
                .toList();
        int start = (int) Math.min((long) page * size, ranked.size());
        int end = Math.min(start + size, ranked.size());
        return new Board(state(all), provider.source(),
                PageResponse.of(ranked.subList(start, end), page, size, ranked.size()));
    }

    @GetMapping("/players/{id}/projection")
    public Detail player(
            @PathVariable long id,
            @RequestParam long profileId,
            @AuthenticationPrincipal Long userId) {
        List<Row> rows = read(profileId, userId);
        boolean stale = "STALE".equals(state(rows));
        Row row = rows.stream().filter(r -> r.playerId() == id).findFirst().orElse(null);
        if (row != null) {
            return new Detail(stale ? "STALE" : "PROJECTED", provider.source(), row);
        }
        if (jdbc.queryForObject("SELECT count(*) FROM players WHERE id = ?", Long.class, id) == 0) {
            throw new PlayerNotFoundException(id);
        }
        // A reason is given only for the week being played, and only with the
        // roster evidence to back it; anything less is plain UNAVAILABLE.
        String missing = rows.isEmpty() || stale ? "UNAVAILABLE" : missingReason(id, rows.getFirst());
        return new Detail(missing, provider.source(), null);
    }

    private String missingReason(long id, Row any) {
        SeasonWeek target = new SeasonWeek(any.season(), any.week());
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

    /** Every stored line of the provider's latest week, scored under the caller's ruleset. */
    private List<Row> read(long profileId, Long userId) {
        // Through the ownership filter first: someone else's profile is a 404.
        ResolvedRuleset rules = profiles.byId(profileId, userId);
        List<SeasonWeek> target = jdbc.query(TARGET_WEEK,
                (rs, n) -> new SeasonWeek(rs.getInt("season"), rs.getInt("week")), config.provider());
        if (target.isEmpty()) {
            return List.of();
        }
        SeasonWeek week = target.getFirst();
        Map<Long, Availability> statuses = availability.forWeek(week);
        Instant now = clock.instant();
        return jdbc.query(WEEK_LINES, (rs, n) -> scored(rs, rules, week, statuses, now),
                config.provider(), week.season(), week.week());
    }

    private Row scored(ResultSet rs, ResolvedRuleset rules, SeasonWeek week,
            Map<Long, Availability> statuses, Instant now) throws SQLException {
        String position = rs.getString("position");
        double[] rates = rules.ratesFor(position);
        double[] stats = new double[StatKey.COUNT];
        List<Part> parts = new ArrayList<>();
        double linear = 0;
        for (StatKey key : StatKey.values()) {
            double value = rs.getDouble(key.json());
            double rate = rates[key.index()];
            stats[key.index()] = value;
            linear += value * rate;
            if (value != 0 || rate != 0) {
                parts.add(new Part(key.json(), value, rate, value * rate));
            }
        }
        double points = ScoringEngine.score(new StatLine(position, stats), rules);

        long id = rs.getLong("player_id");
        Timestamp kickoffAt = rs.getTimestamp("kickoff_at");
        Instant kickoff = kickoffAt == null ? null : kickoffAt.toInstant();
        boolean locked = rs.getTimestamp("locked_at") != null
                || kickoff != null && !kickoff.isAfter(now);
        int gamesUsed = rs.getInt("games_used");
        return new Row(id, rs.getString("full_name"), position, rs.getString("team"),
                rs.getString("opponent"), rs.getBoolean("home"), kickoff, locked,
                ScoringEngine.roundForDisplay(points), gamesUsed,
                gamesUsed < ESTABLISHED_GAMES ? "LIMITED" : "ESTABLISHED",
                statuses.getOrDefault(id, Availability.unknown(week)), rs.getString("espn_id"),
                rs.getTimestamp("generated_at").toInstant(), parts, points - linear,
                week.season(), week.week());
    }

    /** {@code STALE} once every game of the stored week kicked off over {@link #STALE_AFTER} ago. */
    private String state(List<Row> rows) {
        if (rows.isEmpty()) {
            return "UNAVAILABLE";
        }
        Instant cutoff = clock.instant().minus(STALE_AFTER);
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
