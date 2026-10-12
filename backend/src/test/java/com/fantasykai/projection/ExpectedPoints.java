package com.fantasykai.projection;

import com.fantasykai.scoring.Bonus;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.StatLine;
import java.util.List;

/**
 * Expected fantasy points for a projected stat line, around {@link ScoringEngine} rather
 * than inside it.
 *
 * <pre>
 * expected = ScoringEngine.score(mean line, the same rules without bonuses)
 *          + Σ bonus.points × P(stat ≥ bonus.gte)
 * </pre>
 *
 * <p>The linear part is exact: the expectation of a dot product is the dot product of the
 * expectation. A threshold bonus is not linear, so scoring the mean line with it would hand
 * a back projected for 101 rushing yards the whole bonus and one projected for 99 nothing.
 * The engine stays deterministic and unchanged -- it scores what happened -- and the
 * probability lives here, beside the projection it belongs to.
 *
 * <p>For a ruleset with no bonuses, which is every preset, {@link #expected} returns
 * {@code ScoringEngine.score(mean, rules)} itself, so the result is bit-identical.
 */
final class ExpectedPoints {

    /** Where the probability of reaching a threshold comes from -- {@link BonusOdds} in the backtest. */
    interface Odds {
        double probability(String position, com.fantasykai.scoring.StatKey stat, double gte, double projected);
    }

    private final ResolvedRuleset full;
    private final ResolvedRuleset linear;

    ExpectedPoints(Ruleset ruleset) {
        this.full = ResolvedRuleset.compile(ruleset);
        this.linear = ResolvedRuleset.compile(new Ruleset(ruleset.version(), ruleset.base(),
                ruleset.positionOverrides(), List.of()));
    }

    double expected(StatLine mean, Odds odds) {
        if (full.bonuses().isEmpty()) {
            return ScoringEngine.score(mean, full);
        }
        double points = ScoringEngine.score(mean, linear);
        for (Bonus bonus : full.bonuses()) {
            points += bonus.points()
                    * odds.probability(mean.position(), bonus.stat(), bonus.gte(), mean.get(bonus.stat()));
        }
        return points;
    }

    /** The bonus applied to the mean line as if it were a game: the step the brief rejects. */
    double stepped(StatLine mean) {
        return ScoringEngine.score(mean, full);
    }

    double ignoringBonuses(StatLine mean) {
        return ScoringEngine.score(mean, linear);
    }

    /** What a played game scored: the engine, untouched. */
    double actual(StatLine line) {
        return ScoringEngine.score(line, full);
    }

    boolean hasBonuses() {
        return !full.bonuses().isEmpty();
    }

    List<Bonus> bonuses() {
        return full.bonuses();
    }
}
