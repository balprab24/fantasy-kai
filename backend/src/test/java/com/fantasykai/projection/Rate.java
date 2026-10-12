package com.fantasykai.projection;

import com.fantasykai.scoring.StatKey;

/**
 * An efficiency: one stat per unit of an opportunity, shrunk toward the positional rate.
 *
 * <p>{@code pseudoCount} is how many opportunities of the positional rate a player's own
 * record is blended with -- 30 targets, 100 attempts. These are fixed hypotheses rather
 * than tuned knobs: the model is a ridge regression over the products these feed, so it
 * rescales them anyway, and tuning eight more numbers on one validation season would be
 * fitting noise.
 */
enum Rate {
    CATCH_RATE(Quantity.of(StatKey.REC), Quantity.TARGETS, 30),
    YARDS_PER_TARGET(Quantity.of(StatKey.REC_YD), Quantity.TARGETS, 30),
    REC_TD_RATE(Quantity.of(StatKey.REC_TD), Quantity.TARGETS, 60),
    YARDS_PER_CARRY(Quantity.of(StatKey.RUSH_YD), Quantity.RUSH_ATT, 50),
    RUSH_TD_RATE(Quantity.of(StatKey.RUSH_TD), Quantity.RUSH_ATT, 100),
    YARDS_PER_ATTEMPT(Quantity.of(StatKey.PASS_YD), Quantity.PASS_ATT, 100),
    PASS_TD_RATE(Quantity.of(StatKey.PASS_TD), Quantity.PASS_ATT, 200),
    INT_RATE(Quantity.of(StatKey.PASS_INT), Quantity.PASS_ATT, 300);

    final int numerator;
    final int denominator;
    final double pseudoCount;

    Rate(int numerator, int denominator, double pseudoCount) {
        this.numerator = numerator;
        this.denominator = denominator;
        this.pseudoCount = pseudoCount;
    }

    String feature() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
