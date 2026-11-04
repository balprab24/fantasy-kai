package com.fantasykai.leagues;

import com.fantasykai.projections.Availability;
import com.fantasykai.projections.AvailabilityService;
import com.fantasykai.projections.AvailabilityStatus;
import com.fantasykai.projections.JdbcProjectionInputs;
import com.fantasykai.projections.PlayerHistory;
import com.fantasykai.projections.ProjectionProvider;
import com.fantasykai.projections.RawProjection;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.query.ScoringPosition;
import com.fantasykai.scoring.ExplainedScore;
import com.fantasykai.scoring.ResolvedRuleset;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Each player's expected points for every week left in a league's regular season, under that
 * league's ruleset -- league plan S2's "V1.1". Computed on read; nothing is stored, and the
 * projection job and its table are not involved: generation stays the owner's switch.
 *
 * <p>For each player:
 * <ul>
 *   <li>the provider's line from his games before the first remaining week ({@link
 *       PlayerHistory#before}), scored once as an {@link ExplainedScore}. The weighted average is
 *       flat, so it is the same line every week;</li>
 *   <li>0 in a week his team has no regular-season game -- the bye;</li>
 *   <li>0 in the first remaining week only when he is Out for it;</li>
 *   <li>0 every week while on a reserve list or off the roster. A player on injured reserve may
 *       come back; until he is activated, he is projected not to (stated, not modelled);</li>
 *   <li>Questionable and Doubtful get the full line. {@link AvailabilityStatus} carries no
 *       probabilities, and this does not invent one.</li>
 * </ul>
 *
 * <p>Reads by player id only: {@code player_game_stats}' primary key leads with
 * {@code player_id}, so a league's ~200 rostered players cost an index read, never the
 * sequential scan the job makes.
 *
 * <p>The first remaining week comes from {@code games} alone: the first regular-season week with
 * a scheduled game not yet kicked off. Between Monday night and the Tuesday pull that is a day
 * ahead of {@code TargetWeekResolver}, which waits for stats; it never reads stats to decide.
 */
@Component
public class RestOfSeason {

    /** The regular season's last week when a league has no playoffs. */
    static final int LAST_REGULAR_WEEK = 18;

    public enum Status {
        /** Projected from his history. */
        PROJECTED,
        /** A skill player with no game to project from: a rookie before his first snap. */
        NO_PROJECTION,
        /** K or DST: not modelled (north-star §8). */
        NOT_MODELED
    }

