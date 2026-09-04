package com.fantasykai.scoring;

import java.util.EnumMap;
import java.util.Map;

/**
 * One player's raw stats for one game, in the order {@link StatKey} defines.
 *
 * <p>Raw stats only, never points -- points depend on a ruleset this type knows
 * nothing about, which is the whole architecture in one sentence.
 *
 * <p>{@code values} is an array, so the record's generated equals and hashCode
 * compare it by identity. That is fine here: a stat line is scored and
 * discarded, never used as a map key.
 *
 * @param position the scoring position, which selects any override
 * @param values   stat values indexed by {@link StatKey#index()}
 */
public record StatLine(String position, double[] values) {

    public StatLine {
        if (values.length != StatKey.COUNT) {
            throw new IllegalArgumentException(
                    "expected " + StatKey.COUNT + " stats, got " + values.length);
        }
    }

    /** Convenience for tests and for anything holding stats keyed rather than positional. */
    public static StatLine of(String position, Map<StatKey, ? extends Number> stats) {
        double[] values = new double[StatKey.COUNT];
        stats.forEach((stat, value) -> values[stat.index()] = value.doubleValue());
        return new StatLine(position, values);
    }

    public double get(StatKey stat) {
        return values[stat.index()];
    }

    public Map<StatKey, Double> asMap() {
        Map<StatKey, Double> map = new EnumMap<>(StatKey.class);
        for (StatKey stat : StatKey.values()) {
            map.put(stat, values[stat.index()]);
        }
        return map;
    }
}
