package com.fantasykai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
 * Phase 5 acceptance, in the shape {@code QuerySafetyTests} set: prove the
 * constraint by attempting to violate it, then check what survived.
 *
 * <p>These are the token-lifecycle half. Tenant isolation is
 * {@link com.fantasykai.api.ScoringProfileIsolationTests}, and the rate limit
 * is {@link AuthRateLimitTests}, which needs a Redis of its own.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthTests {

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

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthProperties props;

    @Test
    void registeringStoresAnArgon2idHashAndNeverThePassword() {
        register("argon@example.com");

        String stored = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, "argon@example.com");

        assertThat(stored)
                .as("Argon2id, not BCrypt and not a digest")
                .startsWith("$argon2id$")
                .doesNotContain(PASSWORD);
    }

    /** citext, so uniqueness is case-insensitive without a LOWER() anywhere in Java. */
    @Test
    void treatsAnEmailAsTheSameAccountWhateverItsCase() {
        register("Case@Example.com");

        ResponseEntity<String> duplicate = post("/api/v1/auth/register",
                body("cAsE@example.COM", PASSWORD));

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    /**
     * The same message either way, so nobody can enumerate who has an account.
     */
    @Test
    void refusesAWrongPasswordAndAnUnknownEmailIdentically() {
        register("real@example.com");

        ResponseEntity<String> wrongPassword =
                post("/api/v1/auth/login", body("real@example.com", "not-the-password"));
        ResponseEntity<String> noSuchUser =
                post("/api/v1/auth/login", body("ghost@example.com", PASSWORD));

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(noSuchUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(noSuchUser.getBody())
                .as("an attacker must not be able to tell these apart")
                .isEqualTo(wrongPassword.getBody());
    }

    /**
     * Acceptance 2, and the most important thing in this file.
     *
     * <p>Rotation alone is not a security property; detecting the replay is.
     * A stolen refresh token is only harmless if using it a second time kills
     * every token descended from the same login.
     */
    @Test
    void replayingAConsumedRefreshTokenRevokesTheWholeFamily() {
        String stolen = refreshCookie(register("replay@example.com"));

        // The legitimate client refreshes once. The thief now holds a consumed token.
        ResponseEntity<String> honest = refresh(stolen);
        assertThat(honest.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotated = refreshCookie(honest);

        // The thief replays.
        assertThat(refresh(stolen).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // ...and the legitimate client's brand new token is dead too, because
        // there is no way to tell which of the two parties was the thief.
        assertThat(refresh(rotated).getStatusCode())
                .as("the whole family is revoked, not just the replayed token")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        Long live = jdbc.queryForObject("""
                SELECT count(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id
                 WHERE u.email = ? AND t.revoked_at IS NULL AND t.consumed_at IS NULL
                """, Long.class, "replay@example.com");
        assertThat(live).isZero();
    }

    @Test
    void rotationIssuesADifferentTokenEveryTime() {
        String first = refreshCookie(register("rotate@example.com"));
        String second = refreshCookie(refresh(first));

        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT t.family_id) FROM refresh_tokens t
                  JOIN users u ON u.id = t.user_id WHERE u.email = ?
                """, Long.class, "rotate@example.com"))
                .as("a rotation stays in its family; only a login starts a new one")
                .isEqualTo(1L);
    }

    /** The token is never stored in a form that a database dump could replay. */
    @Test
    void storesOnlyAHashOfTheRefreshToken() {
        String issued = refreshCookie(register("hash@example.com"));

        List<String> stored = jdbc.queryForList("SELECT token_hash FROM refresh_tokens", String.class);

        assertThat(stored).isNotEmpty().noneMatch(hash -> hash.contains(issued));
        assertThat(stored.getFirst()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void logoutRevokesTheFamilyAndIsIdempotent() {
        String token = refreshCookie(register("bye@example.com"));

        assertThat(logout(token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(refresh(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(logout(token).getStatusCode())
                .as("logging out twice is not an error")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    /** Acceptance 5, first half. */
    @Test
    void rejectsAJwtSignedWithTheWrongSecret() {
        String forged = new JwtService(
                new AuthProperties("a-different-key-of-at-least-32-bytes!!", props.accessTokenTtl(),
                        props.refreshTokenTtl(), 5, List.of("http://localhost:3000")),
                Clock.systemUTC()).issue(1);

        assertThat(getWithToken("/api/v1/scoring-profiles", forged).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Acceptance 5, second half: 401, and specifically not 500. */
    @Test
    void rejectsAnExpiredJwtAsUnauthorizedRatherThanAServerError() {
        Clock anHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneId.of("UTC"));
        String stale = new JwtService(props, anHourAgo).issue(1);

        ResponseEntity<String> response = getWithToken("/api/v1/scoring-profiles", stale);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .as("the filter chain runs before @RestControllerAdvice, so this "
                        + "only holds because an entry point writes problem+json itself")
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("expired");
    }

    /** A garbage token is refused rather than quietly treated as logged out. */
    @Test
    void rejectsAMalformedBearerTokenRatherThanIgnoringIt() {
        assertThat(getWithToken("/api/v1/scoring-profiles", "not.a.jwt").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * The cookie carries the flags that make the access/refresh split worth
     * having. Without HttpOnly the refresh token is readable by any XSS, and
     * the 15-minute access token stops being a mitigation for anything.
     */
    @Test
    void sendsTheRefreshTokenAsAnHttpOnlyStrictlySameSiteCookie() {
        String setCookie = register("cookie@example.com")
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
    }

    /** The access token goes in the body; the refresh token must not. */
    @Test
    void neverPutsTheRefreshTokenInAResponseBody() {
        ResponseEntity<String> registered = register("body@example.com");
        String cookie = refreshCookie(registered);

        assertThat(registered.getBody()).doesNotContain(cookie).contains("accessToken");
    }

    @Test
    void refusesAPasswordShorterThanTwelveCharacters() {
        ResponseEntity<String> response =
                post("/api/v1/auth/register", body("short@example.com", "hunter2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    // ---- helpers -------------------------------------------------------

    private ResponseEntity<String> register(String email) {
        ResponseEntity<String> response = post("/api/v1/auth/register", body(email, PASSWORD));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response;
    }

    private ResponseEntity<String> post(String path, Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> refresh(String cookie) {
        return withCookie("/api/v1/auth/refresh", cookie);
    }

    private ResponseEntity<String> logout(String cookie) {
        return withCookie("/api/v1/auth/logout", cookie);
    }

    private ResponseEntity<String> withCookie(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + cookie);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static Map<String, String> body(String email, String password) {
        return Map.of("email", email, "password", password);
    }

    private static String refreshCookie(ResponseEntity<String> response) {
        String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(header).as("no refresh cookie on the response").isNotNull();
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }
}
