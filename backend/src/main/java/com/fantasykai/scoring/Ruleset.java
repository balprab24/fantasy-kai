package com.fantasykai.scoring;

import java.util.List;
import java.util.Map;

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
     * The §9 cache key for these rules.
     *
     * <p>Delegates rather than serializing this record, and that is deliberate.
     * The hash has to be a property of what the ruleset <em>does</em>, not of
     * the JSON someone wrote: {@code {"rec_td": 6}} and
     * {@code {"rec_td": 6, "rec": 0}} score every stat line identically, and a
     * hash computed here -- over the authored maps -- gave them different keys,
     * which is exactly the collision the cache exists to avoid. Compiling first
     * makes the two indistinguishable because the compiled arrays are the same
     * arrays. See {@code ResolvedRuleset.canonicalHash} for the full reasoning
     * and the other two shapes it collapses.
     *
     * <p>If you are here to make this cheaper by hashing the fields directly,
     * that is the change that broke it.
     */
    public String canonicalHash() {
        return ResolvedRuleset.compile(this).hash();
    }
}
