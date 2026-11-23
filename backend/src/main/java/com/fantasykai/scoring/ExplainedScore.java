package com.fantasykai.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * A scored line with its parts: each stat's value x rate = points, and each threshold bonus the
 * ruleset pays, whether or not the line reached it. "Why is he ranked here" is the product
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
 * @param bonuses every bonus of the ruleset, in the ruleset's order, with whether this line
 *                reached it. A bonus that pays nothing is not in the ruleset once compiled, so it
 *                is not here. On a projected line a bonus is all-or-nothing at the line's mean --
 *                a stated approximation, not an expected value
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
            bonuses.add(new BonusLine(bonus.stat().json(), bonus.gte(), value, bonus.points(),
                    bonus.reachedBy(value)));
        }
        return new ExplainedScore(ScoringEngine.score(line, rules), parts, bonuses);
    }

    /** What the bonuses that were reached add to the total: their points, summed in order. */
    public double bonusPoints() {
        double points = 0;
        for (BonusLine bonus : bonuses) {
            if (bonus.triggered()) {
                points += bonus.points();
            }
        }
        return points;
    }

    /**
     * The same explanation for a scaled line -- a week the player is expected to miss is 0 x it.
     * Every quantity and every worth is scaled, so the parts and the reached bonuses still add up
     * to the scaled total; whether a bonus was reached is a fact about the line as it stood and is
     * kept.
     */
    public ExplainedScore times(double factor) {
        return new ExplainedScore(total * factor, parts.stream()
                .map(p -> new Part(p.stat(), p.value() * factor, p.rate(), p.points() * factor)).toList(),
                bonuses.stream().map(b -> new BonusLine(b.stat(), b.gte(), b.value() * factor,
                        b.points() * factor, b.triggered())).toList());
    }
}
