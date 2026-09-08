package com.fantasykai.api;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A small, deterministic 2025 season for the API tests.
 *
 * <p>The headline row is real: Josh Allen's week 1 line is the one read out of
 * the loaded database (394 pass yd, 2 pass TD, 30 rush yd, 2 rush TD), and it is
 * the same line §6 hand-computes to 38.76. Fixtures here are measured rather than
 * invented, for the same reason the nflverse CSV fixtures are.
 *
 * <p>Two custom profiles exist only to exercise properties the four presets
 * cannot: the presets' rates all land on exact hundredths, so nothing about them
 * can show whether rounding happens once or thirteen times, and none of them
 * carries a bonus, so nothing about them can show whether a bonus is per game or
 * per season.
 */
final class ApiFixture {

    /** A rate that produces thirds, so rounded-sum and sum-of-rounded diverge. */
    private static final String THIRDS_RULES = """
            {"version":1,"base":{"pass_yd":0.033}}
            """;

    /** 100+ rushing yards is a per-game achievement; this proves we treat it as one. */
    private static final String CENTURY_RULES = """
            {"version":1,"base":{"rush_yd":0.1},
             "bonuses":[{"stat":"rush_yd","gte":100,"points":3}]}
            """;

    private ApiFixture() {}

    static Seeded seed(JdbcTemplate jdbc) {
        int buf = team(jdbc, "BUF", "Buffalo Bills");
        int bal = team(jdbc, "BAL", "Baltimore Ravens");

        // Weeks 1-6 regular season, plus a postseason week to prove the rankings
        // exclude it and the game log does not.
        long[] regular = new long[7];
        for (int week = 1; week <= 6; week++) {
            regular[week] = game(jdbc, week, "REG", buf, bal);
        }
        long postseason = game(jdbc, 20, "POST", buf, bal);

        long allen = player(jdbc, "00-0034857", "Josh Allen", "QB", buf);
        // Same name, different player. 832 name collisions exist in the real
        // players table; this is the one an interviewer would recognise.
        long allenCenter = player(jdbc, "00-0035001", "Josh Allen", "C", bal);
        long receiver = player(jdbc, "00-0036322", "Test Receiver", "WR", buf);
        long tightEnd = player(jdbc, "00-0036323", "Test Tight End", "TE", buf);
        long thirds = player(jdbc, "00-0036324", "Test Thirds", "QB", buf);
        long century = player(jdbc, "00-0036325", "Test Century", "RB", buf);

        // The 38.76 line.
        passRush(jdbc, allen, regular[1], buf, 394, 2, 30, 2);
        // A second regular week, so a season total is a sum of more than one.
        passRush(jdbc, allen, regular[2], buf, 200, 1, 10, 0);
        // Postseason: must not reach a ranking.
        passRush(jdbc, allen, postseason, buf, 300, 3, 40, 1);

        // 100 * 0.033 = 3.3; 101 * 0.033 = 3.333. Rounded sum 9.97, sum of
        // rounded 9.96 -- the invariant in one player.
        pass(jdbc, thirds, regular[1], buf, 100);
        pass(jdbc, thirds, regular[2], buf, 101);
        pass(jdbc, thirds, regular[3], buf, 101);

        // Two century games and one short of it: 3 bonuses' worth is 6, not 3.
        rush(jdbc, century, regular[1], buf, 100);
        rush(jdbc, century, regular[2], buf, 100);
        rush(jdbc, century, regular[3], buf, 50);

        // Receptions, so the PPR presets separate and TE premium bites.
        rec(jdbc, receiver, regular[1], buf, 8, 76);
        rec(jdbc, tightEnd, regular[1], buf, 8, 76);

        // Late weeks only, so last4 includes this and the week-1 rows drop out.
        rec(jdbc, receiver, regular[5], buf, 5, 50);
        rec(jdbc, receiver, regular[6], buf, 5, 50);

        return new Seeded(allen, allenCenter, receiver, tightEnd, thirds, century,
                profile(jdbc, "Thirds", THIRDS_RULES),
                profile(jdbc, "Century Bonus", CENTURY_RULES));
    }

    private static int team(JdbcTemplate jdbc, String abbr, String name) {
        return jdbc.queryForObject(
                "INSERT INTO teams (abbr, name) VALUES (?, ?) RETURNING id", Integer.class, abbr, name);
    }

    private static long game(JdbcTemplate jdbc, int week, String type, int home, int away) {
        return jdbc.queryForObject("""
                INSERT INTO games (nflverse_game_id, season, week, season_type, home_team_id, away_team_id)
                VALUES (?, 2025, ?, ?, ?, ?) RETURNING id
                """, Long.class, "2025_%02d_BAL_BUF".formatted(week), week, type, home, away);
    }

    private static long player(JdbcTemplate jdbc, String gsis, String name, String position, int team) {
        return jdbc.queryForObject("""
                INSERT INTO players (gsis_id, full_name, position, team_id, status)
                VALUES (?, ?, ?, ?, 'ACT') RETURNING id
                """, Long.class, gsis, name, position, team);
    }

    private static long profile(JdbcTemplate jdbc, String name, String rules) {
        return jdbc.queryForObject("""
                INSERT INTO scoring_profiles (user_id, name, rules, is_preset)
                VALUES (NULL, ?, ?::jsonb, FALSE) RETURNING id
                """, Long.class, name, rules);
    }

    private static void passRush(JdbcTemplate jdbc, long player, long game, int team,
            int passYd, int passTd, int rushYd, int rushTd) {
        jdbc.update("""
                INSERT INTO player_game_stats
                    (player_id, game_id, season, week, team_id, snap_pct,
                     pass_yd, pass_td, rush_yd, rush_td)
                SELECT ?, ?, g.season, g.week, ?, 100.00, ?, ?, ?, ?
                FROM games g WHERE g.id = ?
                """, player, game, team, passYd, passTd, rushYd, rushTd, game);
    }

    private static void pass(JdbcTemplate jdbc, long player, long game, int team, int passYd) {
        passRush(jdbc, player, game, team, passYd, 0, 0, 0);
    }

    private static void rush(JdbcTemplate jdbc, long player, long game, int team, int rushYd) {
        passRush(jdbc, player, game, team, 0, 0, rushYd, 0);
    }

    private static void rec(JdbcTemplate jdbc, long player, long game, int team, int rec, int recYd) {
        jdbc.update("""
                INSERT INTO player_game_stats
                    (player_id, game_id, season, week, team_id, snap_pct, rec, rec_yd)
                SELECT ?, ?, g.season, g.week, ?, 85.50, ?, ?
                FROM games g WHERE g.id = ?
                """, player, game, team, rec, recYd, game);
    }

    /** Seeded ids. Preset ids 1-4 come from {@code V3__seed_scoring_presets.sql}. */
    record Seeded(long allen, long allenCenter, long receiver, long tightEnd,
            long thirds, long century, long thirdsProfile, long centuryProfile) {

        static final long STANDARD = 1;
        static final long HALF_PPR = 2;
        static final long FULL_PPR = 3;
        static final long TE_PREMIUM = 4;
    }
}
