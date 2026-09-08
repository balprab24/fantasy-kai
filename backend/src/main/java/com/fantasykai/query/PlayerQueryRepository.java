package com.fantasykai.query;

import com.fantasykai.scoring.StatLine;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Every read the Phase 3 API makes, as parameterized SQL.
 *
 * <p>Deliberately unoptimized -- §9 Step 1. There is no cache, no
 * pre-aggregation, and no index past the primary keys, so a rankings request
 * sequentially scans all 112,319 stat rows and scores the survivors in Java.
 * That is the baseline Phase 6 has to beat, and it is only measurable while it
 * is still true.
 *
 * <p>§8 compliance is structural rather than incidental: every filter
 * <em>value</em> binds with {@code ?}, and the only SQL assembled at runtime is
 * (a) the projection list generated from the {@link com.fantasykai.scoring.StatKey}
 * enum, (b) an {@code ORDER BY} column taken from a whitelist enum's constant,
 * and (c) a run of {@code ?} placeholders whose count comes from a validated
 * list's size. No request string is ever concatenated into a statement.
 */
@Repository
public class PlayerQueryRepository {

    private static final String PLAYER_PROJECTION = """
            SELECT p.id, p.gsis_id, p.full_name, p.position, t.abbr AS team, p.status
            FROM players p
            LEFT JOIN teams t ON t.id = p.team_id
            """;

    /**
     * Ranking input. Joins {@code games} for {@code season_type} because fantasy
     * leagues do not score the postseason, and the data runs to week 22 -- a
     * season total that quietly folded in four playoff weeks would flatter
     * players on deep teams, and {@code last4} would mean "the playoffs".
     *
     * <p>Team comes from {@code players.team_id}, not {@code player_game_stats.team_id}:
     * a ranking row is one player, and a player traded mid-season has two weekly
     * teams but one current one.
     */
    private static final String SCORABLE_PROJECTION = """
            SELECT s.player_id, p.full_name, p.position, t.abbr AS team, s.week, %s
            FROM player_game_stats s
            JOIN players p ON p.id = s.player_id
            JOIN games g ON g.id = s.game_id
            LEFT JOIN teams t ON t.id = p.team_id
            WHERE s.season = ? AND g.season_type = 'REG'
            """.formatted(StatColumns.SELECT_LIST);

    /** The opponent is whichever side of the matchup the player was not on. */
    private static final String GAMELOG = """
            SELECT s.season, s.week, g.season_type, opp.abbr AS opponent, s.snap_pct, %s
            FROM player_game_stats s
            JOIN games g ON g.id = s.game_id
            LEFT JOIN teams opp ON opp.id = CASE WHEN g.home_team_id = s.team_id
                                                 THEN g.away_team_id ELSE g.home_team_id END
            WHERE s.player_id = ?
            """.formatted(StatColumns.SELECT_LIST);

    private static final RowMapper<PlayerRow> PLAYER = (rs, n) -> new PlayerRow(
            rs.getLong("id"), rs.getString("gsis_id"), rs.getString("full_name"),
            rs.getString("position"), rs.getString("team"), rs.getString("status"));

    private static final RowMapper<ScorableRow> SCORABLE = (rs, n) -> new ScorableRow(
            rs.getLong("player_id"), rs.getString("full_name"), rs.getString("position"),
            rs.getString("team"), rs.getInt("week"),
            new StatLine(rs.getString("position"), StatColumns.readValues(rs)));

    private static final RowMapper<GamelogRow> GAMELOG_ROW = (rs, n) -> new GamelogRow(
            rs.getInt("season"), rs.getInt("week"), rs.getString("season_type"),
            rs.getString("opponent"), nullableDouble(rs, "snap_pct"),
            new StatLine(null, StatColumns.readValues(rs)));

    private final JdbcTemplate jdbc;

    public PlayerQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param sort a whitelist constant, never a raw request string
     * @param season when present, restricts to players with a stat line that season
     */
    public List<PlayerRow> findPlayers(String position, String team, Integer season,
            PlayerSort sort, boolean descending, int page, int size) {

        List<Object> args = new ArrayList<>();
        String sql = PLAYER_PROJECTION + filters(position, team, season, args)
                // Both halves are compile-time constants; the tiebreaker keeps
                // offset pagination stable when the sort column has duplicates.
                + " ORDER BY " + sort.column() + (descending ? " DESC" : " ASC") + ", p.id"
                + " LIMIT ? OFFSET ?";
        args.add(size);
        args.add((long) page * size);

        return jdbc.query(sql, PLAYER, args.toArray());
    }

    public long countPlayers(String position, String team, Integer season) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT count(*) FROM players p LEFT JOIN teams t ON t.id = p.team_id"
                + filters(position, team, season, args);

        Long total = jdbc.queryForObject(sql, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public Optional<PlayerRow> findById(long id) {
        return jdbc.query(PLAYER_PROJECTION + " WHERE p.id = ?", PLAYER, id).stream().findFirst();
    }

    public List<GamelogRow> findGamelog(long playerId, Integer season) {
        if (season == null) {
            return jdbc.query(GAMELOG + " ORDER BY s.season, s.week", GAMELOG_ROW, playerId);
        }
        return jdbc.query(GAMELOG + " AND s.season = ? ORDER BY s.season, s.week",
                GAMELOG_ROW, playerId, season);
    }

    /**
     * Every scorable player-week for a season, unsorted and unpaginated.
     *
     * <p>Unpaginated on purpose. Points do not exist in SQL, so the ranking
     * cannot be ordered or sliced there -- the caller scores all of these in
     * Java, sorts, and only then takes a page. That is exactly the recomputation
     * §9 identifies as the bottleneck, and shrinking this result set would hide it.
     *
     * @param weekFloor inclusive lower bound on week, or null for the whole season
     */
    public List<ScorableRow> findScorableRows(int season, List<String> positions, Integer weekFloor) {
        List<Object> args = new ArrayList<>();
        args.add(season);

        // Placeholder count comes from the list's size; the position strings are
        // bound, never spliced.
        String sql = SCORABLE_PROJECTION + " AND p.position IN ("
                + positions.stream().map(p -> "?").collect(Collectors.joining(", ")) + ")";
        args.addAll(positions);

        if (weekFloor != null) {
            sql += " AND s.week >= ?";
            args.add(weekFloor);
        }
        return jdbc.query(sql, SCORABLE, args.toArray());
    }

    /** Highest regular-season week with data, which is what {@code last4} counts back from. */
    public Optional<Integer> latestRegularWeek(int season) {
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT max(s.week) FROM player_game_stats s
                JOIN games g ON g.id = s.game_id
                WHERE s.season = ? AND g.season_type = 'REG'
                """, Integer.class, season));
    }

    /** Fragments are literal; only the values are bound. */
    private static String filters(String position, String team, Integer season, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (position != null) {
            where.append(" AND p.position = ?");
            args.add(position);
        }
        if (team != null) {
            where.append(" AND t.abbr = ?");
            args.add(team);
        }
        if (season != null) {
            where.append(" AND EXISTS (SELECT 1 FROM player_game_stats s"
                    + " WHERE s.player_id = p.id AND s.season = ?)");
            args.add(season);
        }
        return where.toString();
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
