package com.fantasykai.scoring;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A league's scoring rules, as stored in {@code scoring_profiles.rules}.
 *
 * <p>Standard is {@code rec: 0}. Half PPR is {@code 0.5}. Full PPR is
 * {@code 1.0}. TE Premium is full PPR plus a {@code TE} override. Four presets,
 * no branching.
 *
 * @param version            the rule format, not the profile. Only 1 exists; see §6
 * @param base               rate per unit of each stat; absent means zero
 * @param positionOverrides  position to the rates that replace the base for it
 * @param bonuses            threshold bonuses, applied after the dot-product
 */
public record Ruleset(
        int version,
        Map<StatKey, Double> base,
        Map<String, Map<StatKey, Double>> positionOverrides,
        List<Bonus> bonuses) {

    public static final int CURRENT_VERSION = 1;

    public Ruleset {
        base = Map.copyOf(base);
        positionOverrides = Map.copyOf(positionOverrides);
        bonuses = List.copyOf(bonuses);
    }

    /**
     * SHA-256 over a canonical form, and the reason §9's cache key works.
     *
     * <p>The cache is keyed on a hash of the <em>rules</em> rather than the
     * profile id, so two users whose leagues happen to score identically share
     * one entry and the four presets collapse to four entries no matter how many
     * users exist. That only holds if logically identical rulesets hash the
     * same, which means the serialization has to be canonical: keys sorted,
     * numbers in one normalized form, nothing carried over from how the JSON
     * happened to be written.
     */
    public String canonicalHash() {
        StringBuilder canonical = new StringBuilder("v").append(version);

        canonical.append("|base:");
        appendRates(canonical, base);

        canonical.append("|pos:");
        new TreeMap<>(positionOverrides).forEach((position, rates) -> {
            canonical.append(position).append('{');
            appendRates(canonical, rates);
            canonical.append('}');
        });

        canonical.append("|bonus:");
        bonuses.stream()
                .sorted(Comparator.comparing((Bonus b) -> b.stat().json())
                        .thenComparingInt(Bonus::gte)
                        .thenComparingDouble(Bonus::points))
                .forEach(b -> canonical.append(b.stat().json()).append(">=").append(b.gte())
                        .append(':').append(number(b.points())).append(','));

        return sha256(canonical.toString());
    }

    private static void appendRates(StringBuilder out, Map<StatKey, Double> rates) {
        rates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.append(e.getKey().json()).append('=')
                        .append(number(e.getValue())).append(','));
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
