package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fantasykai.scoring.StatKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V7's two tables, held to what they promise -- each promise proved by trying to break it.
 */
class ProjectionSchemaTests {

    private static final Instant KICKOFF = Instant.parse("2026-10-11T17:00:00Z");

    private final ProjectionDatabase db = ProjectionDatabase.get();

    private long player;
    private long game;
    private int team;

    @BeforeEach
    void seed() {
        db.reset();
        team = db.team("KC");
        int other = db.team("DET");
        player = db.player("Projected Receiver", "WR", team);
        game = db.game(2026, 5, team, other, KICKOFF);
    }

    /** Every stat any ruleset can score, under StatKey's own name, and nothing that is a point. */
    @Test
    void theProjectionTableCarriesEveryScorableStatAsAnUnroundedDouble() {
        Map<String, String> types = columnTypes("player_week_projection");

        for (StatKey stat : StatKey.values()) {
            assertThat(types).as(stat.json()).containsEntry(stat.json(), "double precision");
        }
        assertThat(types).containsEntry("pass_att", "double precision")
                .containsEntry("rush_att", "double precision")
                .containsEntry("targets", "double precision");
    }

    /** "Project the stat line, never the points" -- a point value is right for one ruleset only. */
    @Test
    void neitherTableHasAColumnThatCouldHoldPoints() {
        for (String table : List.of("player_week_projection", "player_game_appearances")) {
            assertThat(columnTypes(table).keySet()).as(table)
                    .isNotEmpty()
                    .noneMatch(column -> column.matches(".*(point|pts|fantasy|score).*"));
        }
    }

    /**
     * Storage must not round: a projection is an expected value, and the API rounds once. Values
     * chosen to have no short decimal form.
     */
    @Test
    void aProjectedValueReadsBackBitForBit() {
        double[] stats = new double[StatKey.COUNT];
        for (StatKey stat : StatKey.values()) {
            stats[stat.index()] = (0.1 + 0.2) * (stat.index() + 1) / 3.0 + Math.PI * 1e-9;
        }
        RawProjection projection = new RawProjection(WeightedAverageProjectionProvider.SOURCE,
                new SeasonWeek(2026, 5), stats, 1.0 / 3, 2.0 / 7, Math.E, 3, new SeasonWeek(2026, 4));

        db.transaction.executeWithoutResult(status -> new ProjectionRepository(db.jdbc).replaceOpen(
                new SeasonWeek(2026, 5), "weighted-average-v1",
                List.of(new ProjectionRepository.ProjectedRow(player, game, team, projection)), KICKOFF));

        Map<String, Object> row = db.jdbc.queryForMap("SELECT * FROM player_week_projection");
        for (StatKey stat : StatKey.values()) {
            assertThat(Double.doubleToLongBits((Double) row.get(stat.json()))).as(stat.json())
                    .isEqualTo(Double.doubleToLongBits(stats[stat.index()]));
        }
        assertThat((Double) row.get("pass_att")).isEqualTo(1.0 / 3);
        assertThat((Double) row.get("rush_att")).isEqualTo(2.0 / 7);
        assertThat((Double) row.get("targets")).isEqualTo(Math.E);
    }

