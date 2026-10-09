package com.fantasykai.projection;

import com.fantasykai.scoring.StatKey;

/**
 * What a projection carries: the 13 scorable stats at their {@link StatKey#index()}, then
 * three usage counts the model is built on.
 *
 * <p>Usage is projected but never scored -- no ruleset can name it, because it is not on
 * the {@link StatKey} allowlist -- and it is the part of a projection that explains the
 * rest. {@code pass_cmp} is stored but left out: nothing scores it.
 */
final class Quantity {

    static final int PASS_ATT = StatKey.COUNT;
    static final int RUSH_ATT = StatKey.COUNT + 1;
    static final int TARGETS = StatKey.COUNT + 2;

    /** Width of every projected or actual line in this package. */
    static final int COUNT = StatKey.COUNT + 3;

    private static final String[] USAGE = {"pass_att", "rush_att", "targets"};

    private Quantity() {}

    static int of(StatKey stat) {
        return stat.index();
    }

    /** The stat's column name in {@code player_game_stats}, which is also its ruleset key. */
    static String name(int q) {
        return q < StatKey.COUNT ? StatKey.values()[q].json() : USAGE[q - StatKey.COUNT];
    }

    /** The scorable prefix of a line, which is what {@code ScoringEngine} reads. */
    static double[] scorable(double[] line) {
        return java.util.Arrays.copyOf(line, StatKey.COUNT);
    }
}
