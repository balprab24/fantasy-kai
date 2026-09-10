package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tenant isolation, which is the single most important thing in Phase 5.
 *
 * <p>The crown jewels here are not the stats -- those are public CC BY 4.0 data
 * -- they are credentials and custom rulesets. A league's scoring settings are
 * the one thing in this database that belongs to a person, so "user A cannot
 * read user B's profiles" is the property the whole phase exists to deliver.
 *
 * <p>Written the {@code QuerySafetyTests} way: perform the attack, then check
 * what survived.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScoringProfileIsolationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * These tests register several users, which is several {@code /auth}
     * requests from one address. The limiter is a real filter in a real chain
     * here, so it needs a real Redis -- and a limit high enough that the class
     * measures what it is about rather than measuring the limiter.
     * {@link AuthRateLimitTests} is where the limit itself is tested.
     */
    @Container
    static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("fantasykai.auth.login-attempts-per-minute", () -> 1000);
    }

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final String RULES = """
            {"version":1,"base":{"rec":1.5,"rec_yd":0.1,"rec_td":6}}""";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    static void seed(@Autowired JdbcTemplate jdbc) {
        ApiFixture.seed(jdbc);
    }

    /**
     * Acceptance 1. A profile you do not own is <strong>404, not 403</strong>:
     * 403 would confirm the id exists, which is exactly the fact being kept.
     */
    @Test
    void userACannotReadUserBsProfile() {
        String alice = tokenFor("alice@example.com");
        String bob = tokenFor("bob@example.com");
        long bobsProfile = createProfile(bob, "Bob's League");

        assertThat(get("/api/v1/scoring-profiles", alice).getBody())
                .as("it does not appear in her list")
                .doesNotContain("Bob's League");

        ResponseEntity<String> ranked = get(
                "/api/v1/rankings?season=2025&profileId=" + bobsProfile, alice);
        assertThat(ranked.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ranked.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    /**
     * Acceptance 6, and the reason it is written as an <em>ordering</em> rather
     * than an assertion.
     *
     * <p>{@code ScoringProfiles} memoizes the compiled ruleset by profile id.
     * The tenant filter lives in the query, which only runs on a cache miss --
     * so if Bob reads his own profile first, a plain
     * {@code computeIfAbsent(profileId, ...)} hands it to Alice next without
     * the filter executing at all. Run the other way round this test passes
     * against the vulnerable code, because Alice's own miss does the filtering.
     * The warm-up is the test.
     */
    @Test
    void aWarmCacheIsNotAnAuthorizationBypass() {
        String bob = tokenFor("cache-bob@example.com");
        String alice = tokenFor("cache-alice@example.com");
        long bobsProfile = createProfile(bob, "Cached League");

        // Bob uses it, which puts the compiled ruleset in the cache.
        assertThat(get("/api/v1/rankings?season=2025&profileId=" + bobsProfile, bob).getStatusCode())
                .as("Bob can use his own profile, and this is what warms the cache")
                .isEqualTo(HttpStatus.OK);

        assertThat(get("/api/v1/rankings?season=2025&profileId=" + bobsProfile, alice).getStatusCode())
                .as("a cache hit must re-check the owner; the query cannot, it did not run")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** The same hole, one endpoint over. Both take profileId as a parameter. */
    @Test
    void theGameLogHonoursTheSameOwnership() {
        String bob = tokenFor("log-bob@example.com");
        String alice = tokenFor("log-alice@example.com");
        long bobsProfile = createProfile(bob, "Log League");
        // full_name is not unique and the fixture seeds both Josh Allens on
        // purpose -- a quarterback and a center. Keying a lookup on the name is
        // the trap; this test fell into it once.
        long playerId = jdbc.queryForObject(
                "SELECT id FROM players WHERE full_name = 'Josh Allen' AND position = 'QB'",
                Long.class);

        assertThat(get("/api/v1/players/%d/gamelog?season=2025&profileId=%d"
                .formatted(playerId, bobsProfile), bob).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/players/%d/gamelog?season=2025&profileId=%d"
                .formatted(playerId, bobsProfile), alice).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Acceptance 3. Reads are public; the presets are what a logged-out caller gets. */
    @Test
    void readsArePublicAndResolvePresetsForALoggedOutCaller() {
        assertThat(get("/api/v1/rankings?season=2025&profileId=3", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/players?size=1", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        String anonymousList = get("/api/v1/scoring-profiles", null).getBody();
        assertThat(anonymousList)
                .contains("Standard", "Half PPR", "Full PPR", "TE Premium");
    }

    /** Acceptance 3, second half. */
    @Test
    void writingAProfileNeedsAToken() {
        ResponseEntity<String> anonymous = post("/api/v1/scoring-profiles", null,
                Map.of("name", "Sneaky", "rules", RULES));

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM scoring_profiles WHERE name = 'Sneaky'", Long.class))
                .isZero();
    }

    /** The filter is in the UPDATE, so there is no window between check and write. */
    @Test
    void userACannotEditOrDeleteUserBsProfile() {
        String bob = tokenFor("edit-bob@example.com");
        String alice = tokenFor("edit-alice@example.com");
        long bobsProfile = createProfile(bob, "Untouchable");

        assertThat(exchange("/api/v1/scoring-profiles/" + bobsProfile, HttpMethod.PUT, alice,
                Map.of("name", "Stolen", "rules", RULES)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("/api/v1/scoring-profiles/" + bobsProfile, HttpMethod.DELETE, alice, null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(jdbc.queryForObject("SELECT name FROM scoring_profiles WHERE id = ?",
                String.class, bobsProfile))
                .as("the attempt changed nothing")
                .isEqualTo("Untouchable");
    }

    /** Nobody may edit a preset, including the user who is otherwise allowed to write. */
    @Test
    void nobodyCanEditASystemPreset() {
        String alice = tokenFor("preset@example.com");

        assertThat(exchange("/api/v1/scoring-profiles/3", HttpMethod.PUT, alice,
                Map.of("name", "Hijacked PPR", "rules", RULES)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("/api/v1/scoring-profiles/3", HttpMethod.DELETE, alice, null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(jdbc.queryForObject("SELECT name FROM scoring_profiles WHERE id = 3", String.class))
                .isEqualTo("Full PPR");
    }

    /**
     * The reason {@code evict} existed from Phase 2 with no callers. Without the
     * call, an edited ruleset keeps scoring with its old rates until restart --
     * silently, and only for the person who edited it.
     */
    @Test
    void editingAProfileChangesTheNumbersItProduces() {
        String user = tokenFor("evict@example.com");
        long profile = createProfile(user, "Before", """
                {"version":1,"base":{"pass_td":4}}""");

        double before = totalPoints(get(
                "/api/v1/rankings?season=2025&position=QB&profileId=" + profile, user).getBody());

        assertThat(exchange("/api/v1/scoring-profiles/" + profile, HttpMethod.PUT, user,
                Map.of("name", "After", "rules", """
                        {"version":1,"base":{"pass_td":8}}""")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        double after = totalPoints(get(
                "/api/v1/rankings?season=2025&position=QB&profileId=" + profile, user).getBody());

        assertThat(after)
                .as("doubling the touchdown rate must double the touchdown points")
                .isGreaterThan(before);
    }

    /** A ruleset that cannot compile must never reach the table. */
    @Test
    void refusesToStoreARulesetThatDoesNotValidate() {
        String user = tokenFor("bad-rules@example.com");

        ResponseEntity<String> response = post("/api/v1/scoring-profiles", user,
                Map.of("name", "Absurd", "rules", """
                        {"version":1,"base":{"rec":9999}}"""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM scoring_profiles WHERE name = 'Absurd'", Long.class)).isZero();
    }

    /** V5's partial unique index, so a profile switcher cannot show two identical entries. */
    @Test
    void refusesTwoProfilesWithTheSameNameForOneUser() {
        String user = tokenFor("dupe@example.com");
        createProfile(user, "My League");

        assertThat(post("/api/v1/scoring-profiles", user,
                Map.of("name", "My League", "rules", RULES)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /** ...but two different users may both call theirs "My League". */
    @Test
    void allowsTwoUsersToUseTheSameProfileName() {
        createProfile(tokenFor("same1@example.com"), "Shared Name");

        assertThat(post("/api/v1/scoring-profiles", tokenFor("same2@example.com"),
                Map.of("name", "Shared Name", "rules", RULES)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    // ---- helpers -------------------------------------------------------

    private String tokenFor(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/register",
                new HttpEntity<>(Map.of("email", email, "password", PASSWORD), headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("accessToken");
    }

    private long createProfile(String token, String name) {
        return createProfile(token, name, RULES);
    }

    private long createProfile(String token, String name, String rules) {
        ResponseEntity<String> response =
                post("/api/v1/scoring-profiles", token, Map.of("name", name, "rules", rules));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbc.queryForObject("SELECT id FROM scoring_profiles WHERE name = ?",
                Long.class, name);
    }

    private ResponseEntity<String> get(String path, String token) {
        return exchange(path, HttpMethod.GET, token, null);
    }

    private ResponseEntity<String> post(String path, String token, Map<String, String> body) {
        return exchange(path, HttpMethod.POST, token, body);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String token,
            Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    /** Sum of the "points" fields in a rankings page, without a DTO. */
    private static double totalPoints(String json) {
        double total = 0;
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\"points\":(-?[0-9.]+)").matcher(json);
        while (m.find()) {
            total += Double.parseDouble(m.group(1));
        }
        return total;
    }
}
