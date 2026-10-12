package com.fantasykai.projection;

import com.fantasykai.scoring.Bonus;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.RulesetJson;
import com.fantasykai.scoring.RulesetValidator;
import com.fantasykai.scoring.ScoringProfiles;
import com.fantasykai.scoring.StatKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * A ruleset the backtest scores under. Evaluation only: one model is trained once, and these
 * only change how its projected line is scored -- training per format would be the
 * {@code fantasy_points} mistake again.
 *
 * <p>The presets are read from {@code scoring_profiles} and parsed by the product's own
 * {@link RulesetJson} and {@link RulesetValidator}; each is checked to hash the same as
 * {@link ScoringProfiles#preset} compiles it, so the backtest scores exactly what the board
 * does.
 */
record League(String label, Ruleset ruleset, ExpectedPoints points) {

    static final String PPR = "PPR";

    static List<League> load(Connection connection) {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        RulesetJson json = new RulesetJson(new ObjectMapper());
        RulesetValidator validator = new RulesetValidator();
        ScoringProfiles profiles = new ScoringProfiles(jdbc, json, validator);

        List<League> leagues = new ArrayList<>();
        Ruleset half = null;
        for (String[] preset : new String[][] {
                {"0 PPR", "Standard"}, {"Half PPR", "Half PPR"}, {PPR, "Full PPR"}}) {
            String rules = jdbc.queryForObject(
                    "SELECT rules::text FROM scoring_profiles WHERE is_preset AND name = ?",
                    String.class, preset[1]);
            Ruleset ruleset = validator.validate(json.read(rules));
            ResolvedRuleset product = profiles.preset(preset[1]);
            if (!ResolvedRuleset.compile(ruleset).hash().equals(product.hash())) {
                throw new IllegalStateException(preset[1] + " does not match the product's preset");
            }
            leagues.add(new League(preset[0], ruleset, new ExpectedPoints(ruleset)));
            if ("Half PPR".equals(preset[1])) {
                half = ruleset;
            }
        }

        // The landing page's representative league: Half PPR with 6-point passing touchdowns.
        Map<StatKey, Double> sixPoint = new EnumMap<>(half.base());
        sixPoint.put(StatKey.PASS_TD, 6.0);
        Ruleset myLeague = validator.validate(
                new Ruleset(1, sixPoint, half.positionOverrides(), List.of()));
        leagues.add(new League("My league", myLeague, new ExpectedPoints(myLeague)));

        // No preset carries a bonus, but the builder lets a member add one. A common shape.
        Ruleset bonus = validator.validate(new Ruleset(1, half.base(), half.positionOverrides(),
                List.of(new Bonus(StatKey.RUSH_YD, 100, 3), new Bonus(StatKey.REC_YD, 100, 3),
                        new Bonus(StatKey.PASS_YD, 300, 3))));
        leagues.add(new League("Bonus league", bonus, new ExpectedPoints(bonus)));
        return List.copyOf(leagues);
    }
}
