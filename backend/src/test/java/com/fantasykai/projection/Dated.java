package com.fantasykai.projection;

/**
 * Anything that happened in a regular-season week, ordered by season and then week.
 *
 * <p>Weeks only order within the regular season -- 2020's wild card is week 18 -- which
 * is why everything this package reads is filtered to {@code season_type = 'REG'} first.
 */
interface Dated {

    int season();

    int week();

    default int key() {
        return key(season(), week());
    }

    static int key(int season, int week) {
        return season * 100 + week;
    }
}
