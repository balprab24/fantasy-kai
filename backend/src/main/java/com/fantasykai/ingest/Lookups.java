package com.fantasykai.ingest;

import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** The two id lookups the availability ingestors share: gsis id to player, abbreviation to team. */
final class Lookups {

    private Lookups() {}

    /** Players by nflverse gsis id -- the only key a feed row is tied to a player by, never a name. */
    static Map<String, Long> players(JdbcTemplate jdbc) {
        Map<String, Long> players = new HashMap<>();
        jdbc.query("SELECT gsis_id, id FROM players WHERE gsis_id IS NOT NULL",
                rs -> {
                    players.put(rs.getString(1), rs.getLong(2));
                });
        return players;
    }

    static Map<String, Integer> teams(JdbcTemplate jdbc) {
        Map<String, Integer> teams = new HashMap<>();
        jdbc.query("SELECT abbr, id FROM teams", rs -> {
            teams.put(rs.getString(1), rs.getInt(2));
        });
        return teams;
    }
}
