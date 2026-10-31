package com.fantasykai.projections;

import com.fantasykai.query.ScoringPosition;
import com.fantasykai.query.StatColumns;
import com.fantasykai.query.Usage;
import com.fantasykai.scoring.StatKey;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.stereotype.Component;

/**
 * Builds every skill player's history before a target week from what ingestion stored.
 *
 * <p>A history is a player's regular-season games, every team, every season the database
 * holds:
 *
 * <ul>
 *   <li>each stat row, with its 13 scorable stats and its volume;</li>
 *   <li>each appearance with no stat row for the same game -- a receiver who ran routes and
 *       caught nothing -- as an all-zero game. An appearance that does have a stat row is that
 *       stat row, once.</li>
 * </ul>
 *
 * <p>That is research v2's corrected dataset exactly ({@code BacktestData.load} with its
 * extras), which {@code scripts/backtest.sh parity} checks game by game. Skill means the
 * player's current {@code players.position}, as the research and the board both use. Playoff
 * rows are never history: they are filtered on {@code games.season_type}, because 2020's wild
 * card is week 18 and a week number alone cannot tell.
 *
 * <p>The SQL stops at the target week only to read fewer rows. {@link PlayerHistory#before} is
 * still the one door a game passes to reach a provider, and it enforces the cutoff itself.
 *
 * <p>Two sequential scans of {@code player_game_stats} (it has no index past its primary key,
 * by design); the anti-join from an appearance to its stat row is a primary-key lookup. Once a
 * day, inside the job -- never on a request path.
 */
@Component
public class JdbcProjectionInputs {

    private static final String POSITIONS =
            ScoringPosition.ALL.stream().map(position -> "?").collect(Collectors.joining(", "));

    private static final String STAT_ROWS = """
            SELECT s.player_id, p.gsis_id, g.nflverse_game_id, s.season, s.week, %s, %s
              FROM player_game_stats s
              JOIN players p ON p.id = s.player_id
              JOIN games g ON g.id = s.game_id
             WHERE g.season_type = 'REG' AND p.position IN (%s)
               AND (s.season, s.week) < (?, ?)
            """.formatted(StatColumns.SELECT_LIST, Usage.SELECT_LIST, POSITIONS);

    /**
     * Some players only. A literal fragment with one bound array: {@code player_game_stats}'s
     * primary key leads with {@code player_id}, so this reads by index -- 200 players in ~30 ms
     * locally (2026-10-30) -- where the whole read is two sequential scans.
     */
    private static final String STATS_FOR_PLAYERS = " AND s.player_id = ANY (?)";
    private static final String APPEARANCES_FOR_PLAYERS = " AND a.player_id = ANY (?)";

    private static final String ZERO_STAT_APPEARANCES = """
            SELECT a.player_id, p.gsis_id, g.nflverse_game_id, g.season, g.week
              FROM player_game_appearances a
              JOIN players p ON p.id = a.player_id
              JOIN games g ON g.id = a.game_id
             WHERE g.season_type = 'REG' AND p.position IN (%s)
               AND (g.season, g.week) < (?, ?)
               AND NOT EXISTS (SELECT 1 FROM player_game_stats s
                                WHERE s.player_id = a.player_id AND s.game_id = a.game_id)
            """.formatted(POSITIONS);

    private final JdbcTemplate jdbc;

