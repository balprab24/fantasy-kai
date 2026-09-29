package com.fantasykai.api;

/**
 * One regular-season game inside a {@link CareerSeason}: enough to draw a week
 * on a chart and say where it stood.
 *
 * @param points  the same number the game log shows for this week
 * @param posRank place among every player at his position that week; players
 *                on equal points (to the cent) share a place. Null for a
 *                position v1 does not rank
 */
public record CareerWeek(int week, String opponent, Boolean home, double points,
        Integer posRank) {}
