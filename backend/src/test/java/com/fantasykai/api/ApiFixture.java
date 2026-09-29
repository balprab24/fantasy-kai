package com.fantasykai.api;

import com.fantasykai.auth.JwtService;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
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

    /**
     * A client that calls the API as a signed-in member -- every read needs an
     * account since 2026-09-29. Same root URI and message converters as the
     * autowired anonymous client, so responses decode exactly as before.
     *
     * <p>The token is minted with the application's own {@link JwtService}
     * rather than earned through {@code /auth/login}: signing in goes through
     * the Redis-backed rate limiter, and these classes run without Redis on
     * purpose. A Redis container would mean another Spring context, and context
     * teardown is already the largest term in the build (CLAUDE.md).
     */
    static TestRestTemplate asMember(TestRestTemplate anonymous, JdbcTemplate jdbc, JwtService jwt) {
        Long member = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash) VALUES ('fixture-member@example.com', 'not-a-hash')
                ON CONFLICT ON CONSTRAINT uq_users_email DO UPDATE SET email = EXCLUDED.email
                RETURNING id
                """, Long.class);
        return new TestRestTemplate(new RestTemplateBuilder()
                .rootUri(anonymous.getRootUri())
                .messageConverters(anonymous.getRestTemplate().getMessageConverters())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issue(member)));
    }

    static Seeded seed(JdbcTemplate jdbc) {
        int buf = team(jdbc, "BUF", "Buffalo Bills");
        int bal = team(jdbc, "BAL", "Baltimore Ravens");

        // Weeks 1-6 regular season, plus a postseason week to prove the rankings
        // exclude it and the game log does not. nflverse names the round, never
        // "POST": a playoff game is WC, DIV, CON or SB (measured 2026-09-28).
        long[] regular = new long[7];
        for (int week = 1; week <= 6; week++) {
            regular[week] = game(jdbc, 2025, week, "REG", buf, bal);
        }
        long postseason = game(jdbc, 2025, 20, "DIV", buf, bal);

        // Identity for display. Allen's ESPN id and birth date are his real ones;
        // the receiver's id is the "4262921.0" shape an R export can write, which
        // must reach the API as no id rather than as a broken image URL.
        long allen = player(jdbc, "00-0034857", "Josh Allen", "QB", buf,
                "{\"espn\":\"3918298\"}", "1996-05-21");
        // Same name, different player. 832 name collisions exist in the real
        // players table; this is the one an interviewer would recognise.
        long allenCenter = player(jdbc, "00-0035001", "Josh Allen", "C", bal);
        long receiver = player(jdbc, "00-0036322", "Test Receiver", "WR", buf,
                "{\"espn\":\"4262921.0\"}", null);
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

    /**
     * A second, older season for the career endpoint: {@code seedHistory} is
     * called only by {@code RankingsTests}, and every API test class has its own
     * container, so ReadApiTests' "no 2024 stat lines" stays true -- and 2023 is
     * used rather than 2024 so it would stay true in a shared one too.
     *
     * <p>Everything a career can get wrong between two seasons: a playoff game it
     * must leave out, a player traded mid-season (so {@code home} flips and he has
     * two teams), a second receiver whose season total ties the first's (the
     * id tiebreak) and whose week ties too (a shared weekly place), a third who
     * leads on points per game but trails on total -- without him, ranking a
     * season by per-game instead of by points passed every test -- volume stats,
     * a season the second receiver played only in the playoffs, and a line for
     * the center Josh Allen, whose position v1 does not rank.
     */
    static History seedHistory(JdbcTemplate jdbc, Seeded seeded) {
        int buf = teamId(jdbc, "BUF");
        int bal = teamId(jdbc, "BAL");
        long week1 = game(jdbc, 2023, 1, "REG", buf, bal);
        long week2 = game(jdbc, 2023, 2, "REG", buf, bal);
        long week3 = game(jdbc, 2023, 3, "REG", bal, buf); // BAL at home
        long wildCard = game(jdbc, 2023, 19, "WC", buf, bal);
        // A season the tied receiver reached only in January: a game log, no
        // regular-season line (seven such player-seasons in the real data).
        long wildCard2022 = game(jdbc, 2022, 19, "WC", buf, bal);

        long tiedReceiver = player(jdbc, "00-0036330", "Test Receiver Two", "WR", buf);
        long oneGameReceiver = player(jdbc, "00-0036331", "Test Receiver Three", "WR", buf);

        // Allen: two regular weeks and a playoff game the career must drop.
        passRush(jdbc, seeded.allen(), week1, buf, 250, 2, 20, 0);
        passRush(jdbc, seeded.allen(), week3, buf, 300, 3, 10, 1);
        passRush(jdbc, seeded.allen(), wildCard, buf, 280, 2, 15, 0);
        usage(jdbc, seeded.allen(), week1, 35, 24, 4, 0);
        usage(jdbc, seeded.allen(), week3, 40, 29, 3, 0);

        // The receiver starts at BUF and is traded to BAL, who host week 3.
        rec(jdbc, seeded.receiver(), week1, buf, 6, 60);
        rec(jdbc, seeded.receiver(), week3, bal, 4, 40);
        usage(jdbc, seeded.receiver(), week1, 0, 0, 0, 9);
        usage(jdbc, seeded.receiver(), week3, 0, 0, 1, 7);

        // Same 2023 total as the receiver under every preset (10 catches, 100
        // yards), and the same week 1 line exactly.
        rec(jdbc, tiedReceiver, week1, buf, 6, 60);
        rec(jdbc, tiedReceiver, week2, buf, 4, 40);
        rec(jdbc, tiedReceiver, wildCard2022, buf, 3, 30);

        // One game, 9-90: the best per-game WR of 2023 under every preset and the
        // lowest total (Half PPR 13.5 against the others' 15.0).
        rec(jdbc, oneGameReceiver, week2, buf, 9, 90);

        rec(jdbc, seeded.allenCenter(), week2, bal, 1, 4);

        return new History(tiedReceiver, oneGameReceiver);
    }

    record History(long tiedReceiver, long oneGameReceiver) {}

    private static int teamId(JdbcTemplate jdbc, String abbr) {
        return jdbc.queryForObject("SELECT id FROM teams WHERE abbr = ?", Integer.class, abbr);
    }

    private static long game(JdbcTemplate jdbc, int season, int week, String type, int home, int away) {
        return jdbc.queryForObject("""
                INSERT INTO games (nflverse_game_id, season, week, season_type, home_team_id, away_team_id)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, "%d_%02d_BAL_BUF".formatted(season, week), season, week, type,
                home, away);
    }

    private static long player(JdbcTemplate jdbc, String gsis, String name, String position, int team) {
        return player(jdbc, gsis, name, position, team, "{}", null);
    }

    private static long player(JdbcTemplate jdbc, String gsis, String name, String position, int team,
            String externalIds, String birthDate) {
        return jdbc.queryForObject("""
                INSERT INTO players (gsis_id, full_name, position, team_id, status, external_ids, birth_date)
                VALUES (?, ?, ?, ?, 'ACT', ?::jsonb, ?::date) RETURNING id
                """, Long.class, gsis, name, position, team, externalIds, birthDate);
    }

    /** Volume on a row already inserted: attempts, completions, carries, targets. */
    private static void usage(JdbcTemplate jdbc, long player, long game,
            int passAtt, int passCmp, int rushAtt, int targets) {
        jdbc.update("""
                UPDATE player_game_stats SET pass_att = ?, pass_cmp = ?, rush_att = ?, targets = ?
                WHERE player_id = ? AND game_id = ?
                """, passAtt, passCmp, rushAtt, targets, player, game);
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
