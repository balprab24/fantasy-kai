package com.fantasykai.leagues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fantasykai.auth.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * League import end to end: the real Sleeper provider, mapper, service,
 * controller and schema, with only the network replaced by
 * {@link FixtureLeagueHttp}. Written the {@code QuerySafetyTests} way --
 * perform the attack, then check what survived.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LeagueApiTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class FixtureSleeper {
        @Bean
        @Primary
        FixtureLeagueHttp fixtureLeagueHttp(ObjectMapper json) {
            return new FixtureLeagueHttp(json);
        }
    }

    private static final AtomicInteger MEMBERS = new AtomicInteger();
    private static final String ROSTERS = "/v1/league/" + FixtureLeagueHttp.LEAGUE + "/rosters";
    private static final String LEAGUE = "/v1/league/" + FixtureLeagueHttp.LEAGUE;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private FixtureLeagueHttp sleeper;
    @Autowired private ObjectMapper json;
    /** Spied so a test can show another member's sync never spends this league's cooldown. */
    @MockitoSpyBean private LeagueLimits limits;

    private static long overridden;
    private static long corrected;
    private static long quarterback;

    /**
     * Players carrying the fixture's Sleeper ids. 1012 resolves only through a
     * reviewed override; 1014 is stored on one player and overridden to another,
     * and the override wins (S0's order); 1013 sits on two of our players, which
     * the crosswalk's write guard can leave behind (PR #75 review), so it must
     * stay unmatched.
     */
    @BeforeAll
    static void seed(@Autowired JdbcTemplate jdbc) {
        String[][] players = {{"1001", "Fixture Quarterback", "QB"}, {"1002", "Fixture Back One", "RB"},
                {"1003", "Fixture Back Two", "RB"}, {"1004", "Fixture Wideout One", "WR"},
                {"1005", "Fixture Wideout Two", "WR"}, {"1006", "Fixture Tight End", "TE"},
                {"1007", "Fixture Flex Receiver", "WR"}, {"1008", "Fixture Second Tight End", "TE"},
                {"1009", "Fixture Kicker", "K"}, {"1011", "Fixture Bench Back", "RB"},
                {"1014", "Fixture Rival Receiver", "WR"}, {"1015", "Fixture Rival Back", "RB"},
                {"1013", "Fixture Duplicate One", "WR"}, {"1013", "Fixture Duplicate Two", "WR"}};
        for (String[] p : players) {
            jdbc.update("INSERT INTO players (full_name, position, external_ids) VALUES (?, ?, jsonb_build_object('sleeper', ?::text))",
                    p[1], p[2], p[0]);
        }
        quarterback = jdbc.queryForObject("SELECT id FROM players WHERE full_name = 'Fixture Quarterback'", Long.class);
        overridden = jdbc.queryForObject(
                "INSERT INTO players (full_name, position) VALUES ('Fixture Renamed Receiver', 'WR') RETURNING id", Long.class);
        jdbc.update("INSERT INTO player_id_overrides (platform, external_id, player_id, reason) VALUES ('sleeper', '1012', ?, 'fixture')",
                overridden);
        corrected = jdbc.queryForObject(
                "INSERT INTO players (full_name, position) VALUES ('Fixture Corrected Receiver', 'WR') RETURNING id", Long.class);
        jdbc.update("INSERT INTO player_id_overrides (platform, external_id, player_id, reason) VALUES ('sleeper', '1014', ?, 'fixture')",
                corrected);
    }

    @BeforeEach
    void freshFixtures() {
        sleeper.reset();
    }

    @Test
    void anImportStoresTheLeagueAsTheMembersOwnCopy() {
        String member = member();

        ResponseEntity<JsonNode> imported = importFixture(member);

        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode league = imported.getBody();
        assertThat(league.path("name").asText()).isEqualTo("Fixture Redraft League");
        assertThat(league.path("myTeamExternalId").asText()).as("found by owner id").isEqualTo("1");
        assertThat(league.path("leagueType").asText()).isEqualTo("REDRAFT");
        assertThat(league.path("rosterSlots").toString()).isEqualTo(
                "{\"QB\":1,\"RB\":2,\"WR\":2,\"TE\":1,\"FLEX_RWT\":1,\"FLEX_WT\":1,\"K\":1,\"DST\":1,\"BENCH\":3,\"IR\":1}");
        assertThat(league.path("schedule").findValuesAsText("week")).containsExactly("8", "9");
        assertThat(league.path("schedule").get(0).path("opponentName").asText()).isEqualTo("second_manager");

        JsonNode mine = team(league, "1");
        assertThat(spot(mine, "1001").path("playerId").asLong()).isEqualTo(quarterback);
        assertThat(spot(mine, "1001").path("slot").asText()).isEqualTo("QB");
        assertThat(spot(mine, "1012").path("playerId").asLong()).as("through the override").isEqualTo(overridden);
        assertThat(spot(mine, "1012").path("slot").asText()).isEqualTo("IR");
        assertThat(spot(mine, "CLE").path("kind").asText()).isEqualTo("TEAM_DEFENSE");
        assertThat(spot(mine, "9999").path("kind").asText()).isEqualTo("UNMATCHED");
        assertThat(spot(team(league, "2"), "1014").path("playerId").asLong())
                .as("a reviewed override beats a stored id").isEqualTo(corrected);
        assertThat(spot(team(league, "2"), "1013").path("kind").asText())
                .as("an id two players share is refused, not guessed").isEqualTo("UNMATCHED");
        assertThat(league.path("unmatched").asInt()).as("1010, 9999 and 1013").isEqualTo(3);

        // No points anywhere: the platform's fpts is not stored or returned.
        assertThat(league.toString()).doesNotContain("fpts", "812");
    }

    /** Plan acceptance: an unmodelled key is reported, never dropped -- grouped for the member. */
    @Test
    void unmodelledScoringAndSlotsAreReported() {
        JsonNode league = importFixture(member()).getBody();

        List<String> keys = league.path("unmodeled").findValuesAsText("key");
        assertThat(keys).contains("fgm_0_19", "xpm", "pts_allow_0", "def_td", "DL");
        assertThat(league.path("unmodeled").toString()).contains("\"group\":\"KICKING\"",
                "\"group\":\"TEAM_DEFENSE\"", "\"group\":\"ROSTER\"");
        assertThat(league.path("approximations").get(0).asText()).contains("return touchdowns");
    }

    @Test
    void theLeaguesScoringBecomesAProfileTheMemberOwns() {
        String member = member();
        JsonNode league = importFixture(member).getBody();
        long profile = league.path("scoringProfileId").asLong();

        assertThat(jdbc.queryForObject("SELECT name FROM scoring_profiles WHERE id = ?", String.class, profile))
                .isEqualTo("Fixture Redraft League (Sleeper)");
        assertThat(get("/api/v1/scoring-profiles", member).getBody().toString())
                .contains("Fixture Redraft League (Sleeper)");
        assertThat(get("/api/v1/rankings?season=2026&profileId=" + profile, member).getStatusCode())
                .as("it scores like any profile").isEqualTo(HttpStatus.OK);
    }

    @Test
    void aProfileNameClashIsNumberedNotRefused() {
        String member = member();
        exchange("/api/v1/scoring-profiles", HttpMethod.POST, member, Map.of(
                "name", "Fixture Redraft League (Sleeper)", "rules", "{\"version\":1,\"base\":{\"rec\":1}}"));

        long profile = importFixture(member).getBody().path("scoringProfileId").asLong();

        assertThat(jdbc.queryForObject("SELECT name FROM scoring_profiles WHERE id = ?", String.class, profile))
                .isEqualTo("Fixture Redraft League (Sleeper) 2");
    }

    /** Never clamp: the league is imported, without a profile, and says why. */
    @Test
    void scoringThatDoesNotValidateImportsWithoutAProfileAndSaysWhy() {
        ObjectNode league = (ObjectNode) sleeper.fixture("league.json");
        ((ObjectNode) league.get("scoring_settings")).put("pass_td", 50);
        sleeper.answer(LEAGUE, league);

        JsonNode imported = importFixture(member()).getBody();

        assertThat(imported.path("scoringProfileId").isNull()).isTrue();
        assertThat(imported.path("scoringNote").asText()).contains("pass_td");
    }

    /** Plan acceptance: discovery reads Sleeper and writes nothing. */
    @Test
    void discoveryWritesNothing() {
        String member = member();
        long before = rows();

        ResponseEntity<JsonNode> found = exchange("/api/v1/leagues/sleeper/discover", HttpMethod.POST, member,
                Map.of("username", FixtureLeagueHttp.USERNAME, "season", 2026));

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().path("leagues")).hasSize(2);
        assertThat(found.getBody().path("leagues").get(0).path("importedLeagueId").isNull()).isTrue();
        assertThat(rows()).isEqualTo(before);

        long id = importFixture(member).getBody().path("id").asLong();
        JsonNode again = exchange("/api/v1/leagues/sleeper/discover", HttpMethod.POST, member,
                Map.of("username", FixtureLeagueHttp.USERNAME, "season", 2026)).getBody();
        assertThat(again.path("leagues").get(0).path("importedLeagueId").asLong()).isEqualTo(id);
    }

    /** The API is public: without the membership check any league id imports under any username. */
    @Test
    void aLeagueTheAccountIsNotInIsNotImported() {
        String stranger = "/v1/league/100000000000000099";
        ObjectNode league = (ObjectNode) sleeper.fixture("league.json");
        league.put("league_id", "100000000000000099");
        sleeper.answer(stranger, league);
        sleeper.answer(stranger + "/users", sleeper.fixture("users.json"));
        sleeper.answer(stranger + "/rosters", sleeper.fixture("rosters.json"));
        long before = rows();

        ResponseEntity<JsonNode> refused = exchange("/api/v1/leagues", HttpMethod.POST, member(), Map.of(
                "platform", "sleeper", "username", FixtureLeagueHttp.USERNAME,
                "externalLeagueId", "100000000000000099", "season", 2026));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rows()).isEqualTo(before);
    }

    @Test
    void importingTheSameLeagueTwiceIsAConflict() {
        String member = member();
        importFixture(member);

        assertThat(importFixture(member).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** One copy per member: two members of one league never share a row. */
    @Test
    void twoMembersOfOneLeagueHoldSeparateCopies() {
        long first = importFixture(member()).getBody().path("id").asLong();
        long second = importFixture(member()).getBody().path("id").asLong();

        assertThat(first).isNotEqualTo(second);
    }

    /** Plan acceptance: member B gets a 404 -- not 403 -- on member A's league, on every route. */
    @Test
    void anotherMembersLeagueIsNotFoundOnEveryRoute() {
        String alice = member();
        String bob = member();
        JsonNode league = importFixture(alice).getBody();
        long id = league.path("id").asLong();
        String path = "/api/v1/leagues/" + id;
        sleeper.requested.clear();

        assertThat(get(path, bob).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(path + "/sync", HttpMethod.POST, bob, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(path + "/my-team", HttpMethod.PUT, bob, Map.of("externalTeamId", "2")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(path, HttpMethod.DELETE, bob, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/leagues", bob).getBody()).isEmpty();
        assertThat(sleeper.requested).as("ownership is checked before Sleeper is called").isEmpty();
        verify(limits, never()).sync(id);

        JsonNode still = get(path, alice).getBody();
        assertThat(still.path("myTeamExternalId").asText()).isEqualTo("1");
        assertThat(still.path("scoringProfileId").asLong()).isEqualTo(league.path("scoringProfileId").asLong());
        assertThat(get("/api/v1/scoring-profiles", bob).getBody().toString())
                .as("nor can bob see the league's profile").doesNotContain("Fixture Redraft League (Sleeper)");
    }

    @Test
    void readsAndWritesNeedAnAccount() {
        assertThat(get("/api/v1/leagues", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange("/api/v1/leagues/sleeper/discover", HttpMethod.POST, null,
                Map.of("username", FixtureLeagueHttp.USERNAME, "season", 2026)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(sleeper.requested).isEmpty();
    }

    @Test
    void aSyncReplacesTheSnapshotAndKeepsTheProfile() {
        String member = member();
        JsonNode league = importFixture(member).getBody();
        long id = league.path("id").asLong();
        ArrayNode rosters = (ArrayNode) sleeper.fixture("rosters.json");
        ((ObjectNode) rosters.get(0)).set("players", json.valueToTree(List.of("1001", "1015")));
        ((ObjectNode) rosters.get(0)).set("starters", json.valueToTree(List.of("1001")));
        ((ObjectNode) rosters.get(0)).set("reserve", null);
        ((ObjectNode) rosters.get(3)).set("players", json.valueToTree(List.of()));
        ((ObjectNode) rosters.get(3)).set("starters", json.valueToTree(List.of()));
        ((ObjectNode) rosters.get(0)).set("settings", json.valueToTree(Map.of("wins", 6, "losses", 2, "ties", 0)));
        sleeper.answer(ROSTERS, rosters);
        sleeper.requested.clear();

        JsonNode synced = exchange("/api/v1/leagues/" + id + "/sync", HttpMethod.POST, member, null).getBody();

        assertThat(team(synced, "1").path("roster").findValuesAsText("externalPlayerId"))
                .containsExactlyInAnyOrder("1001", "1015");
        assertThat(team(synced, "1").path("wins").asInt()).isEqualTo(6);
        assertThat(synced.path("scoringProfileId").asLong()).as("updated in place")
                .isEqualTo(league.path("scoringProfileId").asLong());
        assertThat(sleeper.requested).as("stored weeks 8 and 9 are not fetched again")
                .noneMatch(p -> p.endsWith("/matchups/8") || p.endsWith("/matchups/9"))
                .anyMatch(p -> p.endsWith("/matchups/10"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scoring_profiles WHERE name LIKE 'Fixture Redraft League (Sleeper)%' AND user_id = (SELECT user_id FROM leagues WHERE id = ?)",
                Integer.class, id)).isEqualTo(1);
    }

    /** A failed fetch leaves the previous snapshot exactly as it was. */
    @Test
    void aSyncThatFailsHalfwayChangesNothing() {
        String member = member();
        long id = importFixture(member).getBody().path("id").asLong();
        String before = get("/api/v1/leagues/" + id, member).getBody().toString();
        sleeper.answer(ROSTERS, json.createObjectNode()); // not a list: unreadable

        ResponseEntity<JsonNode> failed = exchange("/api/v1/leagues/" + id + "/sync", HttpMethod.POST, member, null);

        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(get("/api/v1/leagues/" + id, member).getBody().toString()).isEqualTo(before);
    }

    @Test
    void theMemberCanChooseTheirTeam_butOnlyOneInTheLeague() {
        String member = member();
        long id = importFixture(member).getBody().path("id").asLong();
        String path = "/api/v1/leagues/" + id + "/my-team";

        assertThat(exchange(path, HttpMethod.PUT, member, Map.of("externalTeamId", "3")).getBody()
                .path("myTeamExternalId").asText()).isEqualTo("3");
        assertThat(exchange(path, HttpMethod.PUT, member, Map.of("externalTeamId", "77")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingRemovesTheLeagueItsProfileAndAnIdleConnection() {
        String member = member();
        JsonNode league = importFixture(member).getBody();
        long id = league.path("id").asLong();
        long profile = league.path("scoringProfileId").asLong();

        assertThat(exchange("/api/v1/leagues/" + id, HttpMethod.DELETE, member, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(get("/api/v1/leagues/" + id, member).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        for (String table : List.of("league_teams", "league_rosters", "league_matchups")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE league_id = ?", Integer.class, id))
                    .as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scoring_profiles WHERE id = ?", Integer.class, profile)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM league_connections c
                 WHERE NOT EXISTS (SELECT 1 FROM leagues l WHERE l.connection_id = c.id)
                """, Integer.class)).isZero();
    }

    /** QuerySafetyTests, for every new parameter: the attack is refused and the tables survive. */
    @Test
    void injectionThroughEveryParameterIsRefusedAndChangesNothing() {
        String member = member();
        long id = importFixture(member).getBody().path("id").asLong();
        long before = rows();
        String attack = "1'; DROP TABLE leagues; --";

        assertThat(exchange("/api/v1/leagues/sleeper/discover", HttpMethod.POST, member,
                Map.of("username", attack, "season", 2026)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange("/api/v1/leagues", HttpMethod.POST, member, Map.of("platform", "sleeper",
                "username", FixtureLeagueHttp.USERNAME, "externalLeagueId", attack, "season", 2026))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange("/api/v1/leagues", HttpMethod.POST, member, Map.of("platform", "espn' OR '1'='1",
                "username", FixtureLeagueHttp.USERNAME, "externalLeagueId", FixtureLeagueHttp.LEAGUE, "season", 2026))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange("/api/v1/leagues", HttpMethod.POST, member, Map.of("platform", "sleeper",
                "username", "../../v1/state", "externalLeagueId", FixtureLeagueHttp.LEAGUE, "season", 1999))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/v1/leagues/1 OR 1=1", member).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange("/api/v1/leagues/" + id + "/my-team", HttpMethod.PUT, member,
                Map.of("externalTeamId", attack)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(sleeper.requested).as("no attack reached Sleeper").allMatch(p -> !p.contains("DROP"));
        assertThat(rows()).isEqualTo(before);
        assertThat(get("/api/v1/leagues/" + id, member).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- S2: team needs -------------------------------------------------------------------

    /** The fixture league as a 2090 league: a schedule decades ahead, so these never age. */
    private static final int FUTURE = 2090;

    /**
     * 2089 history and a 2090 schedule for three of the member's players. The quarterback throws
     * 250 yards and 2 touchdowns a game: 22 a week under the league's scoring (0.04 a yard, 6 a
     * touchdown). His team plays weeks 1-14 except a week-6 bye; the league's playoffs start in 15.
     */
    private ResponseEntity<JsonNode> importFuture(String member) {
        ObjectNode league = (ObjectNode) sleeper.fixture("league.json");
        league.put("season", String.valueOf(FUTURE));
        sleeper.answer(LEAGUE, league);
        ArrayNode leagues = (ArrayNode) sleeper.fixture("user_leagues.json");
        leagues.forEach(l -> ((ObjectNode) l).put("season", String.valueOf(FUTURE)));
        sleeper.answer("/v1/user/" + FixtureLeagueHttp.OWNER + "/leagues/nfl/" + FUTURE, leagues);
        sleeper.answer("/v1/state/nfl", json.createObjectNode().put("season", String.valueOf(FUTURE))
                .put("season_type", "pre").put("week", 0));
        seedFutureSeason();
        return exchange("/api/v1/leagues", HttpMethod.POST, member, Map.of("platform", "sleeper",
                "username", FixtureLeagueHttp.USERNAME, "externalLeagueId", FixtureLeagueHttp.LEAGUE, "season", FUTURE));
    }

    private static boolean futureSeeded;

    private void seedFutureSeason() {
        if (futureSeeded) {
            return;
        }
        futureSeeded = true;
        int home = team("FKA");
        int away = team("FKB");
        int third = team("FKC");
        jdbc.update("""
                UPDATE players SET team_id = ? WHERE full_name IN
                    ('Fixture Quarterback', 'Fixture Wideout One', 'Fixture Wideout Two')
                """, home);
        for (int week = 1; week <= 2; week++) {
            long game = game(FUTURE - 1, week, home, away);
            stat(game, "Fixture Quarterback", FUTURE - 1, week, home, "pass_yd", 250, "pass_td", 2);
            stat(game, "Fixture Wideout One", FUTURE - 1, week, home, "rec", 6, "rec_yd", 90);
            stat(game, "Fixture Wideout Two", FUTURE - 1, week, home, "rec", 3, "rec_yd", 30);
        }
        for (int week = 1; week <= 18; week++) {
            if (week == 6) {
                game(FUTURE, week, away, third); // the home team's bye
            } else {
                game(FUTURE, week, home, away);
            }
        }
    }

    private int team(String abbr) {
        return jdbc.queryForObject("""
                INSERT INTO teams (abbr, name) VALUES (?, ?)
                ON CONFLICT (abbr) DO UPDATE SET name = EXCLUDED.name RETURNING id
                """, Integer.class, abbr, abbr);
    }

    private long game(int season, int week, int home, int away) {
        return jdbc.queryForObject("""
                INSERT INTO games (nflverse_game_id, season, week, season_type, home_team_id, away_team_id, kickoff_at)
                VALUES (?, ?, ?, 'REG', ?, ?, make_timestamptz(?, 9, 1, 17, 0, 0) + make_interval(weeks => ?))
                RETURNING id
                """, Long.class, "fk_%d_%02d_%d".formatted(season, week, home), season, week, home, away,
                season, week - 1);
    }

    private void stat(long game, String player, int season, int week, int team, String k1, int v1, String k2, int v2) {
        // Column names come from this test's own literals, never from input.
        jdbc.update("INSERT INTO player_game_stats (player_id, game_id, season, week, team_id, " + k1 + ", " + k2 + ")"
                + " SELECT id, ?, ?, ?, ?, ?, ? FROM players WHERE full_name = ?",
                game, season, week, team, v1, v2, player);
    }

    private static JsonNode slot(JsonNode needs, String label) {
        for (JsonNode slot : needs.path("slots")) {
            if (slot.path("label").asText().equals(label)) {
                return slot;
            }
        }
        throw new AssertionError("no slot " + label + " in " + needs.path("slots"));
    }

    /**
     * Hand-computed. The quarterback is 22 a game and misses one week of 14 to his bye: 20.43 a
     * week. No other team has a quarterback, so the league's median QB is 0 and he is +20.43.
     */
    @Test
    void teamNeedsCompareEachStartingSlotWithTheLeagueMedian() {
        String member = member();
        long id = importFuture(member).getBody().path("id").asLong();

        JsonNode needs = get("/api/v1/leagues/" + id + "/needs", member).getBody();

        assertThat(needs.path("weeks")).hasSize(14);
        assertThat(needs.path("slots").findValuesAsText("label")).containsExactly(
                "QB", "RB1", "RB2", "WR1", "WR2", "TE", "Flex", "W/T flex", "K", "D/ST", "DL");
        JsonNode qb = slot(needs, "QB");
        assertThat(qb.path("mine").asDouble()).isEqualTo(20.43);
        assertThat(qb.path("median").asDouble()).isEqualTo(0.0);
        assertThat(qb.path("diff").asDouble()).isEqualTo(20.43);
        assertThat(qb.path("starterName").asText()).isEqualTo("Fixture Quarterback");
        assertThat(slot(needs, "WR1").path("starterName").asText()).as("the stronger receiver")
                .isEqualTo("Fixture Wideout One");
        for (String unmodelled : List.of("K", "D/ST", "DL")) {
            assertThat(slot(needs, unmodelled).path("modeled").asBoolean()).as(unmodelled).isFalse();
            assertThat(slot(needs, unmodelled).path("mine").isNull()).isTrue();
        }

        JsonNode quarterback = null;
        for (JsonNode player : needs.path("players")) {
            if (player.path("externalPlayerId").asText().equals("1001")) {
                quarterback = player;
            }
        }
        assertThat(quarterback).isNotNull();
        assertThat(quarterback.path("perGame").asDouble()).isEqualTo(22.0);
        assertThat(quarterback.path("perWeek").asDouble()).isEqualTo(20.43);
        assertThat(quarterback.path("weeks").get(5).path("points").asDouble()).as("week 6, the bye").isZero();
        assertThat(quarterback.path("parts").findValuesAsText("stat")).contains("pass_yd", "pass_td");
        assertThat(needs.path("noProjection").asInt()).as("rostered skill players with no history").isPositive();
    }

    /** Computed on read: asking writes nothing, and another member's league is a 404. */
    @Test
    void teamNeedsWriteNothingAndBelongToTheirOwner() {
        String member = member();
        long id = importFuture(member).getBody().path("id").asLong();
        long before = rows();

        assertThat(get("/api/v1/leagues/" + id + "/needs", member).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rows()).isEqualTo(before);
        assertThat(get("/api/v1/leagues/" + id + "/needs", member()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** No ruleset, no projection: a 409 that says why, never a guessed ruleset. */
    @Test
    void teamNeedsRefuseALeagueWithoutARuleset() {
        String member = member();
        JsonNode league = importFuture(member).getBody();
        exchange("/api/v1/scoring-profiles/" + league.path("scoringProfileId").asLong(), HttpMethod.DELETE, member, null);

        ResponseEntity<JsonNode> refused = get("/api/v1/leagues/" + league.path("id").asLong() + "/needs", member);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().path("detail").asText()).contains("ruleset");
    }

    private ResponseEntity<JsonNode> importFixture(String member) {
        return exchange("/api/v1/leagues", HttpMethod.POST, member, Map.of("platform", "sleeper",
                "username", FixtureLeagueHttp.USERNAME, "externalLeagueId", FixtureLeagueHttp.LEAGUE, "season", 2026));
    }

    private static JsonNode team(JsonNode league, String id) {
        for (JsonNode team : league.path("teams")) {
            if (team.path("externalTeamId").asText().equals(id)) {
                return team;
            }
        }
        throw new AssertionError("no team " + id);
    }

    private static JsonNode spot(JsonNode team, String playerId) {
        for (JsonNode spot : team.path("roster")) {
            if (spot.path("externalPlayerId").asText().equals(playerId)) {
                return spot;
            }
        }
        throw new AssertionError("no roster spot " + playerId);
    }

    /** Every league row, plus scoring profiles: what a refused request must leave alone. */
    private long rows() {
        long total = 0;
        for (String table : List.of("league_connections", "leagues", "league_teams", "league_rosters",
                "league_matchups", "scoring_profiles")) {
            total += jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        }
        return total;
    }

    private String member() {
        Long id = jdbc.queryForObject("INSERT INTO users (email, password_hash) VALUES (?, 'not-a-hash') RETURNING id",
                Long.class, "league-member-" + MEMBERS.incrementAndGet() + "@example.com");
        return jwt.issue(id);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return exchange(path, HttpMethod.GET, token, null);
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
