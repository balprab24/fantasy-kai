package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.auth.JwtService;
import com.fantasykai.ingest.InjuryReportIngestor;
import com.fantasykai.ingest.RosterWeekIngestor;
import com.fantasykai.projections.ProjectionProvider;
import com.fantasykai.projections.ProjectionRepository;
import com.fantasykai.projections.ProjectionRepository.ProjectedRow;
import com.fantasykai.projections.ProjectionSource;
import com.fantasykai.projections.RawProjection;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.ScoringProfiles;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The projection endpoints, through HTTP, exactly as they behave today.
 *
 * <p>Slice 5 changes this contract on purpose (the board envelope, a resolver-derived target, one
 * line per bonus, a preset strip). These tests are written first so each of those changes shows
 * up as a specific, reviewed edit to a specific assertion instead of as a silent difference. The
 * ones about <em>meaning</em> -- the score is the engine's, an Out player is not ranked, another
 * member's profile is a 404 -- must survive every later slice unchanged. The ones about
 * <em>shape</em> ({@link #theWireShapeIsPinned}) are the ones later slices rewrite deliberately.
 *
 * <p>Projections are written to season 2030, with kickoffs set relative to a fixed clock
 * ({@link FixedClock}), and the whole projection table is truncated before every test, so no
 * test sees another's rows or schedule.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "fantasykai.projections.target-cache-seconds=0")
class ProjectionApiTests {

    /** Wednesday 2030-11-20 noon UTC: inside the 2030 season, in a world with no 2030 schedule yet. */
    private static final Instant NOW = Instant.parse("2030-11-20T12:00:00Z");

    /**
     * The application's clock, fixed. The season the resolver asks about, its 48-hour window, the
     * rows' lock times and every token's lifetime follow it, so every time-based assertion here
     * is exact and none depends on how long the suite takes.
     */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final int SEASON = 2030;
    private static final int WEEK = 3;
    private static final String BOARD = "/api/v1/projections";
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private static ApiFixture.Seeded seeded;
    private static int buf;
    private static int bal;
    private static int mia;

    @Autowired private TestRestTemplate anonymous;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private ProjectionProvider provider;
    @Autowired private ProjectionRepository repository;
    @Autowired private ScoringProfiles profiles;

    private TestRestTemplate rest;
    private long memberId;

    @BeforeAll
    static void seed(@Autowired JdbcTemplate jdbc) {
        seeded = ApiFixture.seed(jdbc);
        buf = jdbc.queryForObject("SELECT id FROM teams WHERE abbr = 'BUF'", Integer.class);
        bal = jdbc.queryForObject("SELECT id FROM teams WHERE abbr = 'BAL'", Integer.class);
        mia = jdbc.queryForObject("INSERT INTO teams (abbr, name) VALUES ('MIA', 'Miami Dolphins') RETURNING id",
                Integer.class);
    }

    @BeforeEach
    void signInAndClear() {
        clear();
        memberId = ApiFixture.memberId(jdbc, "fixture-member@example.com");
        rest = ApiFixture.asMember(anonymous, jdbc, jwt, memberId);
    }

    // ------------------------------------------------------------------ who may ask

    @Test
    void bothRoutesNeedAnAccount() {
        for (String path : List.of(BOARD + "?profileId=1",
                "/api/v1/players/" + seeded.allen() + "/projection?profileId=1")) {
            ResponseEntity<String> response = anonymous.getForEntity(path, String.class);
            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getHeaders().getContentType()).as(path)
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        }
    }

    /**
     * Not 403: a profile that is not yours is indistinguishable from one that does not exist. And
     * not only when the profile is cold -- {@code ScoringProfiles} caches compiled rulesets by id,
     * so the stranger asks after the owner has warmed the entry.
     */
    @Test
    void anotherMembersProfileIsA404OnBothRoutesEvenWhenItIsWarm() {
        long ownerId = ApiFixture.memberId(jdbc, "owner@example.com");
        long strangerId = ApiFixture.memberId(jdbc, "stranger@example.com");
        TestRestTemplate owner = ApiFixture.asMember(anonymous, jdbc, jwt, ownerId);
        TestRestTemplate stranger = ApiFixture.asMember(anonymous, jdbc, jwt, strangerId);
        long warm = ownedProfile(ownerId, "Warm");
        long cold = ownedProfile(ownerId, "Cold");

        // The owner reads both routes: 200, and the compiled ruleset is now cached under its id.
        assertThat(status(owner, BOARD + "?profileId=" + warm)).isEqualTo(200);
        assertThat(status(owner, detailPath(seeded.allen(), warm))).isEqualTo(200);

        for (long profile : List.of(warm, cold)) {
            assertThat(status(stranger, BOARD + "?profileId=" + profile))
                    .as("board, profile %d (warm=%b)", profile, profile == warm).isEqualTo(404);
            assertThat(status(stranger, detailPath(seeded.allen(), profile)))
                    .as("player, profile %d (warm=%b)", profile, profile == warm).isEqualTo(404);
        }
        // The presets belong to nobody and to everybody.
        assertThat(status(stranger, BOARD + "?profileId=" + ApiFixture.Seeded.STANDARD)).isEqualTo(200);
    }

    // ------------------------------------------------------------------ what a number means

    /**
     * The number a member sees is {@code ScoringEngine}'s, rounded once, for the stored line under
     * the ruleset asked for -- on both routes, under every preset and the two custom rulesets
     * (one with a bonus). Fractional lines on purpose: whole numbers would hide a premature round.
     */
    @Test
    void pointsAreTheEnginesScoreRoundedOnceUnderEveryProfile() {
        long game = game(WEEK, buf, bal, inDays(2));
        long qb = player("Quentin Quarterback", "QB", buf);
        long wr = player("Wesley Receiver", "WR", buf);
        long te = player("Terrence End", "TE", buf);
        long rb = player("Rashad Runner", "RB", buf);
        store(WEEK,
                new Proj(qb, game, buf, Map.of(StatKey.PASS_YD, 251.37, StatKey.PASS_TD, 1.62,
                        StatKey.PASS_INT, 0.83, StatKey.RUSH_YD, 24.45, StatKey.RUSH_TD, 0.21)),
                new Proj(wr, game, buf, Map.of(StatKey.REC, 5.43, StatKey.REC_YD, 61.84, StatKey.REC_TD, 0.41)),
                new Proj(te, game, buf, Map.of(StatKey.REC, 4.2, StatKey.REC_YD, 41.1, StatKey.REC_TD, 0.3)),
                new Proj(rb, game, buf, Map.of(StatKey.RUSH_YD, 101.37, StatKey.REC, 2.2, StatKey.REC_YD, 14.2)));

        for (long profile : List.of(ApiFixture.Seeded.STANDARD, ApiFixture.Seeded.HALF_PPR,
                ApiFixture.Seeded.FULL_PPR, ApiFixture.Seeded.TE_PREMIUM, seeded.thirdsProfile(),
                seeded.centuryProfile())) {
            JsonNode board = getJson(rest, BOARD + "?profileId=" + profile + "&size=200");
            for (long id : List.of(qb, wr, te, rb)) {
                double expected = expectedPoints(id, profile);
                assertThat(rowOf(board, id).get("points").asDouble())
                        .as("board, player %d, profile %d", id, profile).isEqualTo(expected);
                assertThat(getJson(rest, detailPath(id, profile)).get("projection").get("points").asDouble())
                        .as("player route, player %d, profile %d", id, profile).isEqualTo(expected);
            }
        }
    }

    /**
     * What the explanation promises: the parts plus the bonuses that were reached add back to the
     * total, and a bonus pays <em>at</em> its threshold ({@code >=}), not past it. The century
     * profile pays 3 for 100+ rushing yards; the line is listed whether or not it was reached.
     */
    @Test
    void partsAndTheReachedBonusesAddUpToThePointsAndAThresholdPaysAtExactlyTheThreshold() {
        long game = game(WEEK, buf, bal, inDays(2));
        long atThreshold = player("Exactly Hundred", "RB", buf);
        long justShort = player("Just Short", "RB", buf);
        store(WEEK,
                new Proj(atThreshold, game, buf, Map.of(StatKey.RUSH_YD, 100.0)),
                new Proj(justShort, game, buf, Map.of(StatKey.RUSH_YD, 99.9)));
        JsonNode board = getJson(rest, BOARD + "?profileId=" + seeded.centuryProfile());

        JsonNode paid = rowOf(board, atThreshold);
        assertThat(paid.get("bonuses")).hasSize(1);
        assertBonus(paid.get("bonuses").get(0), "rush_yd", 100, 100.0, 3.0, true);
        assertThat(paid.get("points").asDouble()).isEqualTo(13.0);
        JsonNode missed = rowOf(board, justShort);
        assertThat(missed.get("bonuses")).as("a bonus not reached is still listed").hasSize(1);
        assertBonus(missed.get("bonuses").get(0), "rush_yd", 100, 99.9, 3.0, false);
        assertThat(missed.get("points").asDouble()).isEqualTo(9.99);

        for (JsonNode row : List.of(paid, missed)) {
            assertThat(ScoringEngine.roundForDisplay(partsPlusReachedBonuses(row)))
                    .as("parts + reached bonuses, rounded once").isEqualTo(row.get("points").asDouble());
        }
    }

    /**
     * Two bonuses on two stats: each is its own line, in the ruleset's order, with the line's own
     * value against its own threshold -- so a member sees which one a line crossed and how far the
     * other was. One line reaches the first and not the second, the other the reverse. A bonus on
     * a stat the line has none of is not a line: a receiver who never rushes is not told about the
     * rushing bonus.
     */
    @Test
    void theBonusesThatBearOnALineAreListedWithWhetherItReachedThem() {
        long owned = ownedProfile(memberId, "Two bonuses", """
                {"version":1,"base":{"rush_yd":0.1,"rec_yd":0.1},
                 "bonuses":[{"stat":"rush_yd","gte":100,"points":3},{"stat":"rec_yd","gte":100,"points":2.5}]}
                """);
        long game = game(WEEK, buf, bal, inDays(2));
        long runner = player("Runner Of A Hundred", "RB", buf);
        long catcher = player("Catcher Of A Hundred", "RB", buf);
        long receiver = player("Receiver Who Never Rushes", "WR", buf);
        store(WEEK, new Proj(runner, game, buf, Map.of(StatKey.RUSH_YD, 100.0, StatKey.REC_YD, 40.0)),
                new Proj(catcher, game, buf, Map.of(StatKey.RUSH_YD, 99.9, StatKey.REC_YD, 100.0)),
                new Proj(receiver, game, buf, Map.of(StatKey.REC_YD, 100.0)));
        JsonNode board = getJson(rest, BOARD + "?profileId=" + owned);

        JsonNode run = rowOf(board, runner);
        assertThat(run.get("bonuses")).hasSize(2);
        assertBonus(run.get("bonuses").get(0), "rush_yd", 100, 100.0, 3.0, true);
        assertBonus(run.get("bonuses").get(1), "rec_yd", 100, 40.0, 2.5, false);
        assertThat(run.get("points").asDouble()).isEqualTo(17.0);

        JsonNode catches = rowOf(board, catcher);
        assertThat(catches.get("bonuses")).hasSize(2);
        assertBonus(catches.get("bonuses").get(0), "rush_yd", 100, 99.9, 3.0, false);
        assertBonus(catches.get("bonuses").get(1), "rec_yd", 100, 100.0, 2.5, true);
        assertThat(catches.get("points").asDouble()).isEqualTo(22.49);

        JsonNode catchOnly = rowOf(board, receiver);
        assertThat(catchOnly.get("bonuses")).as("the rushing bonus does not bear on him").hasSize(1);
        assertBonus(catchOnly.get("bonuses").get(0), "rec_yd", 100, 100.0, 2.5, true);
        assertThat(catchOnly.get("points").asDouble()).isEqualTo(12.5);

        for (JsonNode row : List.of(run, catches, catchOnly)) {
            assertThat(ScoringEngine.roundForDisplay(partsPlusReachedBonuses(row)))
                    .as("parts + reached bonuses, rounded once").isEqualTo(row.get("points").asDouble());
        }
        // The same stored lines under a ruleset with no bonus: no bonus lines, and nothing else moved.
        JsonNode plain = getJson(rest, BOARD + "?profileId=" + ApiFixture.Seeded.STANDARD);
        assertThat(rowOf(plain, runner).get("bonuses")).isEmpty();
    }

    private static double partsPlusReachedBonuses(JsonNode row) {
        double sum = 0;
        for (JsonNode part : row.get("parts")) {
            sum += part.get("points").asDouble();
        }
        for (JsonNode bonus : row.get("bonuses")) {
            if (bonus.get("triggered").asBoolean()) {
                sum += bonus.get("points").asDouble();
            }
        }
        return sum;
    }

    private static void assertBonus(JsonNode bonus, String stat, int gte, double value, double points,
            boolean triggered) {
        assertThat(bonus.get("stat").asText()).isEqualTo(stat);
        assertThat(bonus.get("gte").asInt()).as("%s gte", stat).isEqualTo(gte);
        assertThat(bonus.get("value").asDouble()).as("%s value", stat).isEqualTo(value);
        assertThat(bonus.get("points").asDouble()).as("%s points", stat).isEqualTo(points);
        assertThat(bonus.get("triggered").asBoolean()).as("%s triggered", stat).isEqualTo(triggered);
    }

    // ------------------------------------------------------------------ the board's order

    /**
     * Unrounded points descending, then player id -- sorted before paging, so the order holds
     * across a page boundary. The third player's total is 10.004: it displays as 10.0, the same
     * as the other two, and still ranks first.
     */
    @Test
    void ordersByTheUnroundedTotalThenIdAcrossPages() {
        long game = game(WEEK, buf, bal, inDays(2));
        long tiedFirst = player("Tied A", "WR", buf);
        long tiedSecond = player("Tied B", "WR", buf);
        long higherUnrounded = player("Higher Unrounded", "WR", buf);
        store(WEEK,
                new Proj(tiedFirst, game, buf, Map.of(StatKey.REC_YD, 100.0)),
                new Proj(tiedSecond, game, buf, Map.of(StatKey.REC_YD, 100.0)),
                new Proj(higherUnrounded, game, buf, Map.of(StatKey.REC_YD, 100.04)));

        List<Long> order = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode content = getJson(rest, BOARD + "?profileId=" + ApiFixture.Seeded.STANDARD
                    + "&position=WR&size=1&page=" + page).get("page").get("content");
            assertThat(content).hasSize(1);
            order.add(content.get(0).get("playerId").asLong());
            assertThat(content.get(0).get("points").asDouble()).isEqualTo(10.0);
        }
        assertThat(order).containsExactly(higherUnrounded, tiedFirst, tiedSecond);
    }

    // ------------------------------------------------------------------ availability

    /**
     * Availability decides whether the board <em>ranks</em> a line, never the line: an Out
     * player and a reserve-list player are off the board, and both still have their projection
     * on the player route, scored. Questionable is ranked, and so is a player nothing is known
     * about.
     */
    @Test
    void anOutOrReservePlayerIsNotRankedButKeepsHisLine() {
        long game = game(WEEK, buf, bal, inDays(2));
        long out = player("Olly Out", "WR", buf);
        long reserve = player("Rory Reserve", "WR", buf);
        long questionable = player("Quinn Questionable", "WR", buf);
        long healthy = player("Hale Healthy", "WR", buf);
        long unknown = player("Una Unknown", "WR", buf);
        // One call: store() replaces the week's open rows, so five calls would leave one.
        store(WEEK, List.of(out, reserve, questionable, healthy, unknown).stream()
                .map(id -> new Proj(id, game, buf, Map.of(StatKey.REC_YD, 50.0 + id % 7))).toArray(Proj[]::new));
        roster(out, buf, "ACT");
        roster(reserve, buf, "RES");
        roster(questionable, buf, "ACT");
        roster(healthy, buf, "ACT");
        injury(out, buf, "Out");
        injury(questionable, buf, "Questionable");

        JsonNode board = getJson(rest, BOARD + "?profileId=" + ApiFixture.Seeded.STANDARD + "&position=WR");
        assertThat(rowOf(board, out)).as("Out is not ranked").isNull();
        assertThat(rowOf(board, reserve)).as("a reserve-list player is not ranked").isNull();
        assertThat(rowOf(board, questionable).get("availability").get("status").asText()).isEqualTo("QUESTIONABLE");
        assertThat(rowOf(board, healthy).get("availability").get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(rowOf(board, unknown).get("availability").get("status").asText()).isEqualTo("UNKNOWN");
        assertThat(board.get("page").get("total").asInt()).isEqualTo(3);

        for (long id : List.of(out, reserve)) {
            JsonNode detail = getJson(rest, detailPath(id, ApiFixture.Seeded.STANDARD));
            assertThat(detail.get("state").asText()).as("player %d", id).isEqualTo("PROJECTED");
            assertThat(detail.get("projection").get("points").asDouble()).as("his line is kept")
                    .isEqualTo(expectedPoints(id, ApiFixture.Seeded.STANDARD));
        }
        assertThat(getJson(rest, detailPath(out, ApiFixture.Seeded.STANDARD))
                .get("projection").get("availability").get("status").asText()).isEqualTo("OUT");
        assertThat(getJson(rest, detailPath(reserve, ApiFixture.Seeded.STANDARD))
                .get("projection").get("availability").get("status").asText()).isEqualTo("RESERVE");
    }

    // ------------------------------------------------------------------ states

    /** The schedule has a week to project and the job has stored nothing for it. */
    @Test
    void withAWeekToProjectAndNothingStoredTheBoardIsUnavailableAndAnUnknownPlayerIsA404() {
        game(WEEK, buf, bal, inDays(2));
        JsonNode board = getJson(rest, BOARD + "?profileId=" + ApiFixture.Seeded.HALF_PPR);
        assertThat(board.get("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat(board.get("page").get("content")).isEmpty();
        assertThat(getJson(rest, detailPath(seeded.allen(), ApiFixture.Seeded.HALF_PPR)).get("state").asText())
                .isEqualTo("UNAVAILABLE");
        assertThat(status(rest, detailPath(999_999_999L, ApiFixture.Seeded.HALF_PPR))).isEqualTo(404);
    }

    /**
     * No week left to project: the schedule is empty, or its last game is more than 48 hours
     * behind. Even stored rows are not served as if they were next week's, and a player who does
     * not exist is still a 404.
     */
    @Test
    void afterTheLastWeekThereIsNoUpcomingWeekAndNoRowsAreServed() {
        long wr = player("Offseason Receiver", "WR", buf);
        for (boolean scheduled : new boolean[] {false, true}) {
            clear();
            if (scheduled) {
                long last = game(WEEK, buf, bal, NOW.minus(Duration.ofDays(5)));
                store(WEEK, new Proj(wr, last, buf, Map.of(StatKey.REC_YD, 60.0)));
            }
            JsonNode board = getJson(rest, BOARD + "?profileId=1");
            assertThat(board.get("state").asText()).as("scheduled=%b", scheduled).isEqualTo("NO_UPCOMING_WEEK");
            assertThat(board.get("page").get("content")).as("scheduled=%b", scheduled).isEmpty();
            assertThat(getJson(rest, detailPath(wr, 1)).get("state").asText()).as("scheduled=%b", scheduled)
                    .isEqualTo("NO_UPCOMING_WEEK");
            assertThat(getJson(rest, detailPath(wr, 1)).get("projection").isNull()).isTrue();
            assertThat(status(rest, detailPath(999_999_999L, 1))).isEqualTo(404);
        }
    }

    /**
     * Last season's final week is not a stale version of this season's first. In September, with
     * the new schedule loaded and the job off (or its rosters not out), the board has nothing
     * for this season, and says so.
     */
    @Test
    void lastSeasonsRowsAreNeverServedAsThisSeasonsStaleBoard() {
        long lastSeasonFinale = game(SEASON - 1, 18, buf, bal, NOW.minus(Duration.ofDays(300)));
        long lastSeasonSameWeek = game(SEASON - 1, WEEK, buf, bal, NOW.minus(Duration.ofDays(400)));
        game(WEEK, buf, bal, inDays(2));
        long wr = player("Last Season Receiver", "WR", buf);
        long other = player("Last Season Same Week Receiver", "WR", buf);
        store(SEASON - 1, 18, new Proj(wr, lastSeasonFinale, buf, Map.of(StatKey.REC_YD, 60.0)));
        // A row at the same week number as the target: a lookup that compares weeks without the
        // season (week <= 3) would accept this one even though it is last year's.
        store(SEASON - 1, WEEK, new Proj(other, lastSeasonSameWeek, buf, Map.of(StatKey.REC_YD, 70.0)));

        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(board.get("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat(board.get("page").get("content")).isEmpty();
        JsonNode detail = getJson(rest, detailPath(wr, 1));
        assertThat(detail.get("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat(detail.get("projection").isNull()).isTrue();
    }

    /**
     * The board's own staleness, apart from the resolver's: the week is still the target (a game
     * that has not reported stats holds it) but every game that has stored rows kicked off at
     * least 48 hours ago. The edge is the resolver's: a game is old from exactly 48 hours, and
     * one second short of that it is not.
     */
    @Test
    void aTargetWeekWhoseStoredGamesAreAllAtLeastFortyEightHoursOldIsStale() {
        long wr = player("Old Rows Receiver", "WR", buf);
        Object[][] cases = {
                {Duration.ofHours(48).minusSeconds(1), "READY", "PROJECTED"},
                {Duration.ofHours(48), "STALE", "STALE"}};
        for (Object[] c : cases) {
            clear();
            long played = game(WEEK, buf, bal, NOW.minus((Duration) c[0]));
            game(WEEK, mia, bal, NOW.minus(Duration.ofHours(40))); // unreported, holds the week, no rows
            store(WEEK, new Proj(wr, played, buf, Map.of(StatKey.REC_YD, 60.0)));
            assertThat(getJson(rest, BOARD + "?profileId=1").get("state").asText())
                    .as("the stored game kicked off %s ago", c[0]).isEqualTo(c[1]);
            assertThat(getJson(rest, detailPath(wr, 1)).get("state").asText())
                    .as("player route, stored game kicked off %s ago", c[0]).isEqualTo(c[2]);
        }
    }

    /**
     * The ordinary Tuesday: Monday night's stats are in, so the resolver has moved to next week,
     * and the job has not written next week yet. The old rows are less than 48 hours old, yet they
     * are for the wrong week, so they are STALE (until the stats arrive the same rows are READY).
     * What the job then writes for the new week is covered by the provider and latest-week tests.
     */
    @Test
    void onceTheLastGameReportsItsStatsTheOldWeeksRowsAreStale() {
        long played = game(WEEK, buf, bal, NOW.minus(Duration.ofHours(30)));
        game(WEEK + 1, buf, bal, inDays(5));
        long wr = player("Tuesday Receiver", "WR", buf);
        long reporter = player("Reporting Receiver", "WR", bal);
        store(WEEK, new Proj(wr, played, buf, Map.of(StatKey.REC_YD, 60.0)));

        assertThat(getJson(rest, BOARD + "?profileId=1").get("state").asText())
                .as("the game has been played but its stats are not in: it still holds the week").isEqualTo("READY");

        jdbc.update("""
                INSERT INTO player_game_stats (player_id, game_id, season, week, team_id, snap_pct, rec, rec_yd)
                SELECT ?, ?, g.season, g.week, ?, 85.50, 4, 40 FROM games g WHERE g.id = ?
                """, reporter, played, bal, played);

        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(board.get("state").asText()).as("stats are in; the target is next week").isEqualTo("STALE");
        assertThat(ids(board)).as("the old rows are still served").containsExactly(wr);
        assertThat(getJson(rest, detailPath(wr, 1)).get("state").asText()).isEqualTo("STALE");
    }

    /** The week lookup is per provider: another method's newer week must not hide our older rows. */
    @Test
    void anotherProvidersNewerWeekDoesNotHideOurOlderRows() {
        long oldGame = game(WEEK - 1, buf, bal, NOW.minus(Duration.ofDays(9)));
        long game = game(WEEK, buf, bal, inDays(2));
        long ours = player("Older Rows Receiver", "WR", buf);
        long theirs = player("Newer Rows Receiver", "WR", buf);
        store(WEEK - 1, new Proj(ours, oldGame, buf, Map.of(StatKey.REC_YD, 60.0)));
        storeAnotherProvidersRow(WEEK, theirs, game);

        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(board.get("state").asText()).isEqualTo("STALE");
        assertThat(ids(board)).containsExactly(ours);
    }

    /** Stored rows that are all hidden (open rows of delisted games) leave nothing to serve. */
    @Test
    void aWeekWhoseOnlyStoredRowsAreHiddenIsUnavailable() {
        game(WEEK, buf, bal, inDays(2));
        long dropped = game(WEEK, mia, bal, inDays(2));
        long wr = player("Hidden Rows Receiver", "WR", mia);
        store(WEEK, new Proj(wr, dropped, mia, Map.of(StatKey.REC_YD, 60.0)));
        jdbc.update("UPDATE games SET delisted_at = now() WHERE id = ?", dropped);

        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(board.get("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat(board.get("page").get("content")).isEmpty();
    }

    /** A game the schedule has no date for is open: the week stays READY and the row has no kickoff. */
    @Test
    void aGameWithNoKickoffDateKeepsTheWeekReady() {
        long undated = game(WEEK, buf, bal, null);
        long wr = player("Undated Receiver", "WR", buf);
        store(WEEK, new Proj(wr, undated, buf, Map.of(StatKey.REC_YD, 60.0)));

        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(board.get("state").asText()).isEqualTo("READY");
        assertThat(rowOf(board, wr).get("kickoff").isNull()).isTrue();
        assertThat(rowOf(board, wr).get("locked").asBoolean()).isFalse();
    }

    /** Stored rows for a week after the one the resolver names are not the resolver's week. */
    @Test
    void rowsForAWeekAfterTheTargetAreNotServed() {
        long early = game(WEEK, buf, bal, inDays(2));
        long later = game(WEEK + 2, buf, bal, inDays(16));
        long wr = player("Later Receiver", "WR", buf);
        store(WEEK + 2, new Proj(wr, later, buf, Map.of(StatKey.REC_YD, 60.0)));
        assertThat(early).isNotEqualTo(later);
        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(board.get("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat(board.get("page").get("content")).isEmpty();
    }

    /**
     * The 48-hour rule is the resolver's: a game with no stat rows holds its week until 48 hours
     * after kickoff. One second inside, the week is still the target and its rows are READY; at
     * exactly 48 hours the target has moved to the next week, which has no rows yet, so the board
     * serves the last stored week as STALE (and the player route says so).
     */
    @Test
    void aWeekHoldsUntilItsLastGameIsFortyEightHoursOldThenItsRowsAreStale() {
        long wr = player("Stale Receiver", "WR", buf);
        long unlisted = player("Unlisted Stale Receiver", "WR", buf);
        Object[][] cases = {
                {Duration.ofHours(48).minusSeconds(1), "READY", "PROJECTED", "NOT_ROSTERED"},
                {Duration.ofHours(48), "STALE", "STALE", "UNAVAILABLE"}};
        for (Object[] c : cases) {
            clear();
            long played = game(WEEK, buf, bal, NOW.minus((Duration) c[0]));
            game(WEEK + 1, buf, bal, inDays(5));
            store(WEEK, new Proj(wr, played, buf, Map.of(StatKey.REC_YD, 60.0)));
            roster(wr, buf, "ACT");
            // A player with no row: in the live week the roster says why; in a stale one it is not
            // known to be the week being played, so no reason is given.
            assertThat(getJson(rest, detailPath(unlisted, 1)).get("state").asText())
                    .as("a player with no row, kicked off %s ago", c[0]).isEqualTo(c[3]);
            JsonNode board = getJson(rest, BOARD + "?profileId=1");
            assertThat(board.get("state").asText()).as("board, kicked off %s ago", c[0]).isEqualTo(c[1]);
            assertThat(rowOf(board, wr)).as("the week's rows are served either way").isNotNull();
            assertThat(getJson(rest, detailPath(wr, 1)).get("state").asText())
                    .as("player, kicked off %s ago", c[0]).isEqualTo(c[2]);
        }
    }

    /** One game still ahead keeps the week READY however old the other games are. */
    @Test
    void aSingleOpenGameKeepsAnOldWeekReady() {
        long wr = player("Open Receiver", "WR", buf);
        long other = player("Other Receiver", "WR", mia);
        long old = game(WEEK, buf, bal, NOW.minus(Duration.ofHours(60)));
        long open = game(WEEK, mia, bal, NOW.plus(Duration.ofHours(30)));
        store(WEEK, new Proj(wr, old, buf, Map.of(StatKey.REC_YD, 60.0)),
                new Proj(other, open, mia, Map.of(StatKey.REC_YD, 60.0)));
        assertThat(getJson(rest, BOARD + "?profileId=1").get("state").asText()).isEqualTo("READY");
    }

    /**
     * A reason is given for a player with no row only when the week is live and the roster
     * evidence backs it; otherwise it is plain UNAVAILABLE.
     */
    @Test
    void aPlayerWithNoRowGetsTheReasonTheRosterSupports() {
        long game = game(WEEK, buf, bal, inDays(2));
        long projected = player("Projected Receiver", "WR", buf);
        long onBye = player("Bye Receiver", "WR", mia);
        long newcomer = player("Newcomer Receiver", "WR", buf);
        long unlisted = player("Unlisted Receiver", "WR", buf);
        store(WEEK, new Proj(projected, game, buf, Map.of(StatKey.REC_YD, 60.0)));
        long allen = seeded.allen(); // has real stat lines, so history exists

        // No roster rows at all: nothing backs a reason.
        for (long id : List.of(onBye, newcomer, unlisted, allen)) {
            assertThat(getJson(rest, detailPath(id, 1)).get("state").asText()).as("no roster, %d", id)
                    .isEqualTo("UNAVAILABLE");
        }

        roster(projected, buf, "ACT");
        roster(onBye, mia, "ACT");       // MIA has no game this week
        roster(newcomer, buf, "ACT");    // BUF plays, he has never played
        roster(allen, buf, "ACT");       // BUF plays, he has history, yet no row
        assertThat(getJson(rest, detailPath(onBye, 1)).get("state").asText()).isEqualTo("BYE");
        assertThat(getJson(rest, detailPath(newcomer, 1)).get("state").asText()).isEqualTo("NO_HISTORY");
        assertThat(getJson(rest, detailPath(allen, 1)).get("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat(getJson(rest, detailPath(unlisted, 1)).get("state").asText()).isEqualTo("NOT_ROSTERED");
        assertThat(getJson(rest, detailPath(onBye, 1)).get("projection").isNull()).isTrue();
    }

    /**
     * Hand-computed from the V3 seed, so a wrong preset cannot hide behind the engine that is
     * used to check the others: whole-number lines, four presets, TE premium's override included.
     */
    @Test
    void thePresetsScoreAWholeLineToTheHandComputedValue() {
        long game = game(WEEK, buf, bal, inDays(2));
        long qb = player("Hand QB", "QB", buf);
        long rb = player("Hand RB", "RB", buf);
        long wr = player("Hand WR", "WR", buf);
        long te = player("Hand TE", "TE", buf);
        store(WEEK,
                new Proj(qb, game, buf, Map.of(StatKey.PASS_YD, 300.0, StatKey.PASS_TD, 2.0,
                        StatKey.PASS_INT, 1.0, StatKey.RUSH_YD, 20.0)),
                new Proj(rb, game, buf, Map.of(StatKey.RUSH_YD, 80.0, StatKey.RUSH_TD, 1.0,
                        StatKey.REC, 3.0, StatKey.REC_YD, 20.0)),
                new Proj(wr, game, buf, Map.of(StatKey.REC, 5.0, StatKey.REC_YD, 60.0, StatKey.REC_TD, 1.0)),
                new Proj(te, game, buf, Map.of(StatKey.REC, 5.0, StatKey.REC_YD, 60.0, StatKey.REC_TD, 1.0)));
        // qb: 12 + 8 - 2 + 2 = 20 whatever a catch is worth. rb: 8 + 6 + 2 = 16, plus 3 catches.
        // wr and te: 6 + 6 = 12, plus 5 catches; TE premium pays a tight end 1.5 a catch.
        Map<Long, double[]> expected = Map.of(
                qb, new double[] {20.0, 20.0, 20.0, 20.0},
                rb, new double[] {16.0, 17.5, 19.0, 19.0},
                wr, new double[] {12.0, 14.5, 17.0, 17.0},
                te, new double[] {12.0, 14.5, 17.0, 19.5});
        long[] presets = {ApiFixture.Seeded.STANDARD, ApiFixture.Seeded.HALF_PPR, ApiFixture.Seeded.FULL_PPR,
                ApiFixture.Seeded.TE_PREMIUM};
        for (int i = 0; i < presets.length; i++) {
            JsonNode board = getJson(rest, BOARD + "?profileId=" + presets[i]);
            for (Map.Entry<Long, double[]> line : expected.entrySet()) {
                assertThat(rowOf(board, line.getKey()).get("points").asDouble())
                        .as("preset %d, player %d", presets[i], line.getKey()).isEqualTo(line.getValue()[i]);
            }
        }
    }

    /**
     * The week's state is read from every stored row, not from the rows the board ranks or the
     * position that was asked for: an open game belonging to an Out player keeps the week READY
     * even though the only ranked player's game is old.
     */
    /**
     * "Same line, different value": a player's page carries his one stored line under each system
     * preset, in id order -- Standard, Half PPR, Full PPR, TE Premium. The numbers are the
     * hand-computed table above, so a wrong preset cannot hide behind the engine that produces the
     * others; and each is exactly what the board prints for that preset (the unmodified engine,
     * rounded once), for a fractional line as well, where a missing or a doubled rounding would show.
     */
    @Test
    void aPlayersPageScoresHisOneLineUnderEveryPreset() {
        long game = game(WEEK, buf, bal, inDays(2));
        long qb = player("Strip QB", "QB", buf);
        long rb = player("Strip RB", "RB", buf);
        long wr = player("Strip WR", "WR", buf);
        long te = player("Strip TE", "TE", buf);
        long fractional = player("Strip Fractional", "TE", buf);
        store(WEEK,
                new Proj(qb, game, buf, Map.of(StatKey.PASS_YD, 300.0, StatKey.PASS_TD, 2.0,
                        StatKey.PASS_INT, 1.0, StatKey.RUSH_YD, 20.0)),
                new Proj(rb, game, buf, Map.of(StatKey.RUSH_YD, 80.0, StatKey.RUSH_TD, 1.0,
                        StatKey.REC, 3.0, StatKey.REC_YD, 20.0)),
                new Proj(wr, game, buf, Map.of(StatKey.REC, 5.0, StatKey.REC_YD, 60.0, StatKey.REC_TD, 1.0)),
                new Proj(te, game, buf, Map.of(StatKey.REC, 5.0, StatKey.REC_YD, 60.0, StatKey.REC_TD, 1.0)),
                new Proj(fractional, game, buf, Map.of(StatKey.REC, 5.15, StatKey.REC_YD, 63.7,
                        StatKey.REC_TD, 0.45, StatKey.RUSH_YD, 3.3)));
        long[] presets = {ApiFixture.Seeded.STANDARD, ApiFixture.Seeded.HALF_PPR, ApiFixture.Seeded.FULL_PPR,
                ApiFixture.Seeded.TE_PREMIUM};
        // The same hand-computed lines as thePresetsScoreAWholeLineToTheHandComputedValue.
        Map<Long, double[]> expected = Map.of(
                qb, new double[] {20.0, 20.0, 20.0, 20.0},
                rb, new double[] {16.0, 17.5, 19.0, 19.0},
                wr, new double[] {12.0, 14.5, 17.0, 17.0},
                te, new double[] {12.0, 14.5, 17.0, 19.5});

        for (Map.Entry<Long, double[]> line : expected.entrySet()) {
            JsonNode strip = getJson(rest, detailPath(line.getKey(), ApiFixture.Seeded.STANDARD)).get("presets");
            assertThat(strip).as("player %d", line.getKey()).hasSize(4);
            for (int i = 0; i < presets.length; i++) {
                assertThat(strip.get(i).get("profileId").asLong()).as("order, entry %d", i).isEqualTo(presets[i]);
                assertThat(strip.get(i).get("points").asDouble()).as("preset %d, player %d", presets[i], line.getKey())
                        .isEqualTo(line.getValue()[i]);
            }
        }
        List<String> storedNames = jdbc.queryForList(
                "SELECT name FROM scoring_profiles WHERE is_preset ORDER BY id", String.class);
        JsonNode anyStrip = getJson(rest, detailPath(wr, ApiFixture.Seeded.STANDARD)).get("presets");
        for (int i = 0; i < presets.length; i++) {
            assertThat(anyStrip.get(i).get("name").asText()).isEqualTo(storedNames.get(i));
        }

        // Each entry is the number the board prints for that preset, for every line including the fractional one.
        for (long preset : presets) {
            JsonNode board = getJson(rest, BOARD + "?profileId=" + preset);
            for (long id : List.of(qb, rb, wr, te, fractional)) {
                assertThat(presetPoints(getJson(rest, detailPath(id, ApiFixture.Seeded.STANDARD)), preset))
                        .as("preset %d, player %d: strip against board", preset, id)
                        .isEqualTo(rowOf(board, id).get("points").asDouble());
            }
        }
        // A full point per catch is worth the catches: PPR less 0 PPR, to the cent.
        JsonNode strip = getJson(rest, detailPath(fractional, ApiFixture.Seeded.STANDARD));
        assertThat(presetPoints(strip, ApiFixture.Seeded.FULL_PPR) - presetPoints(strip, ApiFixture.Seeded.STANDARD))
                .isCloseTo(5.15, within(0.011));
        // The strip does not depend on which ruleset the member asked under.
        assertThat(getJson(rest, detailPath(te, ApiFixture.Seeded.TE_PREMIUM)).get("presets"))
                .isEqualTo(getJson(rest, detailPath(te, ApiFixture.Seeded.STANDARD)).get("presets"));
    }

    /**
     * Only the system presets, and only where there is a line: another member's ruleset is never
     * listed, an Out player (whose line is kept) and a stale line have a strip, and a player with no
     * line has none -- null, not an empty list.
     */
    @Test
    void theStripIsOnlyThePresetsAndOnlyWhereThereIsALine() {
        long other = ApiFixture.memberId(jdbc, "other-member@example.com");
        ownedProfile(memberId, "My own ruleset");
        ownedProfile(other, "Somebody else's ruleset");
        long game = game(WEEK, buf, bal, inDays(2));
        long out = player("Strip Out Receiver", "WR", buf);
        long bye = player("Strip Bye Receiver", "WR", mia);
        long rookie = player("Strip Rookie", "WR", buf);
        store(WEEK, new Proj(out, game, buf, Map.of(StatKey.REC_YD, 60.0)));
        roster(out, buf, "ACT");
        injury(out, buf, "Out");
        roster(bye, mia, "ACT");
        roster(rookie, buf, "ACT");

        JsonNode outPage = getJson(rest, detailPath(out, ApiFixture.Seeded.STANDARD));
        assertThat(outPage.get("state").asText()).isEqualTo("PROJECTED");
        assertThat(ids(outPage.get("presets"), "profileId")).as("presets, and nobody's own ruleset")
                .containsExactly(ApiFixture.Seeded.STANDARD, ApiFixture.Seeded.HALF_PPR,
                        ApiFixture.Seeded.FULL_PPR, ApiFixture.Seeded.TE_PREMIUM);
        for (long noLine : List.of(bye, rookie)) {
            JsonNode page = getJson(rest, detailPath(noLine, ApiFixture.Seeded.STANDARD));
            assertThat(page.get("projection").isNull()).isTrue();
            assertThat(page.get("presets").isNull()).as("%s: no line, no strip", page.get("state").asText()).isTrue();
        }

        // A stale line still has the strip: every game of the week is over 48 hours old.
        clear();
        long old = game(WEEK, buf, bal, NOW.minus(Duration.ofHours(48)));
        game(WEEK, mia, bal, NOW.minus(Duration.ofHours(40)));
        store(WEEK, new Proj(out, old, buf, Map.of(StatKey.REC_YD, 60.0)));
        JsonNode stale = getJson(rest, detailPath(out, ApiFixture.Seeded.STANDARD));
        assertThat(stale.get("state").asText()).isEqualTo("STALE");
        assertThat(stale.get("presets")).hasSize(4);

        // No week left: no line, no strip.
        clear();
        assertThat(getJson(rest, detailPath(out, ApiFixture.Seeded.STANDARD)).get("presets").isNull()).isTrue();
    }

    private static double presetPoints(JsonNode page, long presetId) {
        for (JsonNode entry : page.get("presets")) {
            if (entry.get("profileId").asLong() == presetId) {
                return entry.get("points").asDouble();
            }
        }
        throw new AssertionError("no preset " + presetId + " in " + page.get("presets"));
    }

    private static List<Long> ids(JsonNode array, String field) {
        List<Long> found = new ArrayList<>();
        array.forEach(entry -> found.add(entry.get(field).asLong()));
        return found;
    }

    @Test
    void theWeeksStateIgnoresWhoIsRankedAndWhichPositionWasAsked() {
        long old = game(WEEK, buf, bal, NOW.minus(Duration.ofHours(60)));
        long open = game(WEEK, mia, bal, NOW.plus(Duration.ofHours(30)));
        long ranked = player("Old Game Quarterback", "QB", buf);
        long out = player("Open Game Receiver", "WR", mia);
        store(WEEK, new Proj(ranked, old, buf, Map.of(StatKey.PASS_YD, 250.0)),
                new Proj(out, open, mia, Map.of(StatKey.REC_YD, 60.0)));
        roster(out, mia, "ACT");
        injury(out, mia, "Out");

        JsonNode everyone = getJson(rest, BOARD + "?profileId=1");
        assertThat(rowOf(everyone, out)).as("the Out player is not on the board").isNull();
        assertThat(rowOf(everyone, ranked)).isNotNull();
        assertThat(everyone.get("state").asText()).isEqualTo("READY");
        assertThat(getJson(rest, BOARD + "?profileId=1&position=QB").get("state").asText())
                .as("a position filter that leaves out the open game").isEqualTo("READY");
    }

    /**
     * A game the schedule dropped (nflverse deletes a moved or cancelled game) is not projected:
     * its open row stays off the board until the job withdraws it. A row that locked at kickoff is
     * the record of what was projected, and stays readable even if the game is later delisted.
     */
    @Test
    void aDelistedGamesOpenRowIsHiddenButItsLockedRowStays() {
        long kickedOff = game(WEEK, buf, bal, NOW.minus(Duration.ofHours(2)));
        long upcomingDelisted = game(WEEK, mia, bal, NOW.plus(Duration.ofHours(30)));
        long upcoming = game(WEEK, bal, mia, NOW.plus(Duration.ofHours(30)));
        long record = player("Locked Receiver", "WR", buf);
        long hidden = player("Delisted Receiver", "WR", mia);
        long control = player("Control Receiver", "WR", bal);
        store(WEEK, new Proj(record, kickedOff, buf, Map.of(StatKey.REC_YD, 60.0)),
                new Proj(hidden, upcomingDelisted, mia, Map.of(StatKey.REC_YD, 60.0)),
                new Proj(control, upcoming, bal, Map.of(StatKey.REC_YD, 60.0)));
        assertThat(repository.lockKickedOff(NOW)).as("only the game that kicked off locks")
                .isEqualTo(1);
        // With the job off, a game can kick off and be dropped from the schedule with its rows never
        // locked (the 2022 BUF-CIN pattern). Such a row is kicked off but not locked: it must be
        // hidden, which tells "locked" apart from "kicked off". replaceOpen keeps the locked row.
        long neverLockedGame = game(WEEK, buf, mia, NOW.minus(Duration.ofHours(3)));
        long neverLocked = player("Never Locked Receiver", "WR", buf);
        store(WEEK, new Proj(hidden, upcomingDelisted, mia, Map.of(StatKey.REC_YD, 60.0)),
                new Proj(control, upcoming, bal, Map.of(StatKey.REC_YD, 60.0)),
                new Proj(neverLocked, neverLockedGame, buf, Map.of(StatKey.REC_YD, 60.0)));
        jdbc.update("UPDATE games SET delisted_at = now() WHERE id IN (?, ?, ?)", kickedOff, upcomingDelisted,
                neverLockedGame);

        JsonNode board = getJson(rest, BOARD + "?profileId=1&position=WR");
        assertThat(rowOf(board, hidden)).as("open row of a delisted game").isNull();
        assertThat(rowOf(board, record)).as("locked row of a delisted game").isNotNull();
        assertThat(rowOf(board, record).get("locked").asBoolean()).isTrue();
        assertThat(rowOf(board, neverLocked)).as("a kicked-off row the job never locked, game delisted").isNull();
        assertThat(rowOf(board, control)).as("an ordinary open game").isNotNull();
        assertThat(rowOf(board, control).get("locked").asBoolean()).isFalse();
    }

    /** The position filter and the name search narrow the board; punctuation and accents do not matter. */
    @Test
    void thePositionFilterAndTheNameSearchNarrowTheBoard() {
        long game = game(WEEK, buf, bal, inDays(2));
        long qb = player("Filter Quarterback", "QB", buf);
        long wr = player("Ja'Marr Filter", "WR", buf);
        long te = player("Filter Tight End", "TE", buf);
        store(WEEK, new Proj(qb, game, buf, Map.of(StatKey.PASS_YD, 250.0)),
                new Proj(wr, game, buf, Map.of(StatKey.REC_YD, 80.0)),
                new Proj(te, game, buf, Map.of(StatKey.REC_YD, 40.0)));
        String all = BOARD + "?profileId=1";

        assertThat(ids(getJson(rest, all))).containsExactlyInAnyOrder(qb, wr, te);
        assertThat(ids(getJson(rest, all + "&position=QB"))).containsExactly(qb);
        assertThat(ids(getJson(rest, all + "&position=wr"))).as("case does not matter").containsExactly(wr);
        assertThat(ids(getJson(rest, all + "&position=TE"))).containsExactly(te);
        assertThat(ids(getJson(rest, all + "&q=jamarr"))).as("the apostrophe is dropped").containsExactly(wr);
        assertThat(ids(getJson(rest, all + "&q=FILTER&position=WR"))).containsExactly(wr);
        assertThat(ids(getJson(rest, all + "&q=nobody"))).isEmpty();
    }

    /** A row is locked once its game has kicked off, whether or not the job has locked it yet. */
    @Test
    void aRowIsLockedOnceItsGameHasKickedOff() {
        long kickedOff = game(WEEK, buf, bal, NOW.minus(Duration.ofHours(1)));
        long upcoming = game(WEEK, mia, bal, NOW.plus(Duration.ofHours(30)));
        long playing = player("Playing Receiver", "WR", buf);
        long waiting = player("Waiting Receiver", "WR", mia);
        store(WEEK, new Proj(playing, kickedOff, buf, Map.of(StatKey.REC_YD, 60.0)),
                new Proj(waiting, upcoming, mia, Map.of(StatKey.REC_YD, 60.0)));
        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(rowOf(board, playing).get("locked").asBoolean()).as("kicked off, never locked by the job").isTrue();
        assertThat(rowOf(board, waiting).get("locked").asBoolean()).isFalse();
    }

    /** One provider's rows only, and the newest stored week of that provider's, not the oldest. */
    @Test
    void onlyTheConfiguredProvidersLatestStoredWeekIsServed() {
        long oldGame = game(WEEK - 1, buf, bal, NOW.minus(Duration.ofDays(9)));
        long game = game(WEEK, buf, bal, inDays(2));
        long lastWeek = player("Last Week Receiver", "WR", buf);
        long thisWeek = player("This Week Receiver", "WR", buf);
        long other = player("Other Method Receiver", "WR", buf);
        store(WEEK - 1, new Proj(lastWeek, oldGame, buf, Map.of(StatKey.REC_YD, 90.0)));
        store(WEEK, new Proj(thisWeek, game, buf, Map.of(StatKey.REC_YD, 60.0)));
        // Another method's row in the same week: the line query must filter on provider. (Its
        // week lookup is pinned by anotherProvidersNewerWeekDoesNotHideOurOlderRows.)
        storeAnotherProvidersRow(WEEK, other, game);

        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(ids(board)).containsExactly(thisWeek);
        assertThat(board.get("source").get("id").asText()).isEqualTo(provider.source().id());
    }

    /** A team whose only game of the week was dropped from the schedule is on a bye. */
    @Test
    void aTeamWhoseOnlyGameWasDelistedIsOnABye() {
        long game = game(WEEK, buf, bal, inDays(2));
        long dropped = game(WEEK, mia, bal, inDays(2));
        jdbc.update("UPDATE games SET delisted_at = now() WHERE id = ?", dropped);
        long projected = player("Bye Check Projected", "WR", buf);
        long stranded = player("Bye Check Stranded", "WR", mia);
        store(WEEK, new Proj(projected, game, buf, Map.of(StatKey.REC_YD, 60.0)));
        roster(projected, buf, "ACT");
        roster(stranded, mia, "ACT");
        assertThat(getJson(rest, detailPath(stranded, 1)).get("state").asText()).isEqualTo("BYE");
    }

    /** Under four games of history a line is LIMITED; at four it is ESTABLISHED. */
    @Test
    void fourGamesOfHistoryMakeALineEstablished() {
        long game = game(WEEK, buf, bal, inDays(2));
        long thin = player("Thin History", "WR", buf);
        long deep = player("Deep History", "WR", buf);
        store(WEEK, new Proj(thin, game, buf, Map.of(StatKey.REC_YD, 60.0), 3),
                new Proj(deep, game, buf, Map.of(StatKey.REC_YD, 60.0), 4));
        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(rowOf(board, thin).get("history").asText()).isEqualTo("LIMITED");
        assertThat(rowOf(board, deep).get("history").asText()).isEqualTo("ESTABLISHED");
    }

    // ------------------------------------------------------------------ the envelope

    /**
     * The week, the data and the feeds behind a board, none of them derived from what the member
     * asked for. The newest line here is an Out player's, so it is not on the board: a version
     * that read the envelope off the ranked rows, or off the filtered page, takes an older line's
     * values instead.
     */
    @Test
    void theEnvelopeNamesTheWeekTheDataAndTheFeeds() {
        long game = game(WEEK, buf, bal, inDays(2));
        long early = player("Early Data Receiver", "WR", buf);
        long newest = player("Newest Data Receiver", "WR", buf);
        long oldest = player("Oldest Data Receiver", "WR", buf);
        store(WEEK,
                new Proj(early, game, buf, Map.of(StatKey.REC_YD, 60.0), 4, new SeasonWeek(SEASON, 1)),
                new Proj(newest, game, buf, Map.of(StatKey.REC_YD, 60.0), 4, new SeasonWeek(SEASON, 2)),
                new Proj(oldest, game, buf, Map.of(StatKey.REC_YD, 60.0), 4, new SeasonWeek(SEASON - 1, 18)));
        roster(newest, buf, "ACT");
        injury(newest, buf, "Out");
        generatedAt(early, NOW.minus(Duration.ofHours(5)));
        generatedAt(newest, NOW.minus(Duration.ofHours(1)));
        generatedAt(oldest, NOW.minus(Duration.ofHours(9)));
        // Each feed's newest success; a skipped or failed run, however recent, read nothing.
        pull(RosterWeekIngestor.SOURCE, "SUCCESS", NOW.minus(Duration.ofHours(10)));
        pull(RosterWeekIngestor.SOURCE, "SUCCESS", NOW.minus(Duration.ofHours(3)));
        pull(RosterWeekIngestor.SOURCE, "SKIPPED", NOW.minus(Duration.ofHours(1)));
        pull(InjuryReportIngestor.SOURCE, "SUCCESS", NOW.minus(Duration.ofHours(5)));
        pull(InjuryReportIngestor.SOURCE, "FAILED", NOW.minus(Duration.ofMinutes(30)));

        for (String filter : List.of("", "&position=QB", "&q=nobody")) {
            JsonNode board = getJson(rest, BOARD + "?profileId=1" + filter);
            assertThat(board.get("state").asText()).as(filter).isEqualTo("READY");
            assertThat(board.get("season").asInt()).as("season, %s", filter).isEqualTo(SEASON);
            assertThat(board.get("week").asInt()).as("week, %s", filter).isEqualTo(WEEK);
            assertThat(board.get("dataThrough").get("season").asInt()).as("dataThrough, %s", filter)
                    .isEqualTo(SEASON);
            assertThat(board.get("dataThrough").get("week").asInt()).as("dataThrough, %s", filter).isEqualTo(2);
            assertThat(Instant.parse(board.get("generatedAt").asText())).as("generatedAt, %s", filter)
                    .isEqualTo(NOW.minus(Duration.ofHours(1)));
            assertThat(Instant.parse(board.get("availabilityAsOf").asText())).as("availabilityAsOf, %s", filter)
                    .isEqualTo(NOW.minus(Duration.ofHours(5)));
        }
    }

    /**
     * One feed's freshness says nothing about the other's: with either never having succeeded the
     * board does not know how current "who is out" is, and says null rather than the other's time.
     */
    @Test
    void availabilityAsOfIsNullUnlessBothFeedsHaveSucceeded() {
        long game = game(WEEK, buf, bal, inDays(2));
        store(WEEK, new Proj(player("As Of Receiver", "WR", buf), game, buf, Map.of(StatKey.REC_YD, 60.0)));
        String path = BOARD + "?profileId=1";
        assertThat(getJson(rest, path).get("availabilityAsOf").isNull()).as("nothing has ever run").isTrue();

        pull(RosterWeekIngestor.SOURCE, "SUCCESS", NOW.minus(Duration.ofHours(2)));
        assertThat(getJson(rest, path).get("availabilityAsOf").isNull()).as("only the roster feed").isTrue();

        pull(InjuryReportIngestor.SOURCE, "SKIPPED", NOW.minus(Duration.ofHours(1)));
        pull(InjuryReportIngestor.SOURCE, "FAILED", NOW.minus(Duration.ofMinutes(10)));
        assertThat(getJson(rest, path).get("availabilityAsOf").isNull()).as("skipped and failed are no pull")
                .isTrue();

        pull(InjuryReportIngestor.SOURCE, "SUCCESS", NOW.minus(Duration.ofMinutes(30)));
        assertThat(Instant.parse(getJson(rest, path).get("availabilityAsOf").asText()))
                .as("both have succeeded: the older of the two").isEqualTo(NOW.minus(Duration.ofHours(2)));
    }

    /**
     * Who the board left out, by kind, for the position asked and ignoring the search -- and in
     * agreement with what each player's own page says. The roster here has every kind of player the
     * count must include or skip: a reserve player and a released one (stored, not ranked); rookies
     * with no history; a whole bye team carried from the week before; a player traded off that
     * team into one that plays (counted once, on the team that plays); and ones the job would
     * never project (practice squad, a kicker).
     */
    @Test
    void excludedCountsWhoTheBoardLeftOutAndWhyAndAgreesWithEachPlayersOwnPage() {
        long game = game(WEEK, buf, bal, inDays(2)); // BUF and BAL play; MIA is on its bye
        long ranked = player("Ranked Receiver", "WR", buf);
        long questionable = player("Questionable Receiver", "WR", bal);
        long outWr = player("Out Receiver", "WR", buf);
        long reserveWr = player("Reserve Receiver", "WR", bal);
        long cutWr = player("Cut Receiver", "WR", buf);
        long outQb = player("Out Quarterback", "QB", buf);
        store(WEEK, List.of(ranked, questionable, outWr, reserveWr, cutWr, outQb).stream()
                .map(id -> new Proj(id, game, buf, Map.of(StatKey.REC_YD, 50.0 + id % 7))).toArray(Proj[]::new));
        roster(ranked, buf, "ACT");
        roster(questionable, bal, "ACT");
        injury(questionable, bal, "Questionable");
        roster(outWr, buf, "ACT");
        injury(outWr, buf, "Out");
        roster(reserveWr, bal, "RES");
        roster(cutWr, buf, "CUT");
        roster(outQb, buf, "ACT");
        injury(outQb, buf, "Out");

        // On a team that plays, rostered, no line, never played: NO_HISTORY.
        long rookieWr = player("Rookie Receiver", "WR", buf);
        long rookieTe = player("Rookie Tight End", "TE", bal);
        long traded = player("Traded Receiver", "WR", buf);
        long squadWr = player("Practice Squad Receiver", "WR", buf);
        roster(rookieWr, buf, "ACT");
        roster(rookieTe, bal, "ACT");
        roster(WEEK - 1, traded, mia, "ACT"); // last on the team now on its bye ...
        roster(WEEK, traded, buf, "ACT");     // ... and this week on one that plays
        roster(squadWr, buf, "DEV");          // not a candidate: never projected, so never "left out"
        // Has history, no line: neither count (his own page says UNAVAILABLE).
        long allen = seeded.allen();
        roster(allen, buf, "ACT");
        // MIA's players, carried from the last week its roster was published.
        long byeWr1 = player("Bye Receiver One", "WR", mia);
        long byeWr2 = player("Bye Receiver Two", "WR", mia);
        long byeRb = player("Bye Reserve Back", "RB", mia);
        long byeQb = player("Bye Quarterback", "QB", mia);
        long byeSquad = player("Bye Practice Squad", "WR", mia);
        long byeKicker = player("Bye Kicker", "K", mia);
        roster(WEEK - 1, byeWr1, mia, "ACT");
        roster(WEEK - 1, byeWr2, mia, "ACT");
        roster(WEEK - 1, byeRb, mia, "RES");
        roster(WEEK - 1, byeQb, mia, "ACT");
        roster(WEEK - 1, byeSquad, mia, "DEV");
        roster(WEEK - 1, byeKicker, mia, "ACT");

        // The board's counts, by position asked ({unavailable, noHistory, bye}).
        Map<String, List<Integer>> expected = Map.of(
                "", List.of(4, 3, 4),
                "&position=WR", List.of(3, 2, 2),
                "&position=QB", List.of(1, 0, 1),
                "&position=TE", List.of(0, 1, 0),
                "&position=RB", List.of(0, 0, 1),
                "&q=nobody", List.of(4, 3, 4),
                "&position=WR&q=zzz", List.of(3, 2, 2));
        expected.forEach((filter, counts) -> {
            JsonNode excluded = getJson(rest, BOARD + "?profileId=1" + filter).get("excluded");
            assertThat(List.of(excluded.get("unavailable").asInt(), excluded.get("noHistory").asInt(),
                    excluded.get("bye").asInt())).as("excluded, filter '%s'", filter).isEqualTo(counts);
        });
        assertThat(ids(getJson(rest, BOARD + "?profileId=1&q=nobody"))).as("the search still narrows the page")
                .isEmpty();

        // Each of the job's candidates with no line: his own page agrees with the board's count.
        Map<Long, String> pages = Map.of(rookieWr, "NO_HISTORY", rookieTe, "NO_HISTORY", traded, "NO_HISTORY",
                byeWr1, "BYE", byeWr2, "BYE", byeRb, "BYE", byeQb, "BYE", allen, "UNAVAILABLE");
        pages.forEach((id, state) -> assertThat(getJson(rest, detailPath(id, 1)).get("state").asText())
                .as("player %d", id).isEqualTo(state));
        JsonNode all = getJson(rest, BOARD + "?profileId=1").get("excluded");
        assertThat(all.get("bye").asInt()).isEqualTo((int) pages.values().stream().filter("BYE"::equals).count());
        assertThat(all.get("noHistory").asInt())
                .isEqualTo((int) pages.values().stream().filter("NO_HISTORY"::equals).count());
        // The stored-but-unranked keep their line on their own page.
        for (long id : List.of(outWr, reserveWr, cutWr, outQb)) {
            assertThat(getJson(rest, detailPath(id, 1)).get("state").asText()).as("player %d", id)
                    .isEqualTo("PROJECTED");
        }
    }

    /**
     * "No history" is the job's rule -- regular-season games before the target week -- and not
     * "never played". A rookie whose debut has been ingested while the week is still being played
     * stays counted: the job did not project him and his only game is not before the week. A
     * player with only a playoff game is counted, a zero-stat appearance is a game, and a kicker is
     * not a "no history" player at all (v1 projects four positions).
     */
    @Test
    void noHistoryIsTheJobsRuleAndNotNeverPlayed() {
        long thursday = game(WEEK, buf, bal, NOW.minus(Duration.ofHours(20))); // played, its stats are in
        long sunday = game(WEEK, mia, bal, inDays(2));                         // not yet: holds the week open
        long lastSeason = game(SEASON - 1, 5, buf, bal, NOW.minus(Duration.ofDays(300)));
        long playoff = playoffGame(SEASON - 1, 18, "WC", buf, bal);
        long projected = player("History Projected", "WR", mia);
        store(WEEK, new Proj(projected, sunday, mia, Map.of(StatKey.REC_YD, 60.0)));
        roster(projected, mia, "ACT");

        long debut = player("Debut Rookie", "WR", buf);
        long playoffOnly = player("Playoff Only Receiver", "WR", buf);
        long veteran = player("Veteran Receiver", "WR", buf);
        long zeroGame = player("Zero Stat Veteran", "WR", buf);
        long kicker = player("Placekicker", "K", buf);
        for (long id : List.of(debut, playoffOnly, veteran, zeroGame, kicker)) {
            roster(id, buf, "ACT");
        }
        statLine(debut, thursday, buf);
        statLine(playoffOnly, playoff, buf);
        statLine(veteran, lastSeason, buf);
        appearance(zeroGame, lastSeason, buf);

        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(board.get("state").asText()).isEqualTo("READY");
        assertThat(board.get("week").asInt()).as("Thursday's stats are in, Sunday's are not").isEqualTo(WEEK);
        assertThat(board.get("excluded").get("noHistory").asInt())
                .as("the debut already played, and the playoff-only player").isEqualTo(2);
        assertThat(board.get("excluded").get("bye").asInt()).isZero();
        assertThat(board.get("excluded").get("unavailable").asInt()).isZero();
        Map<Long, String> pages = Map.of(debut, "NO_HISTORY", playoffOnly, "NO_HISTORY",
                veteran, "UNAVAILABLE", zeroGame, "UNAVAILABLE", kicker, "UNAVAILABLE");
        pages.forEach((id, state) -> assertThat(getJson(rest, detailPath(id, 1)).get("state").asText())
                .as("player %d", id).isEqualTo(state));
    }

    /**
     * The job fails its whole run on a corrupt history (two games in one week) and says so; a
     * request must not turn one player's data fault into a 500 for the whole board. The count that
     * needed the history is unknown -- null, not zero -- the fault is logged by name, and
     * everything that does not need it is still served.
     */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aCorruptHistoryMakesTheCountUnknownAndNotTheBoardAnError(CapturedOutput output) {
        long sunday = game(WEEK, buf, bal, inDays(2));
        long one = game(SEASON - 1, 5, buf, bal, NOW.minus(Duration.ofDays(300)));
        long two = game(SEASON - 1, 5, mia, bal, NOW.minus(Duration.ofDays(300))); // the same week
        long projected = player("Corrupt Case Projected", "WR", buf);
        store(WEEK, new Proj(projected, sunday, buf, Map.of(StatKey.REC_YD, 60.0)));
        roster(projected, buf, "ACT");
        long corrupt = player("Two Games One Week", "WR", buf);
        long rookie = player("Honest Rookie", "WR", bal);
        long onBye = player("Bye Player", "WR", mia);
        roster(corrupt, buf, "ACT");
        roster(rookie, bal, "ACT");
        roster(WEEK - 1, onBye, mia, "ACT");
        statLine(corrupt, one, buf);
        statLine(corrupt, two, mia);

        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(board.get("state").asText()).isEqualTo("READY");
        assertThat(ids(board)).as("the board is still served").containsExactly(projected);
        assertThat(board.get("excluded").get("noHistory").isNull()).as("not known, not zero").isTrue();
        assertThat(board.get("excluded").get("bye").asInt()).as("needs no history").isEqualTo(1);
        assertThat(getJson(rest, detailPath(corrupt, 1)).get("state").asText())
                .as("his history cannot be read, so no reason is claimed").isEqualTo("UNAVAILABLE");
        assertThat(getJson(rest, detailPath(rookie, 1)).get("state").asText())
                .as("a page that asks only about him is unaffected").isEqualTo("NO_HISTORY");
        String gsis = jdbc.queryForObject("SELECT gsis_id FROM players WHERE id = ?", String.class, corrupt);
        assertThat(output.getAll()).as("the fault is logged by name").contains("history is corrupt")
                .contains(gsis).contains((SEASON - 1) + " week 5");
    }

    /**
     * Where the week has no rows, or only its roster-less past, the envelope says what it does not
     * know. The board's one number that needs no roster, how many stored lines it declined to rank,
     * is still given; the two that need the roster are null and never zero. {@code season} and
     * {@code week} are the week being projected, and in a stale board the rows say their own.
     */
    @Test
    void whereTheWeekHasNoRowsOrNoRosterTheEnvelopeSaysWhatItDoesNotKnow() {
        // No week left: no season, no week, no data, and nobody left out of nothing.
        pullBoth(Duration.ofHours(4), Duration.ofHours(4));
        JsonNode none = getJson(rest, BOARD + "?profileId=1");
        assertThat(none.get("state").asText()).isEqualTo("NO_UPCOMING_WEEK");
        assertNothingServed(none);
        assertThat(none.get("season").isNull()).isTrue();
        assertThat(none.get("week").isNull()).isTrue();
        assertThat(none.get("excluded").get("unavailable").asInt()).isZero();
        assertThat(Instant.parse(none.get("availabilityAsOf").asText()))
                .as("the feeds' freshness does not depend on there being a week").isEqualTo(NOW.minus(Duration.ofHours(4)));

        // A week to project, nothing stored.
        clear();
        pullBoth(Duration.ofHours(4), Duration.ofHours(4));
        game(WEEK, buf, bal, inDays(2));
        JsonNode unavailable = getJson(rest, BOARD + "?profileId=1");
        assertThat(unavailable.get("state").asText()).isEqualTo("UNAVAILABLE");
        assertNothingServed(unavailable);
        assertThat(unavailable.get("season").asInt()).isEqualTo(SEASON);
        assertThat(unavailable.get("week").asInt()).isEqualTo(WEEK);

        // Tuesday: stats are in, the target is next week, the rows are last week's.
        clear();
        pullBoth(Duration.ofHours(4), Duration.ofHours(4));
        long played = game(WEEK, buf, bal, NOW.minus(Duration.ofHours(30)));
        game(WEEK + 1, buf, bal, inDays(5));
        long kept = player("Stale Kept Receiver", "WR", buf);
        long out = player("Stale Out Receiver", "WR", buf);
        long reporter = player("Stale Reporting Receiver", "WR", bal);
        store(WEEK, new Proj(kept, played, buf, Map.of(StatKey.REC_YD, 60.0), 4, new SeasonWeek(SEASON, 1)),
                new Proj(out, played, buf, Map.of(StatKey.REC_YD, 60.0), 4, new SeasonWeek(SEASON, 2)));
        roster(out, buf, "ACT"); // the rows' week, WEEK: the week whose availability a stale board uses
        injury(out, buf, "Out");
        jdbc.update("""
                INSERT INTO player_game_stats (player_id, game_id, season, week, team_id, snap_pct, rec, rec_yd)
                SELECT ?, ?, g.season, g.week, ?, 85.50, 4, 40 FROM games g WHERE g.id = ?
                """, reporter, played, bal, played);
        JsonNode stale = getJson(rest, BOARD + "?profileId=1");
        assertThat(stale.get("state").asText()).isEqualTo("STALE");
        assertThat(stale.get("season").asInt()).isEqualTo(SEASON);
        assertThat(stale.get("week").asInt()).as("the week being projected").isEqualTo(WEEK + 1);
        assertThat(rowOf(stale, kept).get("week").asInt()).as("the rows' own week").isEqualTo(WEEK);
        assertThat(stale.get("dataThrough").get("week").asInt()).isEqualTo(2);
        assertThat(Instant.parse(stale.get("generatedAt").asText())).isEqualTo(NOW);
        assertThat(stale.get("excluded").get("unavailable").asInt()).isEqualTo(1);
        assertThat(stale.get("excluded").get("noHistory").isNull()).isTrue();
        assertThat(stale.get("excluded").get("bye").isNull()).isTrue();

        // The target week itself, every game of it over 48 hours old.
        clear();
        long old = game(WEEK, buf, bal, NOW.minus(Duration.ofHours(48)));
        game(WEEK, mia, bal, NOW.minus(Duration.ofHours(40))); // unreported: holds the week
        long oldOut = player("Old Out Receiver", "WR", buf);
        store(WEEK, new Proj(oldOut, old, buf, Map.of(StatKey.REC_YD, 60.0)));
        roster(oldOut, buf, "ACT");
        injury(oldOut, buf, "Out");
        JsonNode old48 = getJson(rest, BOARD + "?profileId=1");
        assertThat(old48.get("state").asText()).isEqualTo("STALE");
        assertThat(old48.get("week").asInt()).isEqualTo(WEEK);
        assertThat(old48.get("excluded").get("unavailable").asInt()).isEqualTo(1);
        assertThat(old48.get("excluded").get("noHistory").isNull()).as("not the week being played").isTrue();
        assertThat(old48.get("excluded").get("bye").isNull()).isTrue();
    }

    private static void assertNothingServed(JsonNode board) {
        assertThat(board.get("dataThrough").isNull()).as("dataThrough with no rows").isTrue();
        assertThat(board.get("generatedAt").isNull()).as("generatedAt with no rows").isTrue();
        assertThat(board.get("page").get("content")).isEmpty();
        assertThat(board.get("excluded").get("noHistory").isNull()).as("noHistory is unknown, not zero").isTrue();
        assertThat(board.get("excluded").get("bye").isNull()).as("bye is unknown, not zero").isTrue();
    }

    // ------------------------------------------------------------------ query safety

    /** §8: every parameter is validated or bound; hostile values change nothing and break nothing. */
    @Test
    void hostileOrMalformedParametersAreRefusedOrInertAndTheTablesSurvive() {
        long game = game(WEEK, buf, bal, inDays(2));
        long wr = player("Safe Receiver", "WR", buf);
        store(WEEK, new Proj(wr, game, buf, Map.of(StatKey.REC_YD, 60.0)));
        long before = count("player_week_projection");

        ResponseEntity<String> position = rest.getForEntity(UriComponentsBuilder.fromPath(BOARD)
                .queryParam("profileId", 1).queryParam("position", "K' OR 1=1--").toUriString(), String.class);
        assertThat(position.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(position.getBody()).contains("allowed: QB, RB, WR, TE");

        ResponseEntity<String> needle = rest.getForEntity(UriComponentsBuilder.fromPath(BOARD)
                .queryParam("profileId", 1).queryParam("q", "x'); DROP TABLE players;--").toUriString(),
                String.class);
        assertThat(needle.getStatusCode()).isEqualTo(HttpStatus.OK);

        for (String bad : List.of("size=5000", "size=0", "page=-1", "q=" + "x".repeat(61), "profileId=abc")) {
            String query = bad.startsWith("profileId") ? bad : "profileId=1&" + bad;
            assertThat(status(rest, BOARD + "?" + query)).as(query).isEqualTo(400);
        }
        assertThat(status(rest, BOARD)).as("no profileId").isEqualTo(400);
        assertThat(status(rest, detailPath(wr, 1).replace("profileId=1", "profileId=abc"))).isEqualTo(400);
        assertThat(status(rest, "/api/v1/players/abc/projection?profileId=1")).isEqualTo(400);
        assertThat(status(rest, BOARD + "?profileId=1&size=" + PageResponse.MAX_SIZE)).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT to_regclass('public.players') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(count("player_week_projection")).isEqualTo(before);
    }

    // ------------------------------------------------------------------ the two older checks, moved here

    /** Two presets differ only in what a catch is worth; the stored line is untouched by asking. */
    @Test
    void aStoredProjectionScoresUnderTheSelectedRulesWithoutChangingTheRawLine() {
        long game = game(WEEK, buf, bal, inDays(2));
        store(WEEK, new Proj(seeded.allen(), game, buf, Map.of(StatKey.REC, 4.0), 3));

        JsonNode half = getJson(rest, detailPath(seeded.allen(), ApiFixture.Seeded.HALF_PPR));
        JsonNode full = getJson(rest, detailPath(seeded.allen(), ApiFixture.Seeded.FULL_PPR));
        assertThat(full.get("projection").get("points").asDouble()
                - half.get("projection").get("points").asDouble()).isEqualTo(2);
        assertThat(half.get("projection").get("history").asText()).isEqualTo("LIMITED");
        assertThat(jdbc.queryForObject("SELECT rec FROM player_week_projection WHERE player_id=?",
                Double.class, seeded.allen())).isEqualTo(4);
    }

    // ------------------------------------------------------------------ shape

    /**
     * The field names and their order, today. Slice 5 adds to these on purpose (5.3 the board's
     * envelope, 5.4 the bonus lines, 5.5 the preset strip, 5.6 the source's summary); this test
     * is where each of those edits has to be made, and so where it gets reviewed.
     */
    @Test
    void theWireShapeIsPinned() {
        long game = game(WEEK, buf, bal, inDays(2));
        long wr = player("Shape Receiver", "WR", buf);
        // A little rushing, so the century profile's rushing bonus bears on the line and is listed.
        store(WEEK, new Proj(wr, game, buf, Map.of(StatKey.REC, 5.0, StatKey.REC_YD, 60.0, StatKey.RUSH_YD, 5.0)));

        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(names(board)).containsExactly("state", "season", "week", "source", "dataThrough",
                "generatedAt", "availabilityAsOf", "excluded", "page");
        assertThat(names(board.get("dataThrough"))).containsExactly("season", "week");
        assertThat(names(board.get("excluded"))).containsExactly("unavailable", "noHistory", "bye");
        assertThat(names(board.get("source"))).containsExactly("id", "label", "description");
        assertThat(names(board.get("page"))).containsExactly("content", "page", "size", "total", "totalPages");
        assertThat(names(rowOf(board, wr))).containsExactly("playerId", "name", "position", "team", "opponent",
                "home", "kickoff", "locked", "points", "gamesUsed", "history", "availability", "espnId",
                "generatedAt", "parts", "bonuses", "season", "week");
        assertThat(names(rowOf(board, wr).get("parts").get(0))).containsExactly("stat", "value", "rate", "points");
        assertThat(rowOf(board, wr).get("bonuses")).as("no bonus in this ruleset").isEmpty();
        JsonNode century = getJson(rest, BOARD + "?profileId=" + seeded.centuryProfile());
        assertThat(names(rowOf(century, wr).get("bonuses").get(0)))
                .containsExactly("stat", "gte", "value", "points", "triggered");

        JsonNode detail = getJson(rest, detailPath(wr, 1));
        assertThat(names(detail)).containsExactly("state", "source", "projection", "presets");
        assertThat(names(detail.get("presets").get(0))).containsExactly("profileId", "name", "points");
    }

    // ------------------------------------------------------------------ helpers

    /** {@code lastGame} is the newest game the line was built from; null is last season's finale. */
    private record Proj(long player, long game, int team, Map<StatKey, Double> stats, int gamesUsed,
            SeasonWeek lastGame) {
        Proj(long player, long game, int team, Map<StatKey, Double> stats) {
            this(player, game, team, stats, 4, null);
        }

        Proj(long player, long game, int team, Map<StatKey, Double> stats, int gamesUsed) {
            this(player, game, team, stats, gamesUsed, null);
        }
    }

    private void store(int week, Proj... rows) {
        store(SEASON, week, rows);
    }

    private void store(int season, int week, Proj... rows) {
        SeasonWeek target = new SeasonWeek(season, week);
        List<ProjectedRow> projected = new ArrayList<>();
        for (Proj row : rows) {
            double[] values = new double[StatKey.COUNT];
            row.stats().forEach((key, value) -> values[key.index()] = value);
            projected.add(new ProjectedRow(row.player(), row.game(), row.team(),
                    new RawProjection(provider.source(), target, values, 0, 0, 0, row.gamesUsed(),
                            row.lastGame() == null ? new SeasonWeek(season - 1, 18) : row.lastGame())));
        }
        repository.replaceOpen(target, provider.source().id(), projected, NOW);
    }

    /**
     * TRUNCATE, not DELETE, on purpose: a locked row cannot be deleted (its trigger refuses), and
     * TRUNCATE does not fire row triggers. Season 2030's games go last, after the rows that point
     * at them.
     */
    private void clear() {
        jdbc.execute("TRUNCATE player_week_projection, player_week_projection_repairs");
        jdbc.update("DELETE FROM roster_week");
        jdbc.update("DELETE FROM injury_report");
        jdbc.update("DELETE FROM ingest_runs WHERE source IN (?, ?)", RosterWeekIngestor.SOURCE,
                InjuryReportIngestor.SOURCE);
        jdbc.update("DELETE FROM player_game_stats WHERE season IN (?, ?)", SEASON - 1, SEASON);
        jdbc.update("DELETE FROM player_game_appearances WHERE game_id IN "
                + "(SELECT id FROM games WHERE season IN (?, ?))", SEASON - 1, SEASON);
        jdbc.update("DELETE FROM games WHERE season IN (?, ?)", SEASON - 1, SEASON);
    }

    private static Instant inDays(int days) {
        return NOW.plus(Duration.ofDays(days));
    }

    private long game(int week, int home, int away, Instant kickoff) {
        return game(SEASON, week, home, away, kickoff);
    }

    /** A regular-season game; a null {@code kickoff} is a game the schedule has no date for. */
    private long game(int season, int week, int home, int away, Instant kickoff) {
        return jdbc.queryForObject("""
                INSERT INTO games (nflverse_game_id, season, week, season_type, home_team_id,
                                   away_team_id, kickoff_at)
                VALUES (?, ?, ?, 'REG', ?, ?, ?) RETURNING id
                """, Long.class, "%d_%02d_%d_%d".formatted(season, week, away, home), season, week,
                home, away, kickoff == null ? null : kickoff.atOffset(ZoneOffset.UTC));
    }

    /** A game he played, with a stat line, in {@code game}'s week. */
    private void statLine(long player, long game, int team) {
        jdbc.update("""
                INSERT INTO player_game_stats (player_id, game_id, season, week, team_id, snap_pct, rec, rec_yd)
                SELECT ?, ?, g.season, g.week, ?, 85.50, 4, 40 FROM games g WHERE g.id = ?
                """, player, game, team, game);
    }

    /** A game he was on the field for and recorded nothing in: an appearance with no stat line. */
    private void appearance(long player, long game, int team) {
        jdbc.update("""
                INSERT INTO player_game_appearances (player_id, game_id, team_id, offense_snaps)
                VALUES (?, ?, ?, 20)
                """, player, game, team);
    }

    /** A postseason game: {@code season_type} is the round ('WC', 'DIV'...), never 'POST'. */
    private long playoffGame(int season, int week, String round, int home, int away) {
        return jdbc.queryForObject("""
                INSERT INTO games (nflverse_game_id, season, week, season_type, home_team_id,
                                   away_team_id, kickoff_at)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, "%d_%02d_%s_%d_%d".formatted(season, week, round, away, home), season, week,
                round, home, away, NOW.minus(Duration.ofDays(300)).atOffset(ZoneOffset.UTC));
    }

    /** Another method's row for {@code player}, in {@code week}: the provider filters must keep it out. */
    private void storeAnotherProvidersRow(int week, long player, long game) {
        ProjectionSource another = new ProjectionSource("other-method-v1", "Another method", "A different method.");
        repository.replaceOpen(new SeasonWeek(SEASON, week), another.id(), List.of(
                new ProjectedRow(player, game, buf, new RawProjection(another, new SeasonWeek(SEASON, week),
                        new double[StatKey.COUNT], 0, 0, 0, 4, new SeasonWeek(SEASON - 1, 18)))), NOW);
    }

    private long player(String name, String position, int team) {
        return jdbc.queryForObject("""
                INSERT INTO players (gsis_id, full_name, position, team_id, status, external_ids)
                VALUES (?, ?, ?, ?, 'ACT', '{}'::jsonb) RETURNING id
                """, Long.class, "00-9%07d".formatted(NEXT.getAndIncrement()), name, position, team);
    }

    private void roster(long player, int team, String rosterStatus) {
        roster(WEEK, player, team, rosterStatus);
    }

    /** A roster row for another week: a team on its bye has none for the week, so its last one is carried. */
    private void roster(int week, long player, int team, String rosterStatus) {
        jdbc.update("""
                INSERT INTO roster_week (season, week, player_id, team_id, status, status_detail, ingested_at)
                VALUES (?, ?, ?, ?, ?, NULL, now())
                """, SEASON, week, player, team, rosterStatus);
    }

    /** One ingest run of a feed, as {@code IngestRunRecorder} leaves it: it finished at {@code finishedAt}. */
    private void pull(String source, String status, Instant finishedAt) {
        jdbc.update("""
                INSERT INTO ingest_runs (source, started_at, status, finished_at)
                VALUES (?, ?, ?, ?)
                """, source, finishedAt.minusSeconds(60).atOffset(ZoneOffset.UTC), status,
                finishedAt.atOffset(ZoneOffset.UTC));
    }

    private void pullBoth(Duration rosterAgo, Duration injuriesAgo) {
        pull(RosterWeekIngestor.SOURCE, "SUCCESS", NOW.minus(rosterAgo));
        pull(InjuryReportIngestor.SOURCE, "SUCCESS", NOW.minus(injuriesAgo));
    }

    private void generatedAt(long player, Instant at) {
        jdbc.update("UPDATE player_week_projection SET generated_at = ? WHERE player_id = ?",
                at.atOffset(ZoneOffset.UTC), player);
    }

    private void injury(long player, int team, String reportStatus) {
        jdbc.update("""
                INSERT INTO injury_report (season, week, player_id, team_id, report_status, ingested_at)
                VALUES (?, ?, ?, ?, ?, now())
                """, SEASON, WEEK, player, team, reportStatus);
    }

    private long ownedProfile(long owner, String name) {
        return ownedProfile(owner, name, "{\"version\":1,\"base\":{\"rec_yd\":0.1}}");
    }

    private long ownedProfile(long owner, String name, String rules) {
        return jdbc.queryForObject("""
                INSERT INTO scoring_profiles (user_id, name, is_preset, rules)
                VALUES (?, ?, FALSE, ?::jsonb) RETURNING id
                """, Long.class, owner, name, rules);
    }

    /** What {@code ScoringEngine} says for the stored line, read back from the table, rounded once. */
    private double expectedPoints(long playerId, long profileId) {
        String columns = Arrays.stream(StatKey.values()).map(StatKey::json).collect(Collectors.joining(","));
        double[] stats = jdbc.queryForObject("SELECT " + columns + " FROM player_week_projection WHERE player_id = ?",
                (rs, n) -> {
                    double[] values = new double[StatKey.COUNT];
                    for (StatKey key : StatKey.values()) {
                        values[key.index()] = rs.getDouble(key.json());
                    }
                    return values;
                }, playerId);
        String position = jdbc.queryForObject("SELECT position FROM players WHERE id = ?", String.class, playerId);
        ResolvedRuleset rules = profiles.byId(profileId, memberId);
        return ScoringEngine.roundForDisplay(ScoringEngine.score(new StatLine(position, stats), rules));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static String detailPath(long playerId, long profileId) {
        return "/api/v1/players/" + playerId + "/projection?profileId=" + profileId;
    }

    private JsonNode getJson(TestRestTemplate who, String path) {
        ResponseEntity<JsonNode> response = who.getForEntity(path, JsonNode.class);
        assertThat(response.getStatusCode().value()).as(path).isEqualTo(200);
        return response.getBody();
    }

    private int status(TestRestTemplate who, String path) {
        return who.getForEntity(path, String.class).getStatusCode().value();
    }

    private static JsonNode rowOf(JsonNode board, long playerId) {
        for (JsonNode row : board.get("page").get("content")) {
            if (row.get("playerId").asLong() == playerId) {
                return row;
            }
        }
        return null;
    }

    private static List<Long> ids(JsonNode board) {
        List<Long> ids = new ArrayList<>();
        board.get("page").get("content").forEach(row -> ids.add(row.get("playerId").asLong()));
        return ids;
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
