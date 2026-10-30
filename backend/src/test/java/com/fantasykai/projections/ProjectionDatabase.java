package com.fantasykai.projections;

import com.fantasykai.scoring.StatKey;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real PostgreSQL 16 with every migration applied, for the projection tests -- without a
 * Spring context.
 *
 * <p>Each {@code @SpringBootTest} class costs a cached context that closes serially at JVM
 * exit, and that teardown is the largest single term in the build (CLAUDE.md, "./mvnw verify
 * takes either ~32s or ~58s"). These tests need a schema, a {@code JdbcTemplate} and a
 * transaction manager, not an application, so they share one container started on first use,
 * migrated by Flyway exactly as the application would migrate it, and torn down by
 * Testcontainers' reaper when the JVM exits.
 *
 * <p>Seeding helpers write rows straight into the tables, with the kickoff times a lifecycle
 * test needs to control. Public for the {@code ingest} tests that seed through it.
 */
public final class ProjectionDatabase {

    private static ProjectionDatabase instance;

    final HikariDataSource dataSource;
    public final JdbcTemplate jdbc;
    public final TransactionTemplate transaction;

    private ProjectionDatabase(PostgreSQLContainer<?> postgres) {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setMaximumPoolSize(4);
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @SuppressWarnings("resource") // reaped by Testcontainers at JVM exit, shared by every class
    public static synchronized ProjectionDatabase get() {
        if (instance == null) {
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
            instance = new ProjectionDatabase(postgres);
        }
        return instance;
    }

    /** Empties every table these tests write. TRUNCATE does not fire the lock trigger. */
    public void reset() {
        jdbc.execute("""
                TRUNCATE player_week_projection, player_week_projection_repairs,
                         player_game_appearances, player_game_stats, roster_week, injury_report,
                         games, players, teams, ingest_runs RESTART IDENTITY CASCADE
                """);
    }

    public int team(String abbr) {
        return jdbc.queryForObject("INSERT INTO teams (abbr, name) VALUES (?, ?) RETURNING id",
                Integer.class, abbr, abbr);
    }

    public long player(String name, String position, Integer teamId) {
        return jdbc.queryForObject("""
                INSERT INTO players (gsis_id, full_name, position, team_id)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class, "00-" + name.hashCode(), name, position, teamId);
    }

    /** A player with his real nflverse gsis id, as an availability feed names him. */
    public long player(String gsisId, String name, String position, Integer teamId) {
        return jdbc.queryForObject("INSERT INTO players (gsis_id, full_name, position, team_id)"
                + " VALUES (?, ?, ?, ?) RETURNING id", Long.class, gsisId, name, position, teamId);
    }

    /** One roster row, replacing any he already has that week. */
    public void roster(int season, int week, long playerId, int teamId, String status) {
        jdbc.update("DELETE FROM roster_week WHERE season = ? AND week = ? AND player_id = ?",
                season, week, playerId);
        jdbc.update("INSERT INTO roster_week (season, week, player_id, team_id, status, status_detail,"
                + " ingested_at) VALUES (?, ?, ?, ?, ?, NULL, now())", season, week, playerId, teamId, status);
    }

    /** One injury-report row; {@code reportStatus} null for listed without a game status. */
    public void injury(int season, int week, long playerId, int teamId, String reportStatus) {
        jdbc.update("INSERT INTO injury_report (season, week, player_id, team_id, report_status, ingested_at)"
                + " VALUES (?, ?, ?, ?, ?, now())", season, week, playerId, teamId, reportStatus);
    }

    /** The schedule no longer lists it: what {@code GameIngestor} records when nflverse drops a game. */
    public void delist(long gameId) {
        jdbc.update("UPDATE games SET delisted_at = now() WHERE id = ?", gameId);
    }

    public void moveTo(long playerId, Integer teamId) {
        jdbc.update("UPDATE players SET team_id = ? WHERE id = ?", teamId, playerId);
    }

    public long game(int season, int week, String seasonType, int home, int away, Instant kickoff) {
        return jdbc.queryForObject("""
                INSERT INTO games (nflverse_game_id, season, week, season_type, home_team_id,
                                   away_team_id, kickoff_at)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, "%d_%02d_%d_%d".formatted(season, week, away, home), season, week,
                seasonType, home, away, kickoff == null ? null : kickoff.atOffset(ZoneOffset.UTC));
    }

    public long game(int season, int week, int home, int away, Instant kickoff) {
        return game(season, week, "REG", home, away, kickoff);
    }

    /** A stat row carrying {@code line}'s 13 scorable stats and its three volume counts. */
    public void stats(long playerId, long gameId, int teamId, HistoricalGame line) {
        stats(playerId, gameId, teamId, line.stats(), line.passAttempts(), line.carries(), line.targets());
    }

    /**
     * A stat row, with season and week copied from its game as the ingest does. The columns are
     * SMALLINT, so a fractional value is refused here rather than truncated into a different
     * test than the one written.
     */
    public void stats(long playerId, long gameId, int teamId, double[] stats, double passAttempts,
            double carries, double targets) {
        Object[] args = new Object[6 + StatKey.COUNT + 1];
        args[0] = playerId;
        args[1] = gameId;
        args[2] = teamId;
        args[3] = whole(passAttempts);
        args[4] = whole(carries);
        args[5] = whole(targets);
        for (int i = 0; i < StatKey.COUNT; i++) {
            args[6 + i] = whole(stats[i]);
        }
        args[6 + StatKey.COUNT] = gameId;
        jdbc.update(INSERT_STATS, args);
    }

    private static final String INSERT_STATS = """
            INSERT INTO player_game_stats (player_id, game_id, season, week, team_id,
                                           pass_att, rush_att, targets, %s)
            SELECT ?, ?, g.season, g.week, ?, ?, ?, ?, %s FROM games g WHERE g.id = ?
            """.formatted(
                    Arrays.stream(StatKey.values()).map(StatKey::json).collect(Collectors.joining(", ")),
                    Arrays.stream(StatKey.values()).map(stat -> "?").collect(Collectors.joining(", ")));

    private static short whole(double value) {
        if (value != Math.rint(value)) {
            throw new IllegalArgumentException(value + " is not a whole number of plays");
        }
        return (short) value;
    }

    /** A stat row with nothing in it but one catch -- the cheapest way to mark a game played. */
    public void played(long playerId, long gameId, int teamId) {
        double[] stats = new double[StatKey.COUNT];
        stats[StatKey.REC.index()] = 1;
        stats(playerId, gameId, teamId, stats, 0, 0, 1);
    }

    public void appearance(long playerId, long gameId, int teamId, int offenseSnaps) {
        jdbc.update("""
                INSERT INTO player_game_appearances (player_id, game_id, team_id, offense_snaps)
                VALUES (?, ?, ?, ?)
                """, playerId, gameId, teamId, offenseSnaps);
    }
}
