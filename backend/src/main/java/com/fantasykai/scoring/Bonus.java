package com.fantasykai.scoring;

/**
 * A threshold bonus: award {@code points} once if the stat reaches {@code gte}.
 *
 * <p>All-or-nothing by design, which is how real leagues write it -- "3 points
 * for a 100-yard rushing game", not 3 points per 100 yards.
 */
public record Bonus(StatKey stat, int gte, double points) {}
