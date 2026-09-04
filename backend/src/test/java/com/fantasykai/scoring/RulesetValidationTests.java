package com.fantasykai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * §8: an unvalidated JSONB column is an injection surface and a data-quality
 * bomb. These are the ways a ruleset is refused.
 */
class RulesetValidationTests {

    private final RulesetJson json = new RulesetJson(new ObjectMapper());
    private final RulesetValidator validator = new RulesetValidator();

    private Ruleset parse(String rules) {
        return validator.validate(json.read(rules));
    }

    @Test
    void acceptsTheShapeTheDocSpecifies() {
        Ruleset ruleset = parse("""
                {
                  "version": 1,
                  "base": { "rec": 1.0, "rec_yd": 0.1, "rec_td": 6 },
                  "position_overrides": { "TE": { "rec": 1.5 } },
                  "bonuses": [ { "stat": "rec_yd", "gte": 100, "points": 3 } ]
                }
                """);

        assertThat(ruleset.base()).containsEntry(StatKey.REC, 1.0);
        assertThat(ruleset.positionOverrides()).containsKey("TE");
        assertThat(ruleset.bonuses()).containsExactly(new Bonus(StatKey.REC_YD, 100, 3));
    }

    @Test
    void rejectsAnUnknownStatRatherThanIgnoringIt() {
        // The important half: a typo must not silently score as zero forever.
        assertThatThrownBy(() -> parse("""
                { "version": 1, "base": { "recieving_yards": 0.1 } }
                """))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("recieving_yards");
    }

    @Test
    void rejectsStatsThatAreStoredButNotYetScorable() {
        // Kicking and defensive columns exist in player_game_stats, but the v1
        // rule format cannot express distance buckets or points-allowed tiers.
        assertThatThrownBy(() -> parse("""
                { "version": 1, "base": { "fg_made_40_49": 4 } }
                """))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("fg_made_40_49");
    }

    @Test
    void rejectsAnUnknownTopLevelFieldAndAnUnknownBonusField() {
        assertThatThrownBy(() -> parse("""
                { "version": 1, "base": { "rec": 1 }, "multiplier": 2 }
                """))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("multiplier");

        assertThatThrownBy(() -> parse("""
                { "version": 1, "base": { "rec": 1 },
                  "bonuses": [ { "stat": "rec_yd", "gte": 100, "points": 3, "per": 100 } ] }
                """))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("per");
    }

    @Test
    void boundsRatesSoARankingCannotBeBlownUp() {
        assertThatThrownBy(() -> parse("""
                { "version": 1, "base": { "rec": 1000000 } }
                """))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("base.rec");

        assertThatCode(() -> parse("""
                { "version": 1, "base": { "rec": -10, "rec_td": 10 } }
                """)).doesNotThrowAnyException();
    }

    @Test
    void boundsBonusCountAndThreshold() {
        String twentyOne = "{ \"version\": 1, \"base\": { \"rec\": 1 }, \"bonuses\": ["
                + "{ \"stat\": \"rec_yd\", \"gte\": 100, \"points\": 1 },".repeat(21);
        assertThatThrownBy(() -> parse(twentyOne.substring(0, twentyOne.length() - 1) + "] }"))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("at most 20 bonuses");

        assertThatThrownBy(() -> parse("""
                { "version": 1, "base": { "rec": 1 },
                  "bonuses": [ { "stat": "rec_yd", "gte": -5, "points": 3 } ] }
                """))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void rejectsAnEmptyOrShapelessRuleset() {
        assertThatThrownBy(() -> parse("{ \"version\": 1, \"base\": {} }"))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("at least one rate");

        assertThatThrownBy(() -> parse("{ \"base\": { \"rec\": 1 } }"))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("version");

        assertThatThrownBy(() -> parse("not json"))
                .isInstanceOf(InvalidRulesetException.class);

        assertThatThrownBy(() -> parse("""
                { "version": 1, "base": { "rec": "one" } }
                """))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("must be a number");
    }

    @Test
    void rejectsAFutureVersionInsteadOfGuessingAtIt() {
        // The v2 format adds a tiers block for K and DST. A build that predates
        // it must refuse the profile, not evaluate the half it recognises.
        assertThatThrownBy(() -> parse("""
                { "version": 2, "base": { "rec": 1 } }
                """))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("version 2");

        assertThatThrownBy(() -> ResolvedRuleset.compile(
                new Ruleset(2, Map.of(StatKey.REC, 1.0), Map.of(), List.of())))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("unsupported ruleset version 2");
    }

    @Test
    void rejectsAnUnknownPositionOverride() {
        assertThatThrownBy(() -> parse("""
                { "version": 1, "base": { "rec": 1 }, "position_overrides": { "FLEX": { "rec": 2 } } }
                """))
                .isInstanceOf(InvalidRulesetException.class)
                .hasMessageContaining("FLEX");
    }

    @Test
    void roundTripsThroughJson() {
        Ruleset original = Presets.tePremium();

        assertThat(json.read(json.write(original)).canonicalHash())
                .isEqualTo(original.canonicalHash());
    }
}
