package com.fantasykai.scoring;

/**
 * A threshold bonus: award {@code points} once if the stat reaches {@code gte}.
 *
 * <p>All-or-nothing by design, which is how real leagues write it -- "3 points
 * for a 100-yard rushing game", not 3 points per 100 yards.
 */
public record Bonus(StatKey stat, int gte, double points) {

    /**
     * Whether a line with this value of {@link #stat} earns the bonus: at the threshold, not past
     * it. The one definition of that comparison -- {@link ScoringEngine} pays by it and
     * {@link ExplainedScore} reports by it, so the explanation cannot say "reached" about a bonus
     * the score did not pay.
     */
    public boolean reachedBy(double value) {
        return value >= gte;
    }
}
