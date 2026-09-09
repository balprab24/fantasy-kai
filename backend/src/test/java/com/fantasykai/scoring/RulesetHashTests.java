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
 * model, testable now, rather than something to discover in Phase 11.
 *
 * <p>The four cases below the first block are the ones that used to fail. The
 * hash was computed over the JSON someone wrote rather than over what it
 * compiles to, so a rate that was absent and a rate that was explicitly zero --
 * indistinguishable to the scorer, since both are the same slot in the same
 * array -- produced different cache keys. Each of these pairs asserts the same
 * thing twice: that the two rulesets score identically, and that they hash
 * identically. The first is the invariant; the second is what the cache needs
 * in order not to break it.
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

    /**
     * The case that broke it. Both are valid -- RulesetValidator requires only
     * that base is non-empty -- both score every stat line to the same number,
     * and they used to hash differently.
     */
    @Test
    void anAbsentRateAndAnExplicitZeroHashTheSame() {
        Ruleset absent = json.read("{ \"version\": 1, \"base\": { \"rec_td\": 6 } }");
        Ruleset explicit = json.read(
                "{ \"version\": 1, \"base\": { \"rec_td\": 6, \"rec\": 0 } }");

        assertThatTheyScoreTheSame(absent, explicit);
        assertThat(explicit.canonicalHash()).isEqualTo(absent.canonicalHash());
    }

    /** An override that repeats the base rate is not an override. */
    @Test
    void anOverrideRepeatingTheBaseRateHashesTheSameAsNoOverride() {
        Ruleset plain = json.read("{ \"version\": 1, \"base\": { \"rec\": 1 } }");
        Ruleset redundant = json.read("""
                { "version": 1, "base": { "rec": 1 },
                  "position_overrides": { "TE": { "rec": 1 } } }
                """);

        assertThatTheyScoreTheSame(plain, redundant);
        assertThat(redundant.canonicalHash()).isEqualTo(plain.canonicalHash());
    }

    @Test
    void anEmptyOverrideBlockHashesTheSameAsNoOverrideBlock() {
        Ruleset plain = json.read("{ \"version\": 1, \"base\": { \"rec\": 1 } }");
        Ruleset empty = json.read("""
                { "version": 1, "base": { "rec": 1 }, "position_overrides": { "TE": {} } }
                """);

        assertThatTheyScoreTheSame(plain, empty);
        assertThat(empty.canonicalHash()).isEqualTo(plain.canonicalHash());
    }

    @Test
    void aZeroPointBonusHashesTheSameAsNoBonus() {
        Ruleset plain = json.read("{ \"version\": 1, \"base\": { \"rec\": 1 } }");
        Ruleset pointless = json.read("""
                { "version": 1, "base": { "rec": 1 },
                  "bonuses": [ { "stat": "rec_yd", "gte": 100, "points": 0 } ] }
                """);

        assertThatTheyScoreTheSame(plain, pointless);
        assertThat(pointless.canonicalHash()).isEqualTo(plain.canonicalHash());
    }

    /**
     * The other direction, and the reason the fix cannot just be "drop zeros".
     * An override TO zero is a real rule -- a TE who scores nothing per
     * reception in a league where everyone else scores one -- and it must stay
     * distinct from having no override at all.
     */
    @Test
    void anOverrideToZeroIsStillDistinctFromNoOverride() {
        Ruleset plain = json.read("{ \"version\": 1, \"base\": { \"rec\": 1 } }");
        Ruleset teScoresNothing = json.read("""
                { "version": 1, "base": { "rec": 1 },
                  "position_overrides": { "TE": { "rec": 0 } } }
                """);

        StatLine te = lineWith(StatKey.REC, 5, "TE");
        assertThat(ScoringEngine.score(te, ResolvedRuleset.compile(teScoresNothing)))
                .as("the override is what makes this zero rather than five")
                .isZero();
        assertThat(ScoringEngine.score(te, ResolvedRuleset.compile(plain))).isEqualTo(5.0);

        assertThat(teScoresNothing.canonicalHash()).isNotEqualTo(plain.canonicalHash());
    }

    /** Two identical bonuses award twice, so they are two rules, not one. */
    @Test
    void duplicateBonusesAreNotCollapsedIntoOne() {
        Ruleset once = json.read("""
                { "version": 1, "base": { "rec": 1 },
                  "bonuses": [ { "stat": "rec_yd", "gte": 100, "points": 3 } ] }
                """);
        Ruleset twice = json.read("""
                { "version": 1, "base": { "rec": 1 }, "bonuses": [
                  { "stat": "rec_yd", "gte": 100, "points": 3 },
                  { "stat": "rec_yd", "gte": 100, "points": 3 } ] }
                """);

        StatLine hundred = lineWith(StatKey.REC_YD, 100, "WR");
        assertThat(ScoringEngine.score(hundred, ResolvedRuleset.compile(twice)))
                .isEqualTo(ScoringEngine.score(hundred, ResolvedRuleset.compile(once)) + 3);

        assertThat(twice.canonicalHash()).isNotEqualTo(once.canonicalHash());
    }

    /**
     * The property the pairs above are instances of: hashing equal implies
     * scoring equal. Asserted over a stat line that exercises every key, so a
     * collapse that lost a real difference would show up here rather than in
     * whichever pair happened to be written down.
     */
    private void assertThatTheyScoreTheSame(Ruleset left, Ruleset right) {
        ResolvedRuleset a = ResolvedRuleset.compile(left);
        ResolvedRuleset b = ResolvedRuleset.compile(right);
        for (String position : new String[] {"QB", "RB", "WR", "TE"}) {
            StatLine line = everyStatSet(position);
            assertThat(ScoringEngine.score(line, b))
                    .as("as a %s, these must score the same or the hash must not match", position)
                    .isEqualTo(ScoringEngine.score(line, a));
        }
    }

    private static StatLine everyStatSet(String position) {
        double[] values = new double[StatKey.COUNT];
        for (StatKey stat : StatKey.values()) {
            values[stat.index()] = stat.index() + 1;
        }
        return new StatLine(position, values);
    }

    private static StatLine lineWith(StatKey stat, double value, String position) {
        double[] values = new double[StatKey.COUNT];
        values[stat.index()] = value;
        return new StatLine(position, values);
    }
}
