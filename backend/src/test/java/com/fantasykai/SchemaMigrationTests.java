package com.fantasykai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.fantasykai.ingest.IntegrityChecks;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 0 acceptance test: the app boots against a real PostgreSQL 16, Flyway
 * applies V1 cleanly, and the health endpoint reports UP.
 *
 * <p>Uses a throwaway container rather than the docker-compose database, so CI
 * and local runs are identical and neither touches developer data.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SchemaMigrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void flywayAppliesV1() {
        Boolean success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void schemaContainsEveryTable() {
        List<String> tables = jdbc.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """,
                String.class);

        assertThat(tables).contains(
                "teams", "players", "games", "player_game_stats",
                "users", "scoring_profiles", "ingest_runs");
    }

    @Test
    @Transactional
    void detectsSeasonWeekDriftFromTheJoinedGame() {
        Integer det = jdbc.queryForObject(
                "INSERT INTO teams (abbr, name) VALUES ('DET', 'Detroit Lions') RETURNING id", Integer.class);
        Integer gb = jdbc.queryForObject(
                "INSERT INTO teams (abbr, name) VALUES ('GB', 'Green Bay Packers') RETURNING id", Integer.class);
        Long game = jdbc.queryForObject(
                """
                INSERT INTO games (nflverse_game_id, season, week, season_type, home_team_id, away_team_id)
                VALUES ('2025_01_GB_DET', 2025, 1, 'REG', ?, ?) RETURNING id
                """, Long.class, det, gb);
        Long player = jdbc.queryForObject(
                """
                INSERT INTO players (gsis_id, full_name, position, team_id)
                VALUES ('00-0000001', 'Test Player', 'WR', ?) RETURNING id
                """, Long.class, det);

        // A row whose denormalized season/week agree with its game: no drift.
        jdbc.update("""
                INSERT INTO player_game_stats (player_id, game_id, season, week, team_id)
                VALUES (?, ?, 2025, 1, ?)
                """, player, game, det);
        assertThat(jdbc.queryForObject(IntegrityChecks.SEASON_WEEK_DRIFT, Long.class)).isZero();

        // Drift the copy away from the source of truth: the check must catch it.
        jdbc.update("UPDATE player_game_stats SET season = 2024 WHERE player_id = ? AND game_id = ?",
                player, game);
        assertThat(jdbc.queryForObject(IntegrityChecks.SEASON_WEEK_DRIFT, Long.class)).isEqualTo(1L);
    }

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
