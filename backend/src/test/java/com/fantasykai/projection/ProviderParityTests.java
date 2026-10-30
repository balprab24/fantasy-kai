package com.fantasykai.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fantasykai.projections.HistoricalGame;
import com.fantasykai.projections.PlayerHistory;
import com.fantasykai.projections.RawProjection;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.projections.WeightedAverageProjectionProvider;
import com.fantasykai.scoring.StatKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Production's {@link WeightedAverageProjectionProvider} against the research baseline it
 * reproduces -- {@code Baseline(EWMA, 4)}, the one frozen in {@code frozen-config.json} -- on
 * seeded random histories: gaps, byes, season crossings, zero lines, negative yardage, one to 150
 * games. Equality is demanded bit for bit, not within a tolerance: the two do the same floating
 * point operations in the same order, so any difference is a different formula.
 *
 * <p>The production side gets the whole history and applies its own cutoff; the research side
 * gets {@link Timeline#asOf}. So this also checks the two leakage doors agree.
 * {@code scripts/backtest.sh parity} runs the same comparison over every 2024-25 player-week of
 * real data.
 */
class ProviderParityTests {

    private static final Baseline RESEARCH = new Baseline(Baseline.Kind.EWMA, 4.0);
    private static final WeightedAverageProjectionProvider PRODUCTION = new WeightedAverageProjectionProvider();

    @Test
    void seededRandomHistoriesMatchBitForBit() {
        SplittableRandom random = new SplittableRandom(20261009L);
        int compared = 0;
        int empty = 0;
        for (int h = 0; h < 2000; h++) {
            List<PlayerGame> research = new ArrayList<>();
            List<HistoricalGame> production = new ArrayList<>();
            int season = 2020 + random.nextInt(3);
            int week = 1 + random.nextInt(4);
            int length = 1 + random.nextInt(150);
            for (int i = 0; i < length && season <= 2026; i++) {
                double[] line = randomLine(random);
                research.add(new PlayerGame(1L, "WR", i, season, week, 1, 80.0, line, 0, null,
                        Double.NaN, false));
                production.add(new HistoricalGame(new SeasonWeek(season, week),
                        Arrays.copyOf(line, StatKey.COUNT), line[Quantity.PASS_ATT],
                        line[Quantity.RUSH_ATT], line[Quantity.TARGETS]));
                week += random.nextInt(5) == 0 ? 2 : 1;   // a bye or a missed game now and then
                if (week > (season == 2020 ? 17 : 18)) {
                    season++;
                    week = 1 + random.nextInt(2);
                }
            }
            // Project a week inside the history half the time, just past it otherwise.
            PlayerGame anchor = research.get(random.nextInt(research.size()));
            int targetSeason = anchor.season();
            int targetWeek = anchor.week();
            if (random.nextBoolean()) {
                PlayerGame last = research.get(research.size() - 1);
                targetSeason = last.week() == 18 ? last.season() + 1 : last.season();
                targetWeek = last.week() == 18 ? 1 : last.week() + 1;
            }
            List<PlayerGame> asOf = new Timeline<>(research).asOf(targetSeason, targetWeek);
            Optional<RawProjection> projected = PRODUCTION.project(
                    PlayerHistory.before(new SeasonWeek(targetSeason, targetWeek), production));
            if (asOf.isEmpty()) {
                assertThat(projected).isEmpty();
                empty++;
                continue;
            }
            assertBitIdentical(RESEARCH.project(asOf, targetSeason), projected.orElseThrow(), asOf.size());
            compared++;
        }
        assertThat(compared).isGreaterThan(1500);
        assertThat(empty).isPositive();
    }

    static void assertBitIdentical(double[] research, RawProjection production, int games) {
        for (StatKey stat : StatKey.values()) {
            assertThat(Double.doubleToRawLongBits(production.get(stat))).as(stat.json())
                    .isEqualTo(Double.doubleToRawLongBits(research[stat.index()]));
        }
        assertThat(Double.doubleToRawLongBits(production.passAttempts()))
                .isEqualTo(Double.doubleToRawLongBits(research[Quantity.PASS_ATT]));
        assertThat(Double.doubleToRawLongBits(production.carries()))
                .isEqualTo(Double.doubleToRawLongBits(research[Quantity.RUSH_ATT]));
        assertThat(Double.doubleToRawLongBits(production.targets()))
                .isEqualTo(Double.doubleToRawLongBits(research[Quantity.TARGETS]));
        assertThat(production.gamesUsed()).isEqualTo(games);
    }

    /** A plausible-to-ugly line: sometimes all zeros, sometimes negative rushing. */
    private static double[] randomLine(SplittableRandom random) {
        double[] line = new double[Quantity.COUNT];
        if (random.nextInt(7) == 0) {
            return line;
        }
        line[StatKey.PASS_YD.index()] = random.nextInt(8) == 0 ? random.nextInt(450) : 0;
        line[StatKey.PASS_TD.index()] = random.nextInt(4);
        line[StatKey.PASS_INT.index()] = random.nextInt(3);
        line[StatKey.RUSH_YD.index()] = random.nextInt(140) - 12;
        line[StatKey.RUSH_TD.index()] = random.nextInt(10) == 0 ? 1 : 0;
        int targets = random.nextInt(14);
        int rec = targets == 0 ? 0 : random.nextInt(targets + 1);
        line[StatKey.REC.index()] = rec;
        line[StatKey.REC_YD.index()] = rec * random.nextInt(25) - (rec > 0 ? random.nextInt(3) : 0);
        line[StatKey.REC_TD.index()] = random.nextInt(6) == 0 ? 1 : 0;
        line[StatKey.FUM_LOST.index()] = random.nextInt(25) == 0 ? 1 : 0;
        line[StatKey.RET_TD.index()] = random.nextInt(200) == 0 ? 1 : 0;
        line[StatKey.REC_2PT.index()] = random.nextInt(60) == 0 ? 1 : 0;
        line[Quantity.TARGETS] = targets;
        line[Quantity.RUSH_ATT] = random.nextInt(25);
        line[Quantity.PASS_ATT] = line[StatKey.PASS_YD.index()] > 0 ? 10 + random.nextInt(40) : 0;
        return line;
    }
}
