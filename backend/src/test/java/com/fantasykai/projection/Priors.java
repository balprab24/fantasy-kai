package com.fantasykai.projection;

import com.fantasykai.scoring.StatKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Positional rates measured on the training seasons only -- the means a thin record is
 * shrunk toward and the per-touch rates rare stats are projected from.
 *
 * <p>Computed from every stored skill row in the training seasons, not just the evaluated
 * population, and never from the season being evaluated.
 */
final class Priors {

    static final List<String> POSITIONS = List.of("QB", "RB", "WR", "TE");

    private final Map<String, double[]> perGame = new HashMap<>();
    private final Map<String, double[]> rates = new HashMap<>();
    private final Map<String, double[]> perTouch = new HashMap<>();
    private final Map<String, Double> snap = new HashMap<>();
    private final Map<String, double[]> allowed = new HashMap<>();
    private final double[] teamVolume;
    private final double impliedTotal;

    /** Per-touch rate slots. */
    static final int FUMBLE_PER_TOUCH = 0;
    static final int PASS_2PT_PER_ATT = 1;
    static final int RUSH_2PT_PER_ATT = 2;
    static final int REC_2PT_PER_TARGET = 3;
    static final int RET_TD_PER_RETURN = 4;

    private Priors(double[] teamVolume, double impliedTotal) {
        this.teamVolume = teamVolume;
        this.impliedTotal = impliedTotal;
    }

    static Priors from(BacktestData data, List<Integer> seasons) {
        double[] team = new double[3];
        int teamGames = 0;
        for (Timeline<TeamGame> timeline : data.teams().values()) {
            for (TeamGame game : timeline.all()) {
                if (seasons.contains(game.season())) {
                    team[0] += game.passAtt();
                    team[1] += game.rushAtt();
                    team[2] += game.targets();
                    teamGames++;
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            team[i] /= teamGames;
        }

        double implied = 0;
        int lines = 0;
        for (Game game : data.games().values()) {
            if (seasons.contains(game.season()) && game.hasLine()) {
                implied += game.impliedTotal(game.homeTeamId()) + game.impliedTotal(game.awayTeamId());
                lines += 2;
            }
        }
        Priors priors = new Priors(team, implied / lines);

        double[][] allowedSum = new double[POSITIONS.size()][Quantity.COUNT];
        int defenseGames = 0;
        for (Timeline<DefenseGame> timeline : data.defenses().values()) {
            for (DefenseGame game : timeline.all()) {
                if (seasons.contains(game.season())) {
                    for (int p = 0; p < POSITIONS.size(); p++) {
                        for (int q = 0; q < Quantity.COUNT; q++) {
                            allowedSum[p][q] += game.allowed()[p][q];
                        }
                    }
                    defenseGames++;
                }
            }
        }
        for (int p = 0; p < POSITIONS.size(); p++) {
            double[] mean = new double[Quantity.COUNT];
            for (int q = 0; q < Quantity.COUNT; q++) {
                mean[q] = allowedSum[p][q] / defenseGames;
            }
            priors.allowed.put(POSITIONS.get(p), mean);
        }

        for (String position : POSITIONS) {
            double[] sum = new double[Quantity.COUNT];
            double games = 0;
            double returns = 0;
            double snapSum = 0;
            double snapN = 0;
            for (Timeline<PlayerGame> timeline : data.players().values()) {
                for (PlayerGame game : timeline.all()) {
                    if (!position.equals(game.position()) || !seasons.contains(game.season())) {
                        continue;
                    }
                    for (int q = 0; q < Quantity.COUNT; q++) {
                        sum[q] += game.line()[q];
                    }
                    games++;
                    returns += game.returns();
                    if (!Double.isNaN(game.snapPct())) {
                        snapSum += game.snapPct();
                        snapN++;
                    }
                }
            }
            double[] mean = new double[Quantity.COUNT];
            for (int q = 0; q < Quantity.COUNT; q++) {
                mean[q] = sum[q] / games;
            }
            priors.perGame.put(position, mean);

            double[] rate = new double[Rate.values().length];
            for (Rate r : Rate.values()) {
                rate[r.ordinal()] = ratio(sum[r.numerator], sum[r.denominator]);
            }
            priors.rates.put(position, rate);

            double touches = sum[Quantity.PASS_ATT] + sum[Quantity.RUSH_ATT]
                    + sum[Quantity.of(StatKey.REC)];
            priors.perTouch.put(position, new double[] {
                ratio(sum[Quantity.of(StatKey.FUM_LOST)], touches),
                ratio(sum[Quantity.of(StatKey.PASS_2PT)], sum[Quantity.PASS_ATT]),
                ratio(sum[Quantity.of(StatKey.RUSH_2PT)], sum[Quantity.RUSH_ATT]),
                ratio(sum[Quantity.of(StatKey.REC_2PT)], sum[Quantity.TARGETS]),
                ratio(sum[Quantity.of(StatKey.RET_TD)], returns)
            });
            priors.snap.put(position, snapSum / snapN);
        }
        return priors;
    }

    private static double ratio(double numerator, double denominator) {
        return denominator == 0 ? 0 : numerator / denominator;
    }

    double perGame(String position, int q) {
        return perGame.get(position)[q];
    }

    double rate(String position, Rate rate) {
        return rates.get(position)[rate.ordinal()];
    }

    double perTouch(String position, int slot) {
        return perTouch.get(position)[slot];
    }

    /** League mean a defense allowed per game to a position, per quantity. */
    double allowed(String position, int q) {
        return allowed.get(position)[q];
    }

    double snap(String position) {
        return snap.get(position);
    }

    /** League team mean per game: 0 pass attempts, 1 carries, 2 targets. */
    double teamVolume(int slot) {
        return teamVolume[slot];
    }

    double impliedTotal() {
        return impliedTotal;
    }
}
