package com.fantasykai.query;

import com.fantasykai.scoring.StatLine;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
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
 * That is the baseline Phase 11 has to beat, and it is only measurable while it
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

    /**
     * {@code external_ids ->> 'espn'} rather than {@code ? 'espn'}: JdbcTemplate
     * reads a jsonb {@code ?} operator as a bind placeholder.
     */
    private static final String PLAYER_PROJECTION = """
            SELECT p.id, p.gsis_id, p.full_name, p.position, t.abbr AS team, p.status,
                   p.external_ids ->> 'espn' AS espn_id, p.birth_date,
                   t.name AS team_name, t.logo_url AS team_logo
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

    /**
     * The opponent is whichever side of the matchup the player was not on. Both
     * CASEs name both sides and have no ELSE, so a stat row whose team is neither
     * -- a data fault, 0 rows today -- reads back as NULL rather than as the
     * player's own team for an opponent and "away" for a venue.
     *
     * <p>Still a primary-key lookup on {@code player_id}: this is the query
     * {@code docs/perf/baseline.md} keeps as the control, and it must stay one.
     */
    private static final String GAMELOG = """
            SELECT s.season, s.week, g.season_type, own.abbr AS team, opp.abbr AS opponent,
                   CASE WHEN g.home_team_id = s.team_id THEN TRUE
                        WHEN g.away_team_id = s.team_id THEN FALSE END AS home,
                   s.snap_pct, %s, %s
            FROM player_game_stats s
            JOIN games g ON g.id = s.game_id
            JOIN teams own ON own.id = s.team_id
            LEFT JOIN teams opp ON opp.id = CASE WHEN g.home_team_id = s.team_id THEN g.away_team_id
                                                 WHEN g.away_team_id = s.team_id THEN g.home_team_id END
            WHERE s.player_id = ?
            """.formatted(Usage.SELECT_LIST, StatColumns.SELECT_LIST);

    /**
     * Every regular-season week at one position, for ranking a player among his
     * peers. The joins and the {@code REG} filter are the scorable projection's,
     * so a career rank is taken over exactly the rows a ranking scores; the
     * projection is narrower -- no name, no team -- because nothing here is
     * rendered. The position is the player's current one, as it is on the board.
     */
    private static final String POSITION_WEEKS = """
            SELECT s.player_id, s.season, s.week, %s
            FROM player_game_stats s
            JOIN players p ON p.id = s.player_id
            JOIN games g ON g.id = s.game_id
            WHERE p.position = ? AND g.season_type = 'REG'
            """.formatted(StatColumns.SELECT_LIST);

    /** nflverse ships digits; anything else is a format change, not an id. */
    private static final Pattern ESPN_ID = Pattern.compile("[0-9]{1,12}");

    // getObject(LocalDate): a DATE read as a date, with no trip through
    // java.sql.Date and the JVM's time zone on the way.
    private static final RowMapper<PlayerRow> PLAYER = (rs, n) -> new PlayerRow(
            rs.getLong("id"), rs.getString("gsis_id"), rs.getString("full_name"),
            rs.getString("position"), rs.getString("team"), rs.getString("status"),
            espnId(rs.getString("espn_id")), rs.getObject("birth_date", LocalDate.class),
            rs.getString("team_name"), rs.getString("team_logo"));

    private static final RowMapper<ScorableRow> SCORABLE = (rs, n) -> new ScorableRow(
            rs.getLong("player_id"), rs.getString("full_name"), rs.getString("position"),
            rs.getString("team"), rs.getInt("week"),
            new StatLine(rs.getString("position"), StatColumns.readValues(rs)));

    private static final RowMapper<GamelogRow> GAMELOG_ROW = (rs, n) -> {
        // Read into a local straight after the column it describes: wasNull()
        // reports on the last column read, whichever that was.
        boolean home = rs.getBoolean("home");
        Boolean venue = rs.wasNull() ? null : home;
        return new GamelogRow(
                rs.getInt("season"), rs.getInt("week"), rs.getString("season_type"),
                rs.getString("team"), rs.getString("opponent"), venue,
                nullableDouble(rs, "snap_pct"), Usage.read(rs),
                new StatLine(null, StatColumns.readValues(rs)));
    };

    private static final RowMapper<PositionWeekRow> POSITION_WEEK = (rs, n) -> new PositionWeekRow(
            rs.getLong("player_id"), rs.getInt("season"), rs.getInt("week"),
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
     * ESPN ids for one page of players. A primary-key lookup per id, run after a
     * ranking has been sorted and sliced -- so it costs one page, not one season,
     * and the scorable query the §9 baseline measures is untouched.
     *
     * @return only players carrying a well-formed id; an empty input issues no query
     */
    public Map<Long, String> findEspnIds(Collection<Long> playerIds) {
        Map<Long, String> ids = new HashMap<>();
        if (playerIds.isEmpty()) {
            return ids; // "IN ()" is a syntax error, and there is nothing to ask
        }
        String sql = "SELECT id, external_ids ->> 'espn' AS espn_id FROM players WHERE id IN ("
                + playerIds.stream().map(id -> "?").collect(Collectors.joining(", ")) + ")";
        jdbc.query(sql, rs -> {
            String espn = espnId(rs.getString("espn_id"));
            if (espn != null) {
                ids.put(rs.getLong("id"), espn);
            }
        }, playerIds.toArray());
        return ids;
    }

    /**
     * Every regular-season week played at {@code position} in the given seasons.
     * A full scan of {@code player_game_stats} -- the same cost class as one
     * ranking request, and for the same reason: nothing but the primary key is
     * indexed, by design, until Phase 11.
     */
    public List<PositionWeekRow> findPositionWeeks(String position, Collection<Integer> seasons) {
        if (seasons.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(position);
        args.addAll(seasons);
        String sql = POSITION_WEEKS + " AND s.season IN ("
                + seasons.stream().map(s -> "?").collect(Collectors.joining(", ")) + ")";
        return jdbc.query(sql, POSITION_WEEK, args.toArray());
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

    /** A well-formed ESPN id, or null -- never a malformed one handed to an image URL. */
    static String espnId(String raw) {
        return raw != null && ESPN_ID.matcher(raw).matches() ? raw : null;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
