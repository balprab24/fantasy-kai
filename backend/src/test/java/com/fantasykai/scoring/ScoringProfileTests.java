package com.fantasykai.scoring;

import static com.fantasykai.scoring.ScoringEngine.roundForDisplay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The four presets as V3 actually seeds them, read back out of JSONB.
 *
 * <p>{@link ScoringEngineTests} builds its presets in Java, which proves the
 * evaluator but not the migration. This closes that gap: if the SQL and the
 * code ever disagree -- a mistyped rate, a dropped override -- the canonical
 * hashes stop matching and this names the preset that moved.
 */
@Testcontainers
@SpringBootTest
class ScoringProfileTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ScoringProfiles profiles;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v3SeedsExactlyTheFourPresets() {
        assertThat(jdbc.queryForList(
                        "SELECT name FROM scoring_profiles WHERE is_preset ORDER BY id", String.class))
                .containsExactly("Standard", "Half PPR", "Full PPR", "TE Premium");

        // A preset belongs to no user. Phase 5 filters custom profiles on the
        // authenticated user_id; a preset with an owner would vanish from that view.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM scoring_profiles WHERE is_preset AND user_id IS NOT NULL",
                        Long.class))
                .isZero();
    }

    @Test
    void theSeededPresetsAreTheOnesTheCodeExpects() {
        Map<String, Ruleset> expected = Map.of(
                "Standard", Presets.standard(),
                "Half PPR", Presets.halfPpr(),
                "Full PPR", Presets.fullPpr(),
                "TE Premium", Presets.tePremium());

        expected.forEach((name, ruleset) ->
                assertThat(profiles.preset(name).hash())
                        .describedAs("seeded %s differs from the ruleset the tests assert against", name)
                        .isEqualTo(ruleset.canonicalHash()));
    }

    @Test
    void aSeededPresetScoresARealLineTheSameWayTheInMemoryOneDoes() {
        // Juwan Johnson, 2025 week 1: 8 receptions for 76 yards, a tight end.
        StatLine johnson = StatLine.of("TE", Map.of(StatKey.REC, 8, StatKey.REC_YD, 76));

        assertThat(roundForDisplay(ScoringEngine.score(johnson, profiles.preset("Standard"))))
                .isEqualTo(7.6);
        assertThat(roundForDisplay(ScoringEngine.score(johnson, profiles.preset("Half PPR"))))
                .isEqualTo(11.6);
        assertThat(roundForDisplay(ScoringEngine.score(johnson, profiles.preset("Full PPR"))))
                .isEqualTo(15.6);
        assertThat(roundForDisplay(ScoringEngine.score(johnson, profiles.preset("TE Premium"))))
                .isEqualTo(19.6);
    }

    @Test
    void loadsACustomProfileByIdAndCachesTheCompiledForm() {
        Long id = jdbc.queryForObject("""
                INSERT INTO scoring_profiles (user_id, name, is_preset, rules)
                VALUES (NULL, 'Six point passing', FALSE, ?::jsonb)
                RETURNING id
                """, Long.class,
                "{\"version\":1,\"base\":{\"pass_yd\":0.04,\"pass_td\":6,\"rec\":1}}");

        ResolvedRuleset first = profiles.byId(id);
        assertThat(profiles.byId(id)).isSameAs(first);

        StatLine allen = StatLine.of("QB", Map.of(StatKey.PASS_YD, 394, StatKey.PASS_TD, 2));
        assertThat(roundForDisplay(ScoringEngine.score(allen, first))).isEqualTo(27.76);
    }

    @Test
    void refusesToCompileAStoredProfileThatIsNoLongerValid() {
        // Rows can be written by a migration, a fixture, or a psql session. A
        // profile is re-validated on load rather than trusted because it is
        // already in the table.
        Long id = jdbc.queryForObject("""
                INSERT INTO scoring_profiles (user_id, name, is_preset, rules)
                VALUES (NULL, 'Hand edited', FALSE, ?::jsonb)
                RETURNING id
                """, Long.class, "{\"version\":1,\"base\":{\"rec\":9999}}");

        assertThatThrownBy(() -> profiles.byId(id))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("base.rec");
    }

    @Test
    void reportsAMissingProfileClearly() {
        assertThatThrownBy(() -> profiles.byId(-1))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("no scoring profile with id -1");
        assertThatThrownBy(() -> profiles.preset("Quarter PPR"))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("Quarter PPR");
    }

    @Test
    void theRankingsTableStillCarriesNothingButItsPrimaryKey() {
        // V3 seeds data only. The section 9 baseline has to survive every
        // migration until the Phase 6 performance pass deliberately breaks it.
        assertThat(jdbc.queryForList(
                        "SELECT indexname FROM pg_indexes WHERE tablename = 'player_game_stats'",
                        String.class))
                .containsExactly("pk_player_game_stats");
    }

    @Test
    void everyScorableStatKeyIsARealStatColumn() {
        List<String> columns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_name = 'player_game_stats'
                """, String.class);

        // StatKey names double as column names, which is what lets the Phase 3
        // rankings query be generated from the enum instead of hand-written.
        assertThat(columns).contains(java.util.Arrays.stream(StatKey.values())
                .map(StatKey::json).toArray(String[]::new));
    }
}
