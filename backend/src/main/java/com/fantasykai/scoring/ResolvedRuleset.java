package com.fantasykai.scoring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link Ruleset} compiled into the form the evaluator actually wants.
 *
 * <p>Resolving position overrides once, at load, turns scoring a stat line into
 * a dot-product over two arrays. The naive alternative -- consult the override
 * map, fall back to base, per stat, per player, per request -- is a hash lookup
 * for every one of the 13 stats on every row, and §9 is explicit that the
 * rankings endpoint is CPU-bound rather than I/O-bound. This is the cheap half
 * of that fix; the cache is the other half.
 */
public final class ResolvedRuleset {

    private final double[] baseRates;
    private final Map<String, double[]> byPosition;
    private final List<Bonus> bonuses;
    private final String hash;

    private ResolvedRuleset(double[] baseRates, Map<String, double[]> byPosition,
            List<Bonus> bonuses, String hash) {
        this.baseRates = baseRates;
        this.byPosition = byPosition;
        this.bonuses = bonuses;
        this.hash = hash;
    }

    /**
     * Dispatch on the rule format version, deliberately, while only one exists.
     *
     * <p>§6: when K and DST land, the format becomes version 2 with a tiers
     * block and the evaluator branches here -- on the version field, never on
     * which keys happen to be present. Profiles stored under version 1 keep
     * evaluating down the v1 path, so no stored profile silently changes
     * meaning and no user data needs migrating. Retrofitting this after people
     * have saved profiles is where it gets expensive, so it goes in now.
     */
    public static ResolvedRuleset compile(Ruleset ruleset) {
        return switch (ruleset.version()) {
            case 1 -> compileV1(ruleset);
            default -> throw new InvalidRulesetException(
                    "unsupported ruleset version " + ruleset.version()
                            + "; this build understands up to " + Ruleset.CURRENT_VERSION);
        };
    }

    private static ResolvedRuleset compileV1(Ruleset ruleset) {
        double[] baseRates = rates(ruleset.base());

        Map<String, double[]> byPosition = new HashMap<>();
        ruleset.positionOverrides().forEach((position, overrides) -> {
            // An override replaces individual rates, not the whole rate table:
            // TE premium says receptions are worth 1.5, not that a TE scores
            // nothing for a touchdown.
            double[] resolved = baseRates.clone();
            overrides.forEach((stat, rate) -> resolved[stat.index()] = rate);
            byPosition.put(position, resolved);
        });

        return new ResolvedRuleset(baseRates, Map.copyOf(byPosition),
                ruleset.bonuses(), ruleset.canonicalHash());
    }

    private static double[] rates(Map<StatKey, Double> from) {
        double[] rates = new double[StatKey.COUNT];
        from.forEach((stat, rate) -> rates[stat.index()] = rate);
        return rates;
    }

    /** Rates for a position, falling back to base where no override exists. */
    public double[] ratesFor(String position) {
        double[] override = position == null ? null : byPosition.get(position);
        return override != null ? override : baseRates;
    }

    public List<Bonus> bonuses() {
        return bonuses;
    }

    /** The §9 cache key component. Stable across equivalent rulesets. */
    public String hash() {
        return hash;
    }
}