    /**
     * One player over the remaining weeks.
     *
     * @param perGame      his projected line, scored; null unless {@code PROJECTED}
     * @param weekly       expected points by week, unrounded -- 0 in a bye or a week he cannot play
     * @param availability what is known about the first remaining week
     * @param teamId       the NFL team the bye was read from; null when he has none
     */
    public record Outlook(long playerId, String position, Status status, ExplainedScore perGame,
            Map<Integer, Double> weekly, AvailabilityStatus availability, Integer teamId) {

        public double mean() {
            return weekly.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        public double week(int week) {
            return weekly.getOrDefault(week, 0.0);
        }
    }

    /** @param weeks the regular-season weeks still to play, first to last; empty when none are */
    public record Projection(int season, List<Integer> weeks, Map<Long, Outlook> players) {}

    private static final String FIRST_OPEN_WEEK = """
            SELECT min(week) FROM games
             WHERE season = ? AND season_type = 'REG' AND delisted_at IS NULL
               AND (kickoff_at IS NULL OR kickoff_at > ?)
            """;

    private static final String SCHEDULE = """
            SELECT week, home_team_id, away_team_id FROM games
             WHERE season = ? AND season_type = 'REG' AND delisted_at IS NULL AND week BETWEEN ? AND ?
            """;

    private final JdbcTemplate jdbc;
    private final JdbcProjectionInputs inputs;
    private final ProjectionProvider provider;
    private final AvailabilityService availability;
    private final Clock clock;

    public RestOfSeason(JdbcTemplate jdbc, JdbcProjectionInputs inputs, ProjectionProvider provider,
            AvailabilityService availability, Clock clock) {
        this.jdbc = jdbc;
        this.inputs = inputs;
        this.provider = provider;
        this.availability = availability;
        this.clock = clock;
    }

    /**
     * @param positions        each player's position, by our player id
     * @param playoffWeekStart the league's first playoff week; 0 or null when it has none
     */
    public Projection project(int season, Integer playoffWeekStart, Map<Long, String> positions,
            ResolvedRuleset rules) {
        List<Integer> weeks = weeks(season, playoffWeekStart);
        if (weeks.isEmpty()) {
            return new Projection(season, weeks, Map.of());
        }
        SeasonWeek first = new SeasonWeek(season, weeks.getFirst());
        Map<Integer, Set<Integer>> playing = schedule(season, weeks.getFirst(), weeks.getLast());

        List<Long> skill = positions.entrySet().stream()
                .filter(e -> ScoringPosition.ALL.contains(e.getValue())).map(Map.Entry::getKey).toList();
        Map<Long, PlayerHistory> histories = inputs.histories(first, skill);
        Map<Long, Availability> known = availability.forWeek(first);
        Map<Long, Integer> listedTeams = listedTeams(positions.keySet());

        Map<Long, Outlook> players = new LinkedHashMap<>();
        positions.forEach((id, position) -> {
            Availability status = known.getOrDefault(id, Availability.unknown(first));
            Integer team = status.teamId() != null ? status.teamId() : listedTeams.get(id);
            if (!ScoringPosition.ALL.contains(position)) {
                players.put(id, new Outlook(id, position, Status.NOT_MODELED, null, zeros(weeks),
                        status.status(), team));
                return;
            }
            PlayerHistory history = histories.get(id);
            Optional<RawProjection> line = history == null ? Optional.empty() : provider.project(history);
            if (line.isEmpty()) {
                players.put(id, new Outlook(id, position, Status.NO_PROJECTION, null, zeros(weeks),
                        status.status(), team));
                return;
            }
            ExplainedScore perGame = ExplainedScore.of(line.get().line(position), rules);
            Map<Integer, Double> weekly = new LinkedHashMap<>();
            for (int week : weeks) {
                weekly.put(week, plays(week, first.week(), team, status.status(), playing) ? perGame.total() : 0.0);
            }
            players.put(id, new Outlook(id, position, Status.PROJECTED, perGame, weekly, status.status(), team));
        });
        return new Projection(season, weeks, players);
    }

    /** Whether he is expected to play {@code week}: a game, a roster spot, and not Out for it. */
    static boolean plays(int week, int firstWeek, Integer team, AvailabilityStatus status,
            Map<Integer, Set<Integer>> playing) {
        if (team == null || !playing.getOrDefault(week, Set.of()).contains(team)) {
            return false; // no team, or his team's bye
        }
        if (status == AvailabilityStatus.RESERVE || status == AvailabilityStatus.NOT_ON_ROSTER) {
            return false;
        }
        return !(status == AvailabilityStatus.OUT && week == firstWeek);
    }

    /** From the first regular-season week not yet kicked off to the week before the league's playoffs. */
    List<Integer> weeks(int season, Integer playoffWeekStart) {
        // An OffsetDateTime binds as timestamptz exactly, with no JVM time zone on the way
        // (TargetWeekResolver.utc).
        Integer first = jdbc.queryForObject(FIRST_OPEN_WEEK, Integer.class, season,
                clock.instant().atOffset(ZoneOffset.UTC));
        int last = playoffWeekStart != null && playoffWeekStart > 1
                ? Math.min(playoffWeekStart - 1, LAST_REGULAR_WEEK) : LAST_REGULAR_WEEK;
        List<Integer> weeks = new ArrayList<>();
        if (first != null) {
            for (int week = first; week <= last; week++) {
                weeks.add(week);
            }
        }
        return weeks;
    }

    /** Week to the teams with a game that week; a team absent from a week is on its bye. */
    private Map<Integer, Set<Integer>> schedule(int season, int from, int to) {
        Map<Integer, Set<Integer>> playing = new HashMap<>();
        jdbc.query(SCHEDULE, rs -> {
            Set<Integer> teams = playing.computeIfAbsent(rs.getInt("week"), w -> new HashSet<>());
            teams.add(rs.getInt("home_team_id"));
            teams.add(rs.getInt("away_team_id"));
        }, season, from, to);
        return playing;
    }

    /** {@code players.team_id}, for a player no published roster lists. */
    private Map<Long, Integer> listedTeams(Collection<Long> ids) {
        Map<Long, Integer> teams = new HashMap<>();
        if (ids.isEmpty()) {
            return teams;
        }
        Long[] array = ids.toArray(Long[]::new);
        jdbc.query("SELECT id, team_id FROM players WHERE id = ANY (?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", array)),
                rs -> {
                    int team = rs.getInt("team_id");
                    if (!rs.wasNull()) {
                        teams.put(rs.getLong("id"), team);
                    }
                });
        return teams;
    }

    private static Map<Integer, Double> zeros(List<Integer> weeks) {
        Map<Integer, Double> weekly = new LinkedHashMap<>();
        weeks.forEach(w -> weekly.put(w, 0.0));
        return weekly;
    }
}
