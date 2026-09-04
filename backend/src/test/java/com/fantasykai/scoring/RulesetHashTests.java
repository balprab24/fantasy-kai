package com.fantasykai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * The property §9's cache key rests on.
 *
 * <p>Hashing the ruleset rather than the profile id is what lets two users with
 * identical league settings share one cache entry, and what collapses the four
 * presets to four entries no matter how many users exist. That only works if
 * logically identical rulesets hash identically -- so it is a property of the
 * model, testable now, rather than something to discover in Phase 6.
 */
class RulesetHashTests {

    private final RulesetJson json = new RulesetJson(new ObjectMapper());

    @Test
    void keyOrderAndNumberSpellingDoNotChangeTheHash() {
        String written = """
                { "version": 1, "base": { "rec": 1.0, "rec_yd": 0.1, "rec_td": 6 } }
                """;
        String sameRulesDifferentText = """
                { "base": { "rec_td": 6.00, "rec_yd": 0.1, "rec": 1 }, "version": 1 }
                """;

        assertThat(json.read(sameRulesDifferentText).canonicalHash())
                .isEqualTo(json.read(written).canonicalHash());
    }

    @Test
    void bonusOrderDoesNotChangeTheHash() {
        String oneWay = """
                { "version": 1, "base": { "rec": 1 }, "bonuses": [
                  { "stat": "rush_yd", "gte": 100, "points": 3 },
                  { "stat": "rec_yd", "gte": 100, "points": 3 } ] }
                """;
        String theOther = """
                { "version": 1, "base": { "rec": 1 }, "bonuses": [
                  { "stat": "rec_yd", "gte": 100, "points": 3 },
                  { "stat": "rush_yd", "gte": 100, "points": 3 } ] }
                """;

        assertThat(json.read(theOther).canonicalHash()).isEqualTo(json.read(oneWay).canonicalHash());
    }

    @Test
    void aRuleThatChangesScoringChangesTheHash() {
        assertThat(Presets.halfPpr().canonicalHash())
                .isNotEqualTo(Presets.fullPpr().canonicalHash());

        // TE Premium differs from full PPR only by the override block. If the
        // hash ignored position_overrides, the two would share a cache entry and
        // every TE would be scored wrong for half the users.
        assertThat(Presets.tePremium().canonicalHash())
                .isNotEqualTo(Presets.fullPpr().canonicalHash());

        // ... and so must a bonus, which lives outside base entirely.
        assertThat(json.read("""
                { "version": 1, "base": { "rec": 1 },
                  "bonuses": [ { "stat": "rec_yd", "gte": 100, "points": 3 } ] }
                """).canonicalHash())
                .isNotEqualTo(json.read("{ \"version\": 1, \"base\": { \"rec\": 1 } }")
                        .canonicalHash());
    }

    @Test
    void theHashIsAStableHexDigestSuitableForACacheKey() {
        String hash = Presets.fullPpr().canonicalHash();

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).isEqualTo(Presets.fullPpr().canonicalHash());
    }
}
