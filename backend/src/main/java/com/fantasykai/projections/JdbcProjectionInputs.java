package com.fantasykai.projections;

import com.fantasykai.query.ScoringPosition;
import com.fantasykai.query.StatColumns;
import com.fantasykai.query.Usage;
import com.fantasykai.scoring.StatKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
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
            SELECT s.player_id, s.season, s.week, %s, %s
              FROM player_game_stats s
              JOIN players p ON p.id = s.player_id
              JOIN games g ON g.id = s.game_id
             WHERE g.season_type = 'REG' AND p.position IN (%s)
               AND (s.season, s.week) < (?, ?)
            """.formatted(StatColumns.SELECT_LIST, Usage.SELECT_LIST, POSITIONS);

    private static final String ZERO_STAT_APPEARANCES = """
            SELECT a.player_id, g.season, g.week
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
     * @throws IllegalArgumentException if a player has two games in one week -- a data fault
     *     that fails the run rather than projecting from a corrupt history
     */
    public Map<Long, PlayerHistory> histories(SeasonWeek target) {
        Object[] args = Stream.concat(ScoringPosition.ALL.stream(),
                Stream.of(target.season(), target.week())).toArray();
        Map<Long, List<HistoricalGame>> games = new TreeMap<>();

        jdbc.query(STAT_ROWS, rs -> {
            Usage usage = Usage.read(rs);
            games.computeIfAbsent(rs.getLong("player_id"), id -> new ArrayList<>()).add(
                    new HistoricalGame(new SeasonWeek(rs.getInt("season"), rs.getInt("week")),
                            StatColumns.readValues(rs), usage.passAtt(), usage.rushAtt(), usage.targets()));
        }, args);

        jdbc.query(ZERO_STAT_APPEARANCES, rs -> {
            games.computeIfAbsent(rs.getLong("player_id"), id -> new ArrayList<>()).add(
                    new HistoricalGame(new SeasonWeek(rs.getInt("season"), rs.getInt("week")),
                            new double[StatKey.COUNT], 0, 0, 0));
        }, args);

        Map<Long, PlayerHistory> histories = new TreeMap<>();
        games.forEach((player, played) -> {
            PlayerHistory history = PlayerHistory.before(target, played);
            if (!history.games().isEmpty()) {
                histories.put(player, history);
            }
        });
        return histories;
    }
}