    /** The record of what was projected before kickoff: no UPDATE, no DELETE, from anywhere. */
    @Test
    void aLockedRowRefusesBothUpdateAndDelete() {
        insertRow(KICKOFF);

        assertThatThrownBy(() -> db.jdbc.update("UPDATE player_week_projection SET rec = 99"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("never rewritten");
        assertThatThrownBy(() -> db.jdbc.update("DELETE FROM player_week_projection"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("never rewritten");
        assertThatThrownBy(() -> db.jdbc.update("UPDATE player_week_projection SET locked_at = NULL"))
                .as("unlocking it is a rewrite too")
                .isInstanceOf(DataAccessException.class);

        assertThat(db.jdbc.queryForObject("SELECT rec FROM player_week_projection", Double.class))
                .isEqualTo(4.0);
    }

    /** The trigger fires on locked rows only: the job must still be able to replace open ones. */
    @Test
    void anOpenRowCanBeChangedLockedAndDeleted() {
        insertRow(null);

        assertThat(db.jdbc.update("UPDATE player_week_projection SET rec = 5")).isOne();
        assertThat(db.jdbc.update("UPDATE player_week_projection SET locked_at = ?",
                KICKOFF.atOffset(java.time.ZoneOffset.UTC))).as("locking is allowed").isOne();

        db.reset();
        seed();
        insertRow(null);
        assertThat(db.jdbc.update("DELETE FROM player_week_projection")).isOne();
    }

    @Test
    void aProjectionClaimingAnUndefinedMethodIsRefused() {
        assertThatThrownBy(() -> db.jdbc.update(INSERT_ROW, "WeightedAverage", player, game, team, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** An appearance is a game with an offensive snap; zero snaps is not one. */
    @Test
    void anAppearanceWithNoOffensiveSnapIsRefused() {
        assertThatThrownBy(() -> db.appearance(player, game, team, 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        db.appearance(player, game, team, 1);
        assertThatThrownBy(() -> db.appearance(player, game, team, 30))
                .as("one appearance per player per game")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- repairing a locked row: deliberate, one row, stated, kept -----------------------

    /**
     * Genuine corruption -- a projection for a game that never existed -- can be repaired without
     * truncating the table: one row, in one transaction, with a stated reason, and the row as it
     * stood kept in player_week_projection_repairs.
     */
    @Test
    void anOperatorCanRepairOneLockedRowWithAStatedReason() {
        insertRow(KICKOFF);
        long other = db.player("Another Receiver", "WR", team);
        db.jdbc.update(INSERT_ROW, "weighted-average-v1", other, game, team,
                KICKOFF.atOffset(java.time.ZoneOffset.UTC));

        int deleted = db.transaction.execute(status -> {
            db.jdbc.execute("SET LOCAL fantasykai.projection_repair_reason = "
                    + "'game never played: delisted upstream before kickoff (test)'");
            return db.jdbc.update("DELETE FROM player_week_projection WHERE player_id = ?", player);
        });

        assertThat(deleted).isOne();
        assertThat(db.jdbc.queryForList("SELECT player_id FROM player_week_projection", Long.class))
                .as("only the row named").containsExactly(other);
        Map<String, Object> repair = db.jdbc.queryForMap("""
                SELECT operation, reason, old_row ->> 'player_id' AS player, old_row ->> 'rec' AS rec,
                       old_row ->> 'locked_at' IS NOT NULL AS was_locked
                  FROM player_week_projection_repairs
                """);
        assertThat(repair).containsEntry("operation", "DELETE")
                .containsEntry("reason", "game never played: delisted upstream before kickoff (test)")
                .containsEntry("player", String.valueOf(player))
                .containsEntry("rec", "4")
                .containsEntry("was_locked", true);
    }

    @Test
    void aRepairedUpdateIsKeptAsWhatItReplaced() {
        insertRow(KICKOFF);

        db.transaction.executeWithoutResult(status -> {
            db.jdbc.execute("SET LOCAL fantasykai.projection_repair_reason = 'corrected a mis-assigned game (test)'");
            db.jdbc.update("UPDATE player_week_projection SET rec = 5");
        });

        assertThat(db.jdbc.queryForObject("SELECT rec FROM player_week_projection", Double.class)).isEqualTo(5.0);
        assertThat(db.jdbc.queryForMap("SELECT operation, old_row ->> 'rec' AS rec FROM player_week_projection_repairs"))
                .containsEntry("operation", "UPDATE").containsEntry("rec", "4");
    }

    /** The reason lasts one transaction on one connection; the next statement is refused again. */
    @Test
    void theRepairReasonEndsWithItsTransaction() {
        insertRow(KICKOFF);

        boolean refusedAfterward = db.jdbc.execute((java.sql.Connection connection) -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SET LOCAL fantasykai.projection_repair_reason = 'a transaction that only reads (test)'");
                connection.commit();
                connection.setAutoCommit(true);
                statement.executeUpdate("DELETE FROM player_week_projection");
                return false;
            } catch (java.sql.SQLException e) {
                return e.getMessage().contains("never rewritten");
            } finally {
                connection.setAutoCommit(true);
            }
        });

        assertThat(refusedAfterward).isTrue();
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM player_week_projection", Long.class)).isOne();
    }

    /** A reason has to be one: a few characters is refused, and nothing changes. */
    @Test
    void aRepairWithoutARealReasonIsRefused() {
        insertRow(KICKOFF);

        assertThatThrownBy(() -> db.transaction.executeWithoutResult(status -> {
            db.jdbc.execute("SET LOCAL fantasykai.projection_repair_reason = 'oops'");
            db.jdbc.update("DELETE FROM player_week_projection");
        })).isInstanceOf(DataAccessException.class);

        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM player_week_projection", Long.class)).isOne();
    }

    /** "Normal application code never rewrites a locked projection": none of it can ask to. */
    @Test
    void noApplicationCodeSetsTheRepairReason() throws java.io.IOException {
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) {
            assertThat(files.filter(path -> path.toString().endsWith(".java")).filter(path -> {
                try {
                    return java.nio.file.Files.readString(path).contains("projection_repair_reason");
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).toList()).isEmpty();
        }
    }

    // --- the provider id: one limit, held by the domain and the column ------------------

    @Test
    void theProviderColumnIsExactlyAsWideAsTheLongestLegalSourceId() {
        assertThat(db.jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                 WHERE table_name = 'player_week_projection' AND column_name = 'provider'
                """, Integer.class)).isEqualTo(ProjectionSource.MAX_ID_LENGTH);
    }

    /** The longest id the domain accepts is one the table stores. */
    @Test
    void aSourceIdOfExactlyTheMaximumLengthIsStored() {
        ProjectionSource longest = new ProjectionSource(
                "a".repeat(ProjectionSource.MAX_ID_LENGTH - 3) + "-v1", "Longest", "An id at the limit.");
        double[] stats = new double[StatKey.COUNT];
        RawProjection projection = new RawProjection(longest, new SeasonWeek(2026, 5), stats, 0, 0, 0, 1,
                new SeasonWeek(2026, 4));

        db.transaction.executeWithoutResult(status -> new ProjectionRepository(db.jdbc).replaceOpen(
                new SeasonWeek(2026, 5), longest.id(),
                List.of(new ProjectionRepository.ProjectedRow(player, game, team, projection)), KICKOFF));

        assertThat(db.jdbc.queryForObject("SELECT length(provider) FROM player_week_projection", Integer.class))
                .isEqualTo(ProjectionSource.MAX_ID_LENGTH);
    }

    private static final String INSERT_ROW = """
            INSERT INTO player_week_projection (season, week, provider, player_id, game_id, team_id,
                %s, pass_att, rush_att, targets, games_used, data_through_season, data_through_week,
                generated_at, locked_at)
            VALUES (2026, 5, ?, ?, ?, ?, %s, 0, 0, 0, 1, 2026, 4, now(), ?)
            """.formatted(
                    Arrays.stream(StatKey.values()).map(StatKey::json).collect(Collectors.joining(", ")),
                    Arrays.stream(StatKey.values()).map(stat -> stat == StatKey.REC ? "4" : "0")
                            .collect(Collectors.joining(", ")));

    private void insertRow(Instant lockedAt) {
        db.jdbc.update(INSERT_ROW, "weighted-average-v1", player, game, team,
                Optional.ofNullable(lockedAt).map(at -> at.atOffset(java.time.ZoneOffset.UTC)).orElse(null));
    }

    private Map<String, String> columnTypes(String table) {
        return db.jdbc.query("""
                SELECT column_name, data_type FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = ?
                """, rs -> {
            Map<String, String> types = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                types.put(rs.getString(1), rs.getString(2));
            }
            return types;
        }, table);
    }
}
