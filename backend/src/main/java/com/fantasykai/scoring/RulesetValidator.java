package com.fantasykai.scoring;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Bounds a ruleset before it is stored or evaluated.
 *
 * <p>§8 names an unvalidated JSONB column as both an injection surface and a
 * data-quality bomb. The stat allowlist lives in {@link StatKey} and is enforced
 * during parsing; what is left is range: a rate of 1e9 is not a scoring rule, it
 * is a way to produce Infinity in a ranking, and a caller who can add unbounded
 * bonuses can make the evaluator arbitrarily expensive.
 *
 * <p>Rejections are loud. A ruleset that silently clamps is worse than one that
 * fails, because the user then has a profile that does not mean what they wrote.
 */
@Component
public class RulesetValidator {

    static final double MAX_RATE = 10.0;
    static final double MAX_BONUS_POINTS = 20.0;
    static final int MAX_BONUSES = 20;
    static final int MAX_THRESHOLD = 1000;

    public Ruleset validate(Ruleset ruleset) {
        if (ruleset.version() < 1 || ruleset.version() > Ruleset.CURRENT_VERSION) {
            throw new InvalidRulesetException("unsupported ruleset version " + ruleset.version()
                    + "; this build understands up to " + Ruleset.CURRENT_VERSION);
        }
        if (ruleset.base().isEmpty()) {
            throw new InvalidRulesetException("a ruleset needs at least one rate in \"base\"");
        }

        checkRates(ruleset.base(), "base");
        ruleset.positionOverrides().forEach(
                (position, rates) -> checkRates(rates, "position_overrides." + position));

        if (ruleset.bonuses().size() > MAX_BONUSES) {
            throw new InvalidRulesetException("at most " + MAX_BONUSES + " bonuses, got "
                    + ruleset.bonuses().size());
        }
        for (Bonus bonus : ruleset.bonuses()) {
            if (bonus.gte() < 0 || bonus.gte() > MAX_THRESHOLD) {
                throw new InvalidRulesetException("bonus threshold for " + bonus.stat().json()
                        + " must be between 0 and " + MAX_THRESHOLD + ", got " + bonus.gte());
            }
            if (!finiteWithin(bonus.points(), MAX_BONUS_POINTS)) {
                throw new InvalidRulesetException("bonus points for " + bonus.stat().json()
                        + " must be between " + -MAX_BONUS_POINTS + " and " + MAX_BONUS_POINTS
                        + ", got " + bonus.points());
            }
        }
        return ruleset;
    }

    private static void checkRates(Map<StatKey, Double> rates, String where) {
        rates.forEach((stat, rate) -> {
            if (!finiteWithin(rate, MAX_RATE)) {
                throw new InvalidRulesetException(where + "." + stat.json() + " must be between "
                        + -MAX_RATE + " and " + MAX_RATE + ", got " + rate);
            }
        });
    }

    private static boolean finiteWithin(double value, double bound) {
        return Double.isFinite(value) && Math.abs(value) <= bound;
    }
}
