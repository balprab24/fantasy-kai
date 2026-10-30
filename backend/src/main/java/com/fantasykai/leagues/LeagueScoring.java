package com.fantasykai.leagues;

import com.fantasykai.scoring.Ruleset;
import java.util.List;
import java.util.Map;

/**
 * A league's scoring, translated: what Fantasy Kai can score, and what it cannot.
 *
 * @param ruleset        null when the league's scoring does not validate
 * @param error          why there is no ruleset; null when there is one
 * @param unmodeled      every non-zero setting the ruleset does not carry, by platform key
 * @param approximations plain-language notes on settings carried approximately
 */
public record LeagueScoring(Ruleset ruleset, String error, Map<String, Double> unmodeled,
        List<String> approximations) {

    public LeagueScoring {
        unmodeled = Map.copyOf(unmodeled);
        approximations = List.copyOf(approximations);
    }
}
