package com.fantasykai.projection;

import com.fantasykai.scoring.StatKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A tiny, hand-checkable league for the unit tests: two teams playing each other every week
 * and a few players, built straight into {@link BacktestData} with no database.
 */
final class Fixture {

    static final int HOME = 1;
    static final int AWAY = 2;

    private final Map<Long, List<PlayerGame>> players = new TreeMap<>();
    private final Map<Long, Game> games = new TreeMap<>();
    private final Map<String, TeamGame> teamGames = new TreeMap<>();

    /** One game per week between the two teams, with a line, every week in the range. */
    Fixture schedule(int season, int fromWeek, int toWeek) {
        for (int week = fromWeek; week <= toWeek; week++) {
            long id = season * 100L + week;
            games.put(id, new Game(id, season, week, HOME, AWAY, 3.0, 45.0, true));
        }
        return this;
    }

    /** A stat line for a player: receptions, receiving yards and targets, everything else 0. */
    Fixture wr(long player, int team, int season, int week, double rec, double recYd, double targets) {
        double[] line = new double[Quantity.COUNT];
        line[Quantity.of(StatKey.REC)] = rec;
        line[Quantity.of(StatKey.REC_YD)] = recYd;
        line[Quantity.TARGETS] = targets;
        return row(player, "WR", team, season, week, line);
    }

    /** Sets a team's volume for one game; unset games default to 30 / 25 / 30. */
    Fixture volume(int team, int season, int week, int passAtt, int rushAtt, int targets) {
        long gameId = season * 100L + week;
        teamGames.put(gameId + ":" + team, new TeamGame(team, gameId, season, week, passAtt, rushAtt, targets));
        return this;
    }

    Fixture row(long player, String position, int team, int season, int week, double[] line) {
        long gameId = season * 100L + week;
        TeamGame t = teamGames.computeIfAbsent(gameId + ":" + team,
                k -> new TeamGame(team, gameId, season, week, 30, 25, 30));
        players.computeIfAbsent(player, id -> new ArrayList<>()).add(
                new PlayerGame(player, position, gameId, season, week, team, 80.0, line, 0, t));
        return this;
    }

    BacktestData build() {
        // Every scheduled game gets both teams' volume, whether or not a player row exists.
        for (Game g : games.values()) {
            for (int team : new int[] {HOME, AWAY}) {
                teamGames.computeIfAbsent(g.id() + ":" + team,
                        k -> new TeamGame(team, g.id(), g.season(), g.week(), 30, 25, 30));
            }
        }
        Map<Long, Timeline<PlayerGame>> timelines = new TreeMap<>();
        players.forEach((id, rows) -> timelines.put(id, new Timeline<>(rows)));
        Map<Integer, List<TeamGame>> byTeam = new TreeMap<>();
        teamGames.values().forEach(t -> byTeam.computeIfAbsent(t.teamId(), x -> new ArrayList<>()).add(t));
        Map<Integer, Timeline<TeamGame>> teams = new TreeMap<>();
        byTeam.forEach((id, rows) -> teams.put(id, new Timeline<>(rows)));

        Map<Integer, List<DefenseGame>> byDefense = new TreeMap<>();
        for (TeamGame t : teamGames.values()) {
            int defense = t.teamId() == HOME ? AWAY : HOME;
            double[][] allowed = new double[Priors.POSITIONS.size()][Quantity.COUNT];
            for (List<PlayerGame> rows : players.values()) {
                for (PlayerGame r : rows) {
                    if (r.gameId() == t.gameId() && r.teamId() == t.teamId()) {
                        for (int q = 0; q < Quantity.COUNT; q++) {
                            allowed[Priors.POSITIONS.indexOf(r.position())][q] += r.line()[q];
                        }
                    }
                }
            }
            byDefense.computeIfAbsent(defense, x -> new ArrayList<>()).add(
                    new DefenseGame(defense, t.gameId(), t.season(), t.week(), allowed));
        }
        Map<Integer, Timeline<DefenseGame>> defenses = new TreeMap<>();
        byDefense.forEach((id, rows) -> defenses.put(id, new Timeline<>(rows)));

        Map<Integer, List<Game>> byWeek = new TreeMap<>();
        games.values().forEach(g -> byWeek.computeIfAbsent(g.key(), k -> new ArrayList<>()).add(g));
        return new BacktestData(timelines, teams, defenses, games, byWeek, List.of());
    }
}
