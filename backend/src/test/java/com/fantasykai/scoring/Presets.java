package com.fantasykai.scoring;

import java.util.List;
import java.util.Map;

/**
 * The four presets, built in code.
 *
 * <p>These are a deliberate duplicate of what {@code V3__seed_scoring_presets.sql}
 * seeds, so the pure tests need no database. The duplication is safe because
 * {@link ScoringProfileTests} asserts the seeded rows and these compile to the
 * same canonical hash -- if the migration and this file ever drift, that test
 * fails and names which preset moved.
 */
final class Presets {

    private Presets() {}

    static final Map<StatKey, Double> BASE = Map.ofEntries(
            Map.entry(StatKey.PASS_YD, 0.04),
            Map.entry(StatKey.PASS_TD, 4.0),
            Map.entry(StatKey.PASS_INT, -2.0),
            Map.entry(StatKey.PASS_2PT, 2.0),
            Map.entry(StatKey.RUSH_YD, 0.1),
            Map.entry(StatKey.RUSH_TD, 6.0),
            Map.entry(StatKey.RUSH_2PT, 2.0),
            Map.entry(StatKey.REC, 0.0),
            Map.entry(StatKey.REC_YD, 0.1),
            Map.entry(StatKey.REC_TD, 6.0),
            Map.entry(StatKey.REC_2PT, 2.0),
            Map.entry(StatKey.FUM_LOST, -2.0),
            Map.entry(StatKey.RET_TD, 6.0));

    static Ruleset standard() {
        return withReceptionRate(0.0);
    }

    static Ruleset halfPpr() {
        return withReceptionRate(0.5);
    }

    static Ruleset fullPpr() {
        return withReceptionRate(1.0);
    }

    static Ruleset tePremium() {
        return new Ruleset(1, ratesWith(1.0),
                Map.of("TE", Map.of(StatKey.REC, 1.5)), List.of());
    }

    private static Ruleset withReceptionRate(double rate) {
        return new Ruleset(1, ratesWith(rate), Map.of(), List.of());
    }

    private static Map<StatKey, Double> ratesWith(double receptionRate) {
        var rates = new java.util.EnumMap<StatKey, Double>(StatKey.class);
        rates.putAll(BASE);
        rates.put(StatKey.REC, receptionRate);
        return rates;
    }
}
