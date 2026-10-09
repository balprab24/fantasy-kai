package com.fantasykai.projection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The named inputs a projection may use, computed from a {@link Case}'s pre-game view only:
 * its history (already cut at week N by {@link Timeline#asOf}), its team's history (cut the
 * same way), and the week's schedule and line. {@link Case#actual} is never read here.
 *
 * <p>Nothing missing is silently zero: a player with no prior season takes his
 * season-to-date mean, a missing snap reading falls back to the positional mean, a game with
 * no line to the training seasons' mean implied total. Each fallback is a choice written
 * down, which is the {@code CsvValues.shortValue} trap one layer up.
 */
final class Features {

    /** Efficiencies and rare stats move slowly: weight them over about a season of games. */
    static final double LONG_HALF_LIFE = 16;

    /**
     * An opponent's allowed-per-game is blended with this many games at the league mean, so
     * a defense two games into a season is not judged on two games.
     */
    static final double OPPONENT_PSEUDO_GAMES = 4;

    /** RACR is blended with this many air yards at the positional rate (v2a; fixed, not tuned). */
    static final double RACR_PSEUDO_AIR_YARDS = 300;

    /**
     * The scoring environment is blended with this many team-games at the training seasons'
     * level -- about two weeks of a full slate -- so week 2 is not judged on one week (v2b;
     * fixed, not tuned).
     */
    static final double ENVIRONMENT_PSEUDO_TEAM_GAMES = 32;

    static final List<String> NAMES;
    private static final Map<String, Integer> INDEX = new HashMap<>();

    static {
        List<String> names = new ArrayList<>();
        for (int q = 0; q < Quantity.COUNT; q++) {
            String n = Quantity.name(q);
            names.add("ewma:" + n);
            names.add("std:" + n);
            names.add("last:" + n);
            names.add("prior:" + n);
            names.add("lsum:" + n);
            names.add("opp:" + n);
            names.add("env:" + n);
        }
        names.addAll(List.of("ewma:air_yards", "ayshare", "x_air_yards", "racr"));
        names.addAll(List.of("lweight", "returns", "snap_ewma", "snap_last", "log_games",
                "no_season_games", "tshare", "cshare", "pshare", "team_pass", "team_rush",
                "team_tgt", "x_targets", "x_carries", "x_pass_att"));
        for (Rate rate : Rate.values()) {
            names.add(rate.feature());
        }
        names.addAll(List.of("implied_total", "team_spread", "home",
                "pos:QB", "pos:RB", "pos:WR", "pos:TE"));
        NAMES = List.copyOf(names);
        for (int i = 0; i < NAMES.size(); i++) {
            INDEX.put(NAMES.get(i), i);
        }
    }

    private Features() {}

    static int index(String name) {
        Integer i = INDEX.get(name);
        if (i == null) {
            throw new IllegalArgumentException("no feature named " + name);
        }
        return i;
    }

    static double[] compute(Case c, Priors priors, double halfLife) {
        double[] f = new double[NAMES.size()];
        List<PlayerGame> h = c.history;
        int n = h.size();

        double[] ewma = History.ewma(h, halfLife);
        double[] std = History.seasonToDate(h, c.season);
        double[] last = h.get(n - 1).line();
        double[] prior = History.priorSeason(h, c.season);
        double[] lsum = new double[Quantity.COUNT];
        double lweight = 0;
        double returns = 0;
        double[] rateNum = new double[Rate.values().length];
        double[] rateDen = new double[Rate.values().length];
        double targets = 0;
        double teamTargets = 0;
        double carries = 0;
        double teamCarries = 0;
        double attempts = 0;
        double teamAttempts = 0;
        for (int i = 0; i < n; i++) {
            PlayerGame game = h.get(i);
            double[] line = game.line();
            double lw = History.weight(n - 1 - i, LONG_HALF_LIFE);
            for (int q = 0; q < Quantity.COUNT; q++) {
                lsum[q] += lw * line[q];
            }
            lweight += lw;
            returns += lw * game.returns();
            for (Rate rate : Rate.values()) {
                rateNum[rate.ordinal()] += lw * line[rate.numerator];
                rateDen[rate.ordinal()] += lw * line[rate.denominator];
            }
            double w = History.weight(n - 1 - i, halfLife);
            targets += w * line[Quantity.TARGETS];
            teamTargets += w * game.team().targets();
            carries += w * line[Quantity.RUSH_ATT];
            teamCarries += w * game.team().rushAtt();
            attempts += w * line[Quantity.PASS_ATT];
            teamAttempts += w * game.team().passAtt();
        }

        for (int q = 0; q < Quantity.COUNT; q++) {
            String name = Quantity.name(q);
            f[index("ewma:" + name)] = ewma[q];
            f[index("std:" + name)] = std[q];
            f[index("last:" + name)] = last[q];
            // No earlier season (a rookie, or 2020): stand in this season's own mean.
            f[index("prior:" + name)] = Double.isNaN(prior[q]) ? std[q] : prior[q];
            f[index("lsum:" + name)] = lsum[q];
        }
        f[index("lweight")] = lweight;
        f[index("returns")] = returns / lweight;

        double snapEwma = History.ewmaSnap(h, halfLife);
        if (Double.isNaN(snapEwma)) {
            snapEwma = priors.snap(c.position);
        }
        double snapLast = History.lastSnap(h);
        f[index("snap_ewma")] = snapEwma;
        f[index("snap_last")] = Double.isNaN(snapLast) ? snapEwma : snapLast;
        f[index("log_games")] = Math.log1p(n);
        f[index("no_season_games")] = History.gamesInSeason(h, c.season) == 0 ? 1 : 0;

        double tshare = teamTargets == 0 ? 0 : targets / teamTargets;
        double cshare = teamCarries == 0 ? 0 : carries / teamCarries;
        double pshare = teamAttempts == 0 ? 0 : attempts / teamAttempts;
        double[] team = teamVolume(c.teamHistory, halfLife, priors);
        f[index("tshare")] = tshare;
        f[index("cshare")] = cshare;
        f[index("pshare")] = pshare;
        f[index("team_pass")] = team[0];
        f[index("team_rush")] = team[1];
        f[index("team_tgt")] = team[2];
        f[index("x_targets")] = tshare * team[2];
        f[index("x_carries")] = cshare * team[1];
        f[index("x_pass_att")] = pshare * team[0];

        for (Rate rate : Rate.values()) {
            double k = rate.pseudoCount;
            f[index(rate.feature())] = (rateNum[rate.ordinal()] + k * priors.rate(c.position, rate))
                    / (rateDen[rate.ordinal()] + k);
        }

        boolean line = c.game.hasLine();
        f[index("implied_total")] = line ? c.game.impliedTotal(c.teamId) : priors.impliedTotal();
        f[index("team_spread")] = line ? c.game.spreadFor(c.teamId) : 0;
        f[index("home")] = c.game.isHome(c.teamId) ? 1 : 0;
        f[index("pos:" + c.position)] = 1;

        double[] opponent = opponentFactor(c, priors, halfLife);
        double[] environment = environment(c, priors);
        for (int q = 0; q < Quantity.COUNT; q++) {
            f[index("opp:" + Quantity.name(q))] = opponent[q];
            f[index("env:" + Quantity.name(q))] = environment[q];
        }
        airYards(c, priors, halfLife, f);
        return f;
    }

    /**
     * Receiving opportunity from air yards (v2a), over the games whose air yards are known.
     *
     * <ul>
     *   <li>{@code ewma:air_yards} -- intended air yards per game, weighted like everything
     *       else</li>
     *   <li>{@code ayshare} -- his air yards over his team's passing air yards in the same
     *       games: nflverse's {@code air_yards_share}, verified to be that ratio</li>
     *   <li>{@code x_air_yards} -- that share times the team's weighted passing air yards, as
     *       {@code x_targets} is for targets</li>
     *   <li>{@code racr} -- receiving yards per air yard, long-weighted and shrunk</li>
     * </ul>
     *
     * A player with no known air yards takes the positional mean, never a zero.
     */
    private static void airYards(Case c, Priors priors, double halfLife, double[] f) {
        List<PlayerGame> h = c.history;
        int n = h.size();
        double air = 0;
        double weights = 0;
        double shareAir = 0;
        double teamAir = 0;
        double longAir = 0;
        double longRecYd = 0;
        for (int i = 0; i < n; i++) {
            PlayerGame game = h.get(i);
            if (Double.isNaN(game.airYards())) {
                continue;
            }
            double w = History.weight(n - 1 - i, halfLife);
            air += w * game.airYards();
            weights += w;
            // The share counts only games where both sides of the ratio are known.
            if (!Double.isNaN(game.team().passAirYards())) {
                shareAir += w * game.airYards();
                teamAir += w * game.team().passAirYards();
            }
            double lw = History.weight(n - 1 - i, LONG_HALF_LIFE);
            longAir += lw * game.airYards();
            longRecYd += lw * game.line()[Quantity.of(com.fantasykai.scoring.StatKey.REC_YD)];
        }
        double share = teamAir > 0 ? shareAir / teamAir : 0;
        f[index("ewma:air_yards")] = weights > 0 ? air / weights : priors.airPerGame(c.position);
        f[index("ayshare")] = share;
        f[index("x_air_yards")] = share * teamPassAir(c.teamHistory, halfLife, priors);
        double racrDenominator = longAir + RACR_PSEUDO_AIR_YARDS;
        f[index("racr")] = racrDenominator > 0
                ? (longRecYd + RACR_PSEUDO_AIR_YARDS * priors.racr(c.position)) / racrDenominator
                : priors.racr(c.position);
    }

    private static double teamPassAir(List<TeamGame> th, double halfLife, Priors priors) {
        double sum = 0;
        double total = 0;
        int n = th.size();
        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(th.get(i).passAirYards())) {
                double w = History.weight(n - 1 - i, halfLife);
                sum += w * th.get(i).passAirYards();
                total += w;
            }
        }
        return total > 0 ? sum / total : priors.teamPassAir();
    }

    /**
     * The league's scoring environment this season so far, per quantity, as a multiple of the
     * training seasons' level (v2b).
     *
     * <pre>
     * env:q = ((Σ league q over this season's weeks before N + K·π_q) / (team-games so far + K)) / π_q
     * </pre>
     *
     * π_q is the training seasons' per-team-game mean and K is
     * {@link #ENVIRONMENT_PSEUDO_TEAM_GAMES}. Week 1 is exactly 1. It reads
     * {@link Case#leagueHistory} -- already cut at week N by {@link Timeline#asOf} -- and only
     * the weeks of the season being projected, never another season and never week N.
     */
    static double[] environment(Case c, Priors priors) {
        double[] sum = new double[Quantity.COUNT];
        double games = 0;
        for (LeagueWeek week : c.leagueHistory) {
            if (week.season() == c.season) {
                games += week.teamGames();
                for (int q = 0; q < Quantity.COUNT; q++) {
                    sum[q] += week.totals()[q];
                }
            }
        }
        double k = ENVIRONMENT_PSEUDO_TEAM_GAMES;
        double[] env = new double[Quantity.COUNT];
        for (int q = 0; q < Quantity.COUNT; q++) {
            double base = priors.leaguePerTeamGame(q);
            env[q] = base == 0 ? 1 : ((sum[q] + k * base) / (games + k)) / base;
        }
        return env;
    }

    /**
     * How generous this week's opponent has been to the player's position, as a multiple of
     * the league: 1.2 means it allowed 20% more of that stat per game than an average
     * defense. Weighted like the player's own history and shrunk toward 1.
     */
    private static double[] opponentFactor(Case c, Priors priors, double halfLife) {
        int p = Priors.POSITIONS.indexOf(c.position);
        List<DefenseGame> games = c.opponentHistory;
        int n = games.size();
        double[] sum = new double[Quantity.COUNT];
        double total = 0;
        for (int i = 0; i < n; i++) {
            double w = History.weight(n - 1 - i, halfLife);
            double[] allowed = games.get(i).allowed()[p];
            for (int q = 0; q < Quantity.COUNT; q++) {
                sum[q] += w * allowed[q];
            }
            total += w;
        }
        double[] factor = new double[Quantity.COUNT];
        for (int q = 0; q < Quantity.COUNT; q++) {
            double league = priors.allowed(c.position, q);
            factor[q] = league == 0 ? 1
                    : (sum[q] + OPPONENT_PSEUDO_GAMES * league) / (total + OPPONENT_PSEUDO_GAMES) / league;
        }
        return factor;
    }

    /** The team's weighted per-game volume: 0 pass attempts, 1 carries, 2 targets. */
    private static double[] teamVolume(List<TeamGame> th, double halfLife, Priors priors) {
        if (th.isEmpty()) {
            return new double[] {priors.teamVolume(0), priors.teamVolume(1), priors.teamVolume(2)};
        }
        double[] sum = new double[3];
        double total = 0;
        int n = th.size();
        for (int i = 0; i < n; i++) {
            double w = History.weight(n - 1 - i, halfLife);
            TeamGame game = th.get(i);
            sum[0] += w * game.passAtt();
            sum[1] += w * game.rushAtt();
            sum[2] += w * game.targets();
            total += w;
        }
        return new double[] {sum[0] / total, sum[1] / total, sum[2] / total};
    }
}
