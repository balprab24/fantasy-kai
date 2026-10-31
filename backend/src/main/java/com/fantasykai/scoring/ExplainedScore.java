package com.fantasykai.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * A scored line with its parts: each stat's value x rate = points, and the
 * threshold bonuses as one remainder. "Why is he ranked here" is the product
 * (architecture.md: every ranked number carries its parts), so a number that
 * reaches a member travels as this, not as a bare double.
 *
 * <p>The total is {@link ScoringEngine#score} itself, never a re-derivation,
 * so the explanation and the number cannot disagree. Unrounded: rounding is
 * the API boundary's, once.
 *
 * @param total       the scored line
 * @param parts       every stat with a value or a rate, in {@link StatKey} order
 * @param bonusPoints the threshold bonuses: the total less its linear parts. On
 *                    an expected line a bonus is all-or-nothing at the mean --
 *                    a stated approximation, not an expected value
 */
public record ExplainedScore(double total, List<Part> parts, double bonusPoints) {

    /** One stat's share: {@code value} x {@code rate} = {@code points}, unrounded. */
    public record Part(String stat, double value, double rate, double points) {}

    public ExplainedScore {
        parts = List.copyOf(parts);
    }

    public static ExplainedScore of(StatLine line, ResolvedRuleset rules) {
        double[] rates = rules.ratesFor(line.position());
        double[] values = line.values();
        List<Part> parts = new ArrayList<>();
        double linear = 0;
        for (StatKey key : StatKey.values()) {
            double value = values[key.index()];
            double rate = rates[key.index()];
            linear += value * rate;
            if (value != 0 || rate != 0) {
                parts.add(new Part(key.json(), value, rate, value * rate));
            }
        }
        double total = ScoringEngine.score(line, rules);
        return new ExplainedScore(total, parts, total - linear);
    }

    /** The same explanation for a scaled line -- a week the player is expected to miss is 0 x it. */
    public ExplainedScore times(double factor) {
        return new ExplainedScore(total * factor, parts.stream()
                .map(p -> new Part(p.stat(), p.value() * factor, p.rate(), p.points() * factor)).toList(),
                bonusPoints * factor);
    }
}
