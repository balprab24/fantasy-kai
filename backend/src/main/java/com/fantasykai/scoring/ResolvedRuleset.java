package com.fantasykai.scoring;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A {@link Ruleset} compiled into the form the evaluator actually wants.
 *
 * <p>Resolving position overrides once, at load, turns scoring a stat line into
 * a dot-product over two arrays. The naive alternative -- consult the override
 * map, fall back to base, per stat, per player, per request -- is a hash lookup
 * for every one of the 13 stats on every row, and §9 is explicit that the
 * rankings endpoint is CPU-bound rather than I/O-bound. This is the cheap half
 * of that fix; the cache is the other half.
 *
 * <p>The canonical hash is computed <em>here</em>, over the compiled arrays,
 * and that placement is the whole point -- see {@link #canonicalHash}.
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
            // An override that reproduces the base rate is not an override. It
            // scores identically to having none, so it must not survive into
            // the hash as though it were different.
            if (!Arrays.equals(resolved, baseRates)) {
                byPosition.put(position, resolved);
            }
        });

        // A zero-point bonus adds zero. Same reasoning: identical to absent.
        List<Bonus> bonuses = ruleset.bonuses().stream()
                .filter(bonus -> bonus.points() != 0)
                .toList();

        return new ResolvedRuleset(baseRates, Map.copyOf(byPosition), bonuses,
                canonicalHash(ruleset.version(), baseRates, byPosition, bonuses));
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

    /**
     * SHA-256 over a canonical form, and the reason §9's cache key works.
     *
     * <p>The cache is keyed on a hash of the <em>rules</em> rather than the
     * profile id, so two users whose leagues happen to score identically share
     * one entry and the four presets collapse to four entries no matter how
     * many users exist. That only holds if logically identical rulesets hash
     * the same.
     *
     * <p><strong>Which is why this hashes the compiled arrays and not the JSON
     * the user wrote.</strong> It used to hash the authored form, and three
     * pairs of rulesets that score identically hashed differently:
     *
     * <ul>
     *   <li>{@code base: {rec_td: 6}} against {@code base: {rec_td: 6, rec: 0}}
     *       -- an absent rate and an explicit zero are the same array slot, and
     *       {@code RulesetValidator} requires only that base is non-empty, so
     *       both are legal</li>
     *   <li>a position override that repeats the base rate, against no override</li>
     *   <li>an empty override block, against no override block</li>
     * </ul>
     *
     * <p>Enumerating those cases in the serializer would fix the three that
     * were found. Hashing the resolved form makes every one of them collapse by
     * construction, including the ones nobody has thought of -- two rulesets
     * hash the same exactly when they score the same, because the arrays are
     * literally what scoring reads. An explicit override of {@code 0} stays
     * correctly <em>distinct</em> from no override, with no special case: zero
     * is not the base rate.
     *
     * <p>Canonical means the serialization carries nothing from how the JSON
     * happened to be written: fixed stat order, one spelling per number, and
     * zero rates omitted rather than spelled out -- omission is injective here,
     * since every slot this skips is zero in both arrays being compared.
     */
    private static String canonicalHash(int version, double[] baseRates,
            Map<String, double[]> byPosition, List<Bonus> bonuses) {
        StringBuilder canonical = new StringBuilder("v").append(version);

        canonical.append("|base:");
        appendRates(canonical, baseRates);

        canonical.append("|pos:");
        new TreeMap<>(byPosition).forEach((position, rates) -> {
            canonical.append(position).append('{');
            appendRates(canonical, rates);
            canonical.append('}');
        });

        // Not deduplicated: two identical bonuses award twice, so they are two.
        canonical.append("|bonus:");
        bonuses.stream()
                .sorted(Comparator.comparing((Bonus b) -> b.stat().json())
                        .thenComparingInt(Bonus::gte)
                        .thenComparingDouble(Bonus::points))
                .forEach(b -> canonical.append(b.stat().json()).append(">=").append(b.gte())
                        .append(':').append(number(b.points())).append(','));

        return sha256(canonical.toString());
    }

    private static void appendRates(StringBuilder out, double[] rates) {
        for (StatKey stat : StatKey.values()) {
            double rate = rates[stat.index()];
            if (rate != 0) {
                out.append(stat.json()).append('=').append(number(rate)).append(',');
            }
        }
    }

    /** One spelling per value, so 4, 4.0 and 4.00 cannot hash differently. */
    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
