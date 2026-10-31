package com.fantasykai.leagues;

import com.fantasykai.scoring.Bonus;
import com.fantasykai.scoring.InvalidRulesetException;
import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.RulesetValidator;
import com.fantasykai.scoring.StatKey;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * A Sleeper league's {@code scoring_settings} as a version-1 {@link Ruleset}.
 *
 * <p>Sleeper names twelve of our thirteen stats exactly as {@link StatKey}
 * does (plan A1), so most of this is a copy. The rest is the part that matters:
 * <ul>
 *   <li>yardage bonuses become threshold {@link Bonus}es, which is what they are;
 *   <li>{@code bonus_rec_te/rb/wr} become a position override of {@code rec};
 *   <li>kick, punt and special-teams return TDs share our one {@code ret_td}, an
 *       approximation that is reported as one;
 *   <li>every other key with a non-zero value is <strong>reported, never
 *       dropped</strong> -- a member whose league scores first downs is told so.
 * </ul>
 *
 * <p>When the result does not validate, there is no ruleset and the reason is
 * kept. {@link RulesetValidator}'s own rule: rejections are loud, never clamped.
 */
@Component
public class SleeperScoringMapper {

    /** Sleeper's yardage bonuses: key to stat and threshold. */
    private static final Map<String, Bonus> BONUSES = Map.of(
            "bonus_pass_yd_300", new Bonus(StatKey.PASS_YD, 300, 0),
            "bonus_pass_yd_400", new Bonus(StatKey.PASS_YD, 400, 0),
            "bonus_rush_yd_100", new Bonus(StatKey.RUSH_YD, 100, 0),
            "bonus_rush_yd_200", new Bonus(StatKey.RUSH_YD, 200, 0),
            "bonus_rec_yd_100", new Bonus(StatKey.REC_YD, 100, 0),
            "bonus_rec_yd_200", new Bonus(StatKey.REC_YD, 200, 0));

    /** Per-position reception premiums: added to {@code rec} for that position. */
    private static final Map<String, String> RECEPTION_PREMIUMS = Map.of(
            "bonus_rec_rb", "RB", "bonus_rec_wr", "WR", "bonus_rec_te", "TE");

    private static final List<String> RETURN_TDS = List.of("kr_td", "pr_td", "st_td");

    private final RulesetValidator validator;

    public SleeperScoringMapper(RulesetValidator validator) {
        this.validator = validator;
    }

    public LeagueScoring map(JsonNode settings) {
        if (settings == null || !settings.isObject()) {
            throw new LeagueDataException("Sleeper scoring_settings is not an object");
        }
        Map<String, Double> values = new HashMap<>();
        settings.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isNumber()) {
                throw new LeagueDataException(
                        "Sleeper scoring setting " + entry.getKey() + " is not a number");
            }
            // Sleeper stores float32 noise: pass_yd arrives as 0.03999999910593033.
            values.put(entry.getKey(), round(entry.getValue().asDouble()));
        });

        Set<String> used = new LinkedHashSet<>();
        Map<StatKey, Double> base = new EnumMap<>(StatKey.class);
        for (StatKey stat : StatKey.values()) {
            if (stat != StatKey.RET_TD && values.containsKey(stat.json())) {
                base.put(stat, values.get(stat.json()));
                used.add(stat.json());
            }
        }

        List<String> approximations = new ArrayList<>();
        List<Double> returns = RETURN_TDS.stream()
                .filter(key -> values.getOrDefault(key, 0.0) != 0.0)
                .map(values::get).distinct().toList();
        if (returns.size() == 1) {
            base.put(StatKey.RET_TD, returns.getFirst());
            RETURN_TDS.stream().filter(key -> values.getOrDefault(key, 0.0) != 0.0).forEach(used::add);
            approximations.add("Kick return, punt return and special-teams touchdowns are scored "
                    + "together as return touchdowns, at " + format(returns.getFirst()) + " points.");
        }
        // Different rates for different return TDs cannot share one stat: left unmodelled.

        List<Bonus> bonuses = new ArrayList<>();
        BONUSES.forEach((key, shape) -> {
            double points = values.getOrDefault(key, 0.0);
            used.add(key);
            if (points != 0.0) {
                bonuses.add(new Bonus(shape.stat(), shape.gte(), points));
            }
        });
        bonuses.sort((a, b) -> a.stat() != b.stat()
                ? a.stat().compareTo(b.stat()) : Integer.compare(a.gte(), b.gte()));

        Map<String, Map<StatKey, Double>> overrides = new TreeMap<>();
        RECEPTION_PREMIUMS.forEach((key, position) -> {
            double premium = values.getOrDefault(key, 0.0);
            used.add(key);
            if (premium != 0.0) {
                overrides.put(position, Map.of(StatKey.REC,
                        round(base.getOrDefault(StatKey.REC, 0.0) + premium)));
            }
        });

        Map<String, Double> unmodeled = new TreeMap<>();
        values.forEach((key, value) -> {
            if (!used.contains(key) && value != 0.0) {
                unmodeled.put(key, value);
            }
        });

        Ruleset ruleset = new Ruleset(Ruleset.CURRENT_VERSION, base, overrides, bonuses);
        try {
            return new LeagueScoring(validator.validate(ruleset), null, unmodeled, approximations);
        } catch (InvalidRulesetException e) {
            return new LeagueScoring(null, e.getMessage(), unmodeled, approximations);
        }
    }

    /** Four decimals, half up, in decimal -- never {@code Math.round(x * 1e4) / 1e4} (development.md). */
    static double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