    public JdbcProjectionInputs(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return each skill player with at least one game before {@code target}, by player id;
     *     a player with none is absent
     * @throws CorruptHistoryException if a player has two games in one week -- a data fault that
     *     fails the run, naming the player, the week and both games, rather than projecting from
     *     a corrupt history or quietly leaving him off the board
     */
    public Map<Long, PlayerHistory> histories(SeasonWeek target) {
        Object[] args = Stream.concat(ScoringPosition.ALL.stream(),
                Stream.of(target.season(), target.week())).toArray();
        return histories(target, STAT_ROWS, ZERO_STAT_APPEARANCES, args);
    }

    /**
     * The same histories for {@code playerIds} only, built by the same code -- the read a request
     * may make (a league's rostered players), where {@link #histories(SeasonWeek)} is the job's.
     */
    public Map<Long, PlayerHistory> histories(SeasonWeek target, Collection<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        Long[] ids = playerIds.toArray(Long[]::new);
        Object[] args = Stream.concat(Stream.concat(ScoringPosition.ALL.stream(),
                Stream.of(target.season(), target.week())), Stream.of((Object) new SqlArray(ids))).toArray();
        return histories(target, STAT_ROWS + STATS_FOR_PLAYERS,
                ZERO_STAT_APPEARANCES + APPEARANCES_FOR_PLAYERS, args);
    }

    /** A bigint[] bound through the JDBC connection, so the filter is one parameter. */
    private record SqlArray(Long[] ids) implements SqlTypeValue {
        @Override
        public void setTypeValue(PreparedStatement ps, int index, int sqlType, String typeName)
                throws SQLException {
            ps.setArray(index, ps.getConnection().createArrayOf("bigint", ids));
        }
    }

    private Map<Long, PlayerHistory> histories(SeasonWeek target, String statRows, String appearances,
            Object[] args) {
        Map<Long, List<HistoricalGame>> games = new TreeMap<>();
        // player -> week -> where each game came from, for a diagnosis that names them all.
        Map<Long, Map<SeasonWeek, List<String>>> sources = new TreeMap<>();
        Map<Long, String> gsisIds = new TreeMap<>();

        jdbc.query(statRows, rs -> {
            Usage usage = Usage.read(rs);
            SeasonWeek when = new SeasonWeek(rs.getInt("season"), rs.getInt("week"));
            long player = rs.getLong("player_id");
            games.computeIfAbsent(player, id -> new ArrayList<>()).add(new HistoricalGame(when,
                    StatColumns.readValues(rs), usage.passAtt(), usage.rushAtt(), usage.targets()));
            note(sources, gsisIds, player, rs.getString("gsis_id"), when,
                    rs.getString("nflverse_game_id") + " (stat row)");
        }, args);

        jdbc.query(appearances, rs -> {
            SeasonWeek when = new SeasonWeek(rs.getInt("season"), rs.getInt("week"));
            long player = rs.getLong("player_id");
            games.computeIfAbsent(player, id -> new ArrayList<>()).add(
                    new HistoricalGame(when, new double[StatKey.COUNT], 0, 0, 0));
            note(sources, gsisIds, player, rs.getString("gsis_id"), when,
                    rs.getString("nflverse_game_id") + " (appearance, no stat row)");
        }, args);

        refuseTwoGamesInAWeek(sources, gsisIds);

        Map<Long, PlayerHistory> histories = new TreeMap<>();
        games.forEach((player, played) -> {
            PlayerHistory history = PlayerHistory.before(target, played);
            if (!history.games().isEmpty()) {
                histories.put(player, history);
            }
        });
        return histories;
    }

    private static void note(Map<Long, Map<SeasonWeek, List<String>>> sources, Map<Long, String> gsisIds,
            long player, String gsisId, SeasonWeek when, String game) {
        sources.computeIfAbsent(player, id -> new TreeMap<>())
                .computeIfAbsent(when, w -> new ArrayList<>()).add(game);
        gsisIds.putIfAbsent(player, gsisId);
    }

    /** The first corrupt player-week in player-then-week order, and how many more there are. */
    private static void refuseTwoGamesInAWeek(Map<Long, Map<SeasonWeek, List<String>>> sources,
            Map<Long, String> gsisIds) {
        CorruptHistoryException first = null;
        int others = 0;
        for (Map.Entry<Long, Map<SeasonWeek, List<String>>> player : sources.entrySet()) {
            for (Map.Entry<SeasonWeek, List<String>> week : player.getValue().entrySet()) {
                if (week.getValue().size() > 1) {
                    if (first == null) {
                        first = new CorruptHistoryException(player.getKey(), gsisIds.get(player.getKey()),
                                week.getKey(), week.getValue().stream().sorted().toList(), 0);
                    } else {
                        others++;
                    }
                }
            }
        }
        if (first != null) {
            throw others == 0 ? first : new CorruptHistoryException(first.playerId(),
                    gsisIds.get(first.playerId()), first.week(), first.games(), others);
        }
    }
}
