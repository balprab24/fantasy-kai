package com.fantasykai.projections;

import com.fantasykai.scoring.StatKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Writes {@code player_week_projection}: raw projected lines, never points.
 *
 * <p>The insert's stat columns are generated from {@link StatKey}, as {@code StatColumns} does
 * for reads, so the table and the enum cannot disagree on which stats a projection carries.
 * Every value binds with {@code ?}; the only text assembled is enum names and a run of
 * placeholders whose length is fixed at class load (section 8).
 *
 * <p>A row whose game has kicked off is locked and is never written again. This class only
 * ever deletes unlocked rows, and the table's trigger refuses to change a locked one whatever
 * asks it to.
 */
@Repository
public class ProjectionRepository {

    /** One row to write: a raw projection, and the game and team it was made for. */
    public record ProjectedRow(long playerId, long gameId, int teamId, RawProjection projection) {}

    private static final List<String> COLUMNS = columns();

    private static List<String> columns() {
        List<String> columns = new ArrayList<>(List.of(
                "season", "week", "provider", "player_id", "game_id", "team_id"));
        Arrays.stream(StatKey.values()).map(StatKey::json).forEach(columns::add);
        columns.addAll(List.of("pass_att", "rush_att", "targets", "games_used",
                "data_through_season", "data_through_week", "generated_at"));
        return List.copyOf(columns);
    }

    private static final String INSERT = "INSERT INTO player_week_projection ("
            + String.join(", ", COLUMNS) + ") VALUES ("
            + IntStream.range(0, COLUMNS.size()).mapToObj(i -> "?").collect(Collectors.joining(", "))
            + ")";

    /**
     * Locks every row whose game has kicked off, at its kickoff: every week and every provider,
     * so a row is locked however late the job next runs. The job never wrote one after kickoff,
     * so the row as it stands is the one that was in place when the game started.
     */
    private static final String LOCK_KICKED_OFF = """
            UPDATE player_week_projection p
               SET locked_at = g.kickoff_at
              FROM games g
             WHERE g.id = p.game_id AND p.locked_at IS NULL AND g.kickoff_at <= ?
            """;

    private static final String LOCKED_PLAYERS = """
            SELECT player_id FROM player_week_projection
             WHERE season = ? AND week = ? AND provider = ? AND locked_at IS NOT NULL
            """;

    private static final String DELETE_OPEN = """
            DELETE FROM player_week_projection
             WHERE season = ? AND week = ? AND provider = ? AND locked_at IS NULL
            """;

    private final JdbcTemplate jdbc;

    public ProjectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return how many rows were locked by this call */
    public int lockKickedOff(Instant now) {
        return jdbc.update(LOCK_KICKED_OFF, TargetWeekResolver.utc(now));
    }

    /** Players already holding a locked row for this week: the job never writes them again. */
    public Set<Long> lockedPlayers(SeasonWeek week, String provider) {
        return new HashSet<>(jdbc.queryForList(LOCKED_PLAYERS, Long.class,
                week.season(), week.week(), provider));
    }

    /**
     * Replaces this week's unlocked rows for {@code provider} with {@code rows}. Run inside the
     * caller's transaction, so a failure leaves the previous rows exactly as they were.
     */
    public void replaceOpen(SeasonWeek week, String provider, List<ProjectedRow> rows, Instant generatedAt) {
        jdbc.update(DELETE_OPEN, week.season(), week.week(), provider);
        List<Object[]> args = new ArrayList<>(rows.size());
        for (ProjectedRow row : rows) {
            RawProjection p = row.projection();
            if (!p.target().equals(week) || !p.source().id().equals(provider)) {
                throw new IllegalArgumentException("a projection for " + p.target() + " by "
                        + p.source().id() + " cannot be stored as " + week + " by " + provider);
            }
            List<Object> values = new ArrayList<>(COLUMNS.size());
            values.addAll(List.of(week.season(), week.week(), provider, row.playerId(), row.gameId(),
                    row.teamId()));
            for (StatKey stat : StatKey.values()) {
                values.add(p.get(stat));
            }
            values.addAll(List.of(p.passAttempts(), p.carries(), p.targets(), p.gamesUsed(),
                    p.lastGame().season(), p.lastGame().week(), TargetWeekResolver.utc(generatedAt)));
            args.add(values.toArray());
        }
        jdbc.batchUpdate(INSERT, args);
    }
}
