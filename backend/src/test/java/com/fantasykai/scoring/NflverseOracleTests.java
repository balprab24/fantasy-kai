package com.fantasykai.scoring;

import static com.fantasykai.scoring.ScoringEngine.roundForDisplay;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * An independent check on the evaluator.
 *
 * <p>nflverse ships {@code fantasy_points} and {@code fantasy_points_ppr} and
 * §5 says not to store them -- a stored point value is correct for exactly one
 * ruleset. But not storing a number is not the same as not being able to check
 * against it. These 53 real 2025 stat lines are scored under our Standard and
 * Full PPR presets and compared to the source's own arithmetic, which is an
 * oracle we did not write. Every other test in this package asserts our maths
 * against our maths.
 *
 * <p>The fixture was chosen to cover the cases that break naive scorers: 2-point
 * conversions of all three kinds, return touchdowns, multi-interception games,
 * lost fumbles of both kinds, 100-yard and 300-yard games, and scoreless lines.
 */
class NflverseOracleTests {

    /**
     * Where we disagree with nflverse on purpose.
     *
     * <p>nflverse penalises only fumbles lost on offence -- sacks, rushes and
     * receptions. We ingest {@code fumbles_lost_total}, which also counts a
     * muffed punt or kickoff. Real leagues penalise the roster player for any
     * fumble they lose, so ours is the behaviour a fantasy platform wants; the
     * difference is a decision, not a defect.
     *
     * <p>Pinned exactly rather than absorbed into a tolerance. A tolerance wide
     * enough to hide a two-point return fumble is wide enough to hide a bug.
     */
    private static double returnFumblePenalty(CSVRecord row) {
        double offensive = value(row, "sack_fumbles_lost")
                + value(row, "rushing_fumbles_lost")
                + value(row, "receiving_fumbles_lost");
        return 2.0 * (value(row, "fumbles_lost_total") - offensive);
    }

    private static final ResolvedRuleset STANDARD = ResolvedRuleset.compile(Presets.standard());
    private static final ResolvedRuleset FULL_PPR = ResolvedRuleset.compile(Presets.fullPpr());

    private static List<CSVRecord> rows;

    @BeforeAll
    static void loadFixture() throws Exception {
        try (var reader = new InputStreamReader(
                        new ClassPathResource("nflverse/scoring_oracle_2025.csv").getInputStream(),
                        StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            rows = new ArrayList<>(parser.getRecords());
        }
    }

    @Test
    void ourStandardPresetReproducesTheSourcesFantasyPoints() {
        for (CSVRecord row : rows) {
            double ours = ScoringEngine.score(statLine(row), STANDARD) + returnFumblePenalty(row);

            assertThat(roundForDisplay(ours))
                    .describedAs("%s wk%s standard", row.get("player_display_name"), row.get("week"))
                    .isEqualTo(roundForDisplay(value(row, "fantasy_points")));
        }
    }

    @Test
    void ourFullPprPresetReproducesTheSourcesPprPoints() {
        for (CSVRecord row : rows) {
            double ours = ScoringEngine.score(statLine(row), FULL_PPR) + returnFumblePenalty(row);

            assertThat(roundForDisplay(ours))
                    .describedAs("%s wk%s full PPR", row.get("player_display_name"), row.get("week"))
                    .isEqualTo(roundForDisplay(value(row, "fantasy_points_ppr")));
        }
    }

    @Test
    void theFixtureStillContainsTheReturnFumbleDisagreement() {
        // If a future fixture refresh drops these rows, the two tests above keep
        // passing while quietly testing less. This is the tripwire for that.
        List<CSVRecord> divergent = rows.stream().filter(r -> returnFumblePenalty(r) != 0).toList();

        assertThat(divergent).describedAs("rows where a fumble was lost on a return").isNotEmpty();
        assertThat(divergent).allSatisfy(row ->
                assertThat(returnFumblePenalty(row)).isEqualTo(2.0));
    }

    @Test
    void aFixtureRowIsScoredByEveryStatWeClaimToSupport() {
        // A silent mapping bug -- a stat never read from the fixture -- would let
        // the oracle pass on rows where that stat is always zero.
        for (StatKey stat : List.of(StatKey.PASS_YD, StatKey.PASS_TD, StatKey.PASS_INT,
                StatKey.PASS_2PT, StatKey.RUSH_YD, StatKey.RUSH_TD, StatKey.RUSH_2PT,
                StatKey.REC, StatKey.REC_YD, StatKey.REC_TD, StatKey.REC_2PT,
                StatKey.FUM_LOST, StatKey.RET_TD)) {
            assertThat(rows).describedAs("no fixture row exercises %s", stat.json())
                    .anySatisfy(row -> assertThat(statLine(row).get(stat)).isNotZero());
        }
    }

    /** Mirrors the mapping StatIngestor uses, so this tests the same columns production reads. */
    private static StatLine statLine(CSVRecord row) {
        Map<StatKey, Double> stats = new EnumMap<>(StatKey.class);
        stats.put(StatKey.PASS_YD, value(row, "passing_yards"));
        stats.put(StatKey.PASS_TD, value(row, "passing_tds"));
        stats.put(StatKey.PASS_INT, value(row, "passing_interceptions"));
        stats.put(StatKey.PASS_2PT, value(row, "passing_2pt_conversions"));
        stats.put(StatKey.RUSH_YD, value(row, "rushing_yards"));
        stats.put(StatKey.RUSH_TD, value(row, "rushing_tds"));
        stats.put(StatKey.RUSH_2PT, value(row, "rushing_2pt_conversions"));
        stats.put(StatKey.REC, value(row, "receptions"));
        stats.put(StatKey.REC_YD, value(row, "receiving_yards"));
        stats.put(StatKey.REC_TD, value(row, "receiving_tds"));
        stats.put(StatKey.REC_2PT, value(row, "receiving_2pt_conversions"));
        stats.put(StatKey.FUM_LOST, value(row, "fumbles_lost_total"));
        stats.put(StatKey.RET_TD, value(row, "special_teams_tds"));
        return StatLine.of(row.get("position"), stats);
    }

    private static double value(CSVRecord row, String column) {
        String raw = row.get(column);
        if (raw == null || raw.isBlank() || "NA".equals(raw)) {
            return 0;
        }
        return Double.parseDouble(raw);
    }
}
