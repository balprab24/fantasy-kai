package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.auth.JwtService;
import com.fantasykai.projections.ProjectionProvider;
import com.fantasykai.projections.ProjectionRepository;
import com.fantasykai.projections.ProjectionRepository.ProjectedRow;
import com.fantasykai.projections.RawProjection;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.ScoringProfiles;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * <p>Projections are written to season 2030 with kickoffs set relative to the real clock (one
 * moved test copies a 2025 row instead), and the whole projection table is truncated before every
 * test, so no test sees another's rows.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectionApiTests {

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
     * What the explanation promises today: the parts and the bonus remainder add back to the
     * total, and a bonus pays <em>at</em> its threshold ({@code >=}), not past it. The century
     * profile pays 3 for 100+ rushing yards.
     */
    @Test
    void partsAndTheBonusAddUpToThePointsAndAThresholdPaysAtExactlyTheThreshold() {
        long game = game(WEEK, buf, bal, inDays(2));
        long atThreshold = player("Exactly Hundred", "RB", buf);
        long justShort = player("Just Short", "RB", buf);
        store(WEEK,
                new Proj(atThreshold, game, buf, Map.of(StatKey.RUSH_YD, 100.0)),
                new Proj(justShort, game, buf, Map.of(StatKey.RUSH_YD, 99.9)));
        JsonNode board = getJson(rest, BOARD + "?profileId=" + seeded.centuryProfile());

        JsonNode paid = rowOf(board, atThreshold);
        assertThat(paid.get("bonusPoints").asDouble()).isCloseTo(3.0, within(1e-9));
        assertThat(paid.get("points").asDouble()).isEqualTo(13.0);
        JsonNode missed = rowOf(board, justShort);
        assertThat(missed.get("bonusPoints").asDouble()).isCloseTo(0.0, within(1e-9));
        assertThat(missed.get("points").asDouble()).isEqualTo(9.99);

        for (JsonNode row : List.of(paid, missed)) {
            double sum = 0;
            for (JsonNode part : row.get("parts")) {
                sum += part.get("points").asDouble();
            }
            assertThat(ScoringEngine.roundForDisplay(sum + row.get("bonusPoints").asDouble()))
                    .as("parts + bonus, rounded once").isEqualTo(row.get("points").asDouble());
        }
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

    @Test
    void withNothingStoredTheBoardIsUnavailableAndAnUnknownPlayerIsA404() {
        JsonNode board = getJson(rest, BOARD + "?profileId=" + ApiFixture.Seeded.HALF_PPR);
        assertThat(board.get("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat(board.get("page").get("content")).isEmpty();
        assertThat(getJson(rest, detailPath(seeded.allen(), ApiFixture.Seeded.HALF_PPR)).get("state").asText())
                .isEqualTo("UNAVAILABLE");
        assertThat(status(rest, detailPath(999_999_999L, ApiFixture.Seeded.HALF_PPR))).isEqualTo(404);
    }

    /**
     * A stored week is READY until every one of its games kicked off more than 48 hours ago;
     * pinned to five minutes either side (the test and the application share one JVM clock).
     */
    @Test
    void aWeekIsStaleExactlyWhenEveryGameKickedOffOverFortyEightHoursAgo() {
        long wr = player("Stale Receiver", "WR", buf);
        for (Object[] c : new Object[][] {{47 * 60 + 55, "READY", "PROJECTED"}, {48 * 60 + 5, "STALE", "STALE"}}) {
            clear();
            long game = game(WEEK, buf, bal, Instant.now().minus(Duration.ofMinutes((int) c[0])));
            store(WEEK, new Proj(wr, game, buf, Map.of(StatKey.REC_YD, 60.0)));
            assertThat(getJson(rest, BOARD + "?profileId=1").get("state").asText())
                    .as("board, kicked off %d min ago", c[0]).isEqualTo(c[1]);
            assertThat(getJson(rest, detailPath(wr, 1)).get("state").asText())
                    .as("player, kicked off %d min ago", c[0]).isEqualTo(c[2]);
        }
    }

    /** One game still ahead keeps the week READY however old the other games are. */
    @Test
    void aSingleOpenGameKeepsAnOldWeekReady() {
        long wr = player("Open Receiver", "WR", buf);
        long other = player("Other Receiver", "WR", mia);
        long old = game(WEEK, buf, bal, Instant.now().minus(Duration.ofHours(60)));
        long open = game(WEEK, mia, bal, Instant.now().plus(Duration.ofHours(30)));
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
    @Test
    void theWeeksStateIgnoresWhoIsRankedAndWhichPositionWasAsked() {
        long old = game(WEEK, buf, bal, Instant.now().minus(Duration.ofHours(60)));
        long open = game(WEEK, mia, bal, Instant.now().plus(Duration.ofHours(30)));
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
        long kickedOff = game(WEEK, buf, bal, Instant.now().minus(Duration.ofHours(2)));
        long upcomingDelisted = game(WEEK, mia, bal, Instant.now().plus(Duration.ofHours(30)));
        long upcoming = game(WEEK, bal, mia, Instant.now().plus(Duration.ofHours(30)));
        long record = player("Locked Receiver", "WR", buf);
        long hidden = player("Delisted Receiver", "WR", mia);
        long control = player("Control Receiver", "WR", bal);
        store(WEEK, new Proj(record, kickedOff, buf, Map.of(StatKey.REC_YD, 60.0)),
                new Proj(hidden, upcomingDelisted, mia, Map.of(StatKey.REC_YD, 60.0)),
                new Proj(control, upcoming, bal, Map.of(StatKey.REC_YD, 60.0)));
        assertThat(repository.lockKickedOff(Instant.now())).as("only the game that kicked off locks")
                .isEqualTo(1);
        jdbc.update("UPDATE games SET delisted_at = now() WHERE id IN (?, ?)", kickedOff, upcomingDelisted);

        JsonNode board = getJson(rest, BOARD + "?profileId=1&position=WR");
        assertThat(rowOf(board, hidden)).as("open row of a delisted game").isNull();
        assertThat(rowOf(board, record)).as("locked row of a delisted game").isNotNull();
        assertThat(rowOf(board, record).get("locked").asBoolean()).isTrue();
        assertThat(rowOf(board, control)).as("an ordinary open game").isNotNull();
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
        String columns = Arrays.stream(StatKey.values()).map(StatKey::json).collect(Collectors.joining(","));
        String values = Arrays.stream(StatKey.values()).map(k -> k.json().equals("rec") ? "4" : "0")
                .collect(Collectors.joining(","));
        jdbc.update("INSERT INTO player_week_projection (season,week,provider,player_id,game_id,team_id,"
                + columns + ",pass_att,rush_att,targets,games_used,data_through_season,data_through_week,generated_at)"
                + " SELECT s.season,s.week,?,s.player_id,s.game_id,s.team_id," + values
                + ",0,0,5,3,2024,18,now() FROM player_game_stats s WHERE s.player_id=? AND s.season=2025 LIMIT 1",
                provider.source().id(), seeded.allen());

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
        store(WEEK, new Proj(wr, game, buf, Map.of(StatKey.REC, 5.0, StatKey.REC_YD, 60.0)));

        JsonNode board = getJson(rest, BOARD + "?profileId=1");
        assertThat(names(board)).containsExactly("state", "source", "page");
        assertThat(names(board.get("source"))).containsExactly("id", "label", "description");
        assertThat(names(board.get("page"))).containsExactly("content", "page", "size", "total", "totalPages");
        assertThat(names(rowOf(board, wr))).containsExactly("playerId", "name", "position", "team", "opponent",
                "home", "kickoff", "locked", "points", "gamesUsed", "history", "availability", "espnId",
                "generatedAt", "parts", "bonusPoints", "season", "week");
        assertThat(names(rowOf(board, wr).get("parts").get(0))).containsExactly("stat", "value", "rate", "points");

        JsonNode detail = getJson(rest, detailPath(wr, 1));
        assertThat(names(detail)).containsExactly("state", "source", "projection");
    }

    // ------------------------------------------------------------------ helpers

    private record Proj(long player, long game, int team, Map<StatKey, Double> stats) {}

    private void store(int week, Proj... rows) {
        SeasonWeek target = new SeasonWeek(SEASON, week);
        List<ProjectedRow> projected = new ArrayList<>();
        for (Proj row : rows) {
            double[] values = new double[StatKey.COUNT];
            row.stats().forEach((key, value) -> values[key.index()] = value);
            projected.add(new ProjectedRow(row.player(), row.game(), row.team(),
                    new RawProjection(provider.source(), target, values, 0, 0, 0, 4,
                            new SeasonWeek(SEASON - 1, 18))));
        }
        repository.replaceOpen(target, provider.source().id(), projected, Instant.now());
    }

    private void clear() {
        jdbc.execute("TRUNCATE player_week_projection, player_week_projection_repairs");
        jdbc.update("DELETE FROM roster_week");
        jdbc.update("DELETE FROM injury_report");
        jdbc.update("DELETE FROM games WHERE season = ?", SEASON);
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(Duration.ofDays(days));
    }

    private long game(int week, int home, int away, Instant kickoff) {
        return jdbc.queryForObject("""
                INSERT INTO games (nflverse_game_id, season, week, season_type, home_team_id,
                                   away_team_id, kickoff_at)
                VALUES (?, ?, ?, 'REG', ?, ?, ?) RETURNING id
                """, Long.class, "%d_%02d_%d_%d".formatted(SEASON, week, away, home), SEASON, week,
                home, away, kickoff.atOffset(ZoneOffset.UTC));
    }

    private long player(String name, String position, int team) {
        return jdbc.queryForObject("""
                INSERT INTO players (gsis_id, full_name, position, team_id, status, external_ids)
                VALUES (?, ?, ?, ?, 'ACT', '{}'::jsonb) RETURNING id
                """, Long.class, "00-9%07d".formatted(NEXT.getAndIncrement()), name, position, team);
    }

    private void roster(long player, int team, String rosterStatus) {
        jdbc.update("""
                INSERT INTO roster_week (season, week, player_id, team_id, status, status_detail, ingested_at)
                VALUES (?, ?, ?, ?, ?, NULL, now())
                """, SEASON, WEEK, player, team, rosterStatus);
    }

    private void injury(long player, int team, String reportStatus) {
        jdbc.update("""
                INSERT INTO injury_report (season, week, player_id, team_id, report_status, ingested_at)
                VALUES (?, ?, ?, ?, ?, now())
                """, SEASON, WEEK, player, team, reportStatus);
    }

    private long ownedProfile(long owner, String name) {
        return jdbc.queryForObject("""
                INSERT INTO scoring_profiles (user_id, name, is_preset, rules)
                VALUES (?, ?, FALSE, ?::jsonb) RETURNING id
                """, Long.class, owner, name, "{\"version\":1,\"base\":{\"rec_yd\":0.1}}");
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

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
