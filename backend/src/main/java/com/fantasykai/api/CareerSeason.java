package com.fantasykai.api;

import com.fantasykai.query.Usage;
import java.util.List;
import java.util.Map;

/**
 * One regular season of a career.
 *
 * <p>Regular season only, like a ranking, so that {@code points},
 * {@code pointsPerGame} and {@code gamesPlayed} are the same three numbers the
 * board shows for this player and season -- not the game log's, which include
 * the playoffs because a log is a record of what happened.
 *
 * @param teams         every team he played for that season, in week order
 * @param age           on 1 September of the season; null without a birth date
 * @param points        rounded sum of the unrounded weeks, summed in week order
 * @param posRank       place among players at his position by season points --
 *                      the positional rank the board derives on a season
 *                      board, by construction. Null for a position v1 does not
 *                      rank (K, OL, defence)
 * @param stats         the scorable stat line summed over the season
 * @param usage         volume summed over the season
 * @param weeks         each game, in week order
 */
public record CareerSeason(int season, List<String> teams, Integer age, int gamesPlayed,
        double points, double pointsPerGame, Integer posRank, Map<String, Double> stats,
        Usage usage, List<CareerWeek> weeks) {}
