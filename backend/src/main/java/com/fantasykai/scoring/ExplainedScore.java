package com.fantasykai.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * A scored line with its parts: each stat's value x rate = points, and each threshold bonus that
 * bears on the line, whether or not it reached it. "Why is he ranked here" is the product
 * (architecture.md: every ranked number carries its parts), so a number that reaches a member
 * travels as this, not as a bare double.
 *
 * <p>The total is {@link ScoringEngine#score} itself, never a re-derivation, so the explanation
 * and the number cannot disagree. Adding the parts' points in order and then the points of the
 * bonuses that were reached, in order, is the engine's own sum and gives its bits exactly
 * ({@code ExplainedScoreTests} holds that over 300 fractional lines): nothing is left over to be
 * called "other". Unrounded: rounding is the API boundary's, once.
 *
 * @param total   the scored line
 * @param parts   every stat with a value or a rate, in {@link StatKey} order
 * @param bonuses the ruleset's bonuses that bear on this line, in the ruleset's order, each with
 *                whether the line reached it: those on a stat the line has some of (so a near miss
 *                shows how far short it fell) and those that were paid. The same rule as
 *                {@code parts}, which lists a stat with a value or a rate. A bonus on a stat the
 *                line has none of -- passing yards on a running back -- explains nothing, so it is
 *                not a line. A bonus that pays nothing is not in the ruleset once compiled. On a
 *                projected line a bonus is all-or-nothing at the line's mean -- a stated
 *                approximation, not an expected value
 */
public record ExplainedScore(double total, List<Part> parts, List<BonusLine> bonuses) {

    /** One stat's share: {@code value} x {@code rate} = {@code points}, unrounded. */
    public record Part(String stat, double value, double rate, double points) {}

    /**
     * One threshold bonus: paid once, in full, when {@code value} reaches {@code gte}.
     *
     * @param stat      the stat it is on, named as {@link StatKey#json} names it
     * @param gte       the threshold
     * @param value     the line's value of that stat
     * @param points    what the ruleset pays for it, whether or not this line earned it
     * @param triggered whether {@code value} reached {@code gte}: only then is {@code points}
     *                  part of the total
     */
    public record BonusLine(String stat, int gte, double value, double points, boolean triggered) {}

    public ExplainedScore {
        parts = List.copyOf(parts);
        bonuses = List.copyOf(bonuses);
    }

    public static ExplainedScore of(StatLine line, ResolvedRuleset rules) {
        double[] rates = rules.ratesFor(line.position());
        double[] values = line.values();
        List<Part> parts = new ArrayList<>();
        for (StatKey key : StatKey.values()) {
            double value = values[key.index()];
            double rate = rates[key.index()];
            if (value != 0 || rate != 0) {
                parts.add(new Part(key.json(), value, rate, value * rate));
            }
        }
        List<BonusLine> bonuses = new ArrayList<>();
        for (Bonus bonus : rules.bonuses()) {
            double value = values[bonus.stat().index()];
            boolean triggered = bonus.reachedBy(value);
            if (triggered || value != 0) {
                bonuses.add(new BonusLine(bonus.stat().json(), bonus.gte(), value, bonus.points(), triggered));
            }
        }
        return new ExplainedScore(ScoringEngine.score(line, rules), parts, bonuses);
    }

    /**
     * What the bonuses that were reached add to the total: their points, summed together. Exact,
     * unlike the remainder (total less the linear parts) it replaces, which carried rounding
     * noise. Added to the parts' sum it can still differ from the total in the last bit when two
     * or more bonuses fire, because the engine adds them one at a time to the running total;
     * the lines, summed in order, are the engine's sum exactly.
     */
    public double bonusPoints() {
        double points = 0;
        for (BonusLine bonus : bonuses) {
            if (bonus.triggered()) {
                points += bonus.points();
            }
        }
        return points;
    }
}
