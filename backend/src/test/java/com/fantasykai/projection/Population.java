package com.fantasykai.projection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Who gets projected and scored, pinned before any method runs.
 *
 * <p>The population moves MAE more than any weight does (north-star Phase 6 brief), so it is
 * defined from pre-game information only and is identical for every method. Ranking by
 * trailing opportunity rather than by any candidate's projection means no method can shape
 * the set it is graded on.
 *
 * <ul>
 *   <li><b>P</b> -- per week and position, the top QB 32 / RB 64 / WR 96 / TE 32 by trailing
 *       opportunity, scaled by the teams playing, among players who have at least one prior
 *       game and a stored row that week. Conditional on playing.</li>
 *   <li><b>P0</b> -- the same ranking among players who appeared in their team's previous
 *       game and whose team plays; a player who then did not play counts as a zero line. Every
 *       point projected for him is error that knowing he was out would remove: that, not the
 *       gap from P (a different set of players), is what availability costs. Week 1 also
 *       counts offseason retirements and releases.</li>
 *   <li><b>P-all</b> -- every player with history and a stored row: the board's
 *       population.</li>
 * </ul>
 */
final class Population {

    static final Map<String, Integer> TOP_N = Map.of("QB", 32, "RB", 64, "WR", 96, "TE", 32);

    /** What building a season's population skipped or noticed, counted rather than dropped. */
    record Counts(int season, int weeks, int p, int p0, int p0DidNotPlay, int pAll,
            int debuts, int teamChanges, int noLine) {}

    record Season(List<Case> cases, Counts counts) {

        List<Case> where(Predicate<Case> filter) {
            return cases.stream().filter(filter).toList();
        }
    }

    private Population() {}

    static Season build(BacktestData data, int season) {
        List<Case> all = new ArrayList<>();
        int weeks = 0;
        int debuts = 0;
        int teamChanges = 0;
        for (int week = 1; week <= 22; week++) {
            List<Game> played = data.gamesIn(season, week).stream().filter(Game::played).toList();
            if (played.isEmpty()) {
                continue;
            }
            weeks++;
            Map<Integer, Game> byTeam = new HashMap<>();
            for (Game game : played) {
                byTeam.put(game.homeTeamId(), game);
                byTeam.put(game.awayTeamId(), game);
            }

            List<Case> thisWeek = new ArrayList<>();
            Map<Case, Boolean> eligibleP0 = new LinkedHashMap<>();
            for (Map.Entry<Long, Timeline<PlayerGame>> entry : data.players().entrySet()) {
                Timeline<PlayerGame> timeline = entry.getValue();
                PlayerGame actual = timeline.at(season, week).orElse(null);
                List<PlayerGame> history = timeline.asOf(season, week);
                if (history.isEmpty()) {
                    if (actual != null) {
                        debuts++;
                    }
                    continue;
                }
                PlayerGame last = history.get(history.size() - 1);
                boolean lastTeamPlays = byTeam.containsKey(last.teamId());
                List<TeamGame> lastTeamHistory = data.teams().get(last.teamId()).asOf(season, week);
                boolean playedTeamsLastGame = !lastTeamHistory.isEmpty()
                        && lastTeamHistory.get(lastTeamHistory.size() - 1).gameId() == last.gameId();
                boolean p0 = lastTeamPlays && playedTeamsLastGame;
                if (actual == null && !p0) {
                    continue;
                }

                int team = actual != null ? actual.teamId() : last.teamId();
                Game game = actual != null ? data.games().get(actual.gameId()) : byTeam.get(team);
                if (actual != null && actual.teamId() != last.teamId()) {
                    teamChanges++;
                }
                int opponent = game.homeTeamId() == team ? game.awayTeamId() : game.homeTeamId();
                Case c = new Case(entry.getKey(), last.position(), season, week, team, game,
                        actual, history, data.teams().get(team).asOf(season, week),
                        data.defenses().get(opponent).asOf(season, week));
                c.inPAll = actual != null;
                thisWeek.add(c);
                eligibleP0.put(c, p0);
            }

            int teamsPlaying = byTeam.size();
            for (Map.Entry<String, Integer> topN : TOP_N.entrySet()) {
                int n = (int) Math.round(topN.getValue() * teamsPlaying / 32.0);
                String position = topN.getKey();
                rank(thisWeek, c -> position.equals(c.position) && c.actual != null, n)
                        .forEach(c -> c.inP = true);
                rank(thisWeek, c -> position.equals(c.position) && eligibleP0.get(c), n)
                        .forEach(c -> c.inP0 = true);
            }
            all.addAll(thisWeek);
        }

        int p = 0;
        int p0 = 0;
        int p0DidNotPlay = 0;
        int pAll = 0;
        int noLine = 0;
        for (Case c : all) {
            p += c.inP ? 1 : 0;
            p0 += c.inP0 ? 1 : 0;
            p0DidNotPlay += c.inP0 && c.actual == null ? 1 : 0;
            pAll += c.inPAll ? 1 : 0;
            noLine += (c.inP || c.inP0 || c.inPAll) && !c.game.hasLine() ? 1 : 0;
        }
        return new Season(all,
                new Counts(season, weeks, p, p0, p0DidNotPlay, pAll, debuts, teamChanges, noLine));
    }

    /** Highest trailing opportunity first; player id breaks ties so the set is stable. */
    private static List<Case> rank(List<Case> cases, Predicate<Case> filter, int n) {
        return cases.stream()
                .filter(filter)
                .sorted(Comparator.comparingDouble((Case c) -> -c.trailingOpportunity)
                        .thenComparingLong(c -> c.playerId))
                .limit(n)
                .toList();
    }
}
