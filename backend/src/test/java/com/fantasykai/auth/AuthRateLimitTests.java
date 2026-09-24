package com.fantasykai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance 4: the 6th login in a minute is 429, and the 6th read is not.
 *
 * <p>The only test class that needs Redis, because the bucket store is resolved
 * lazily -- a Redis outage costs you logins and nothing else, which is also why
 * the other integration classes get to run without a second container.
 *
 * <p>Real Redis rather than a stub: the limiter's whole reason for being
 * distributed is that the count survives outside one JVM, and an in-memory
 * double would test the opposite of the thing that matters.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthRateLimitTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

    /** The limit, from application.yml. Handoff §8 says 5/min/IP on /auth. */
    private static final int LIMIT = 5;

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate rest;

    /**
     * Every test here comes from the same peer, so they all share one bucket.
     * Without this, a test that asserts "the 6th is 429" can pass because an
     * earlier test already drained the bucket -- which, for the bypass tests
     * below, would turn a live bypass into a green bar.
     */
    @BeforeEach
    void emptyTheBuckets() throws Exception {
        REDIS.execInContainer("redis-cli", "FLUSHALL");
    }

    @Test
    void refusesTheSixthAuthenticationAttemptInAMinute() {
        // Wrong password on purpose: the limiter must count attempts, not
        // successes, or it protects nothing against password guessing.
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            assertThat(login("nobody@example.com").getStatusCode())
                    .as("attempt %d of %d is within the limit", attempt, LIMIT)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<String> sixth = login("nobody@example.com");

        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(sixth.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(sixth.getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }

    /**
     * The bucket is per IP, not per email, so rotating the email does not reset
     * it. That is the whole point: an attacker guessing passwords changes the
     * password, and one enumerating accounts changes the email.
     */
    @Test
    void countsPerAddressRatherThanPerAccount() {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            login("distinct-%d@example.com".formatted(attempt));
        }

        assertThat(login("yet-another@example.com").getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /** The reads stay loose. Throttling them would be throttling the product. */
    @Test
    void doesNotThrottleTheReadEndpoints() {
        for (int request = 0; request < LIMIT * 3; request++) {
            assertThat(rest.getForEntity("/api/v1/scoring-profiles", String.class).getStatusCode())
                    .as("read %d", request)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    /**
     * <strong>A forged {@code X-Forwarded-For} DOES buy a fresh bucket, and this
     * test exists to keep that visible.</strong>
     *
     * <p>It is written the way it is because the opposite assertion was tried
     * first and <em>failed</em>: six logins carrying a varying forged header
     * returned {@code 401}, not {@code 429}. So the limiter is bypassable at the
     * application layer by anyone who can set one header. That is not a
     * hypothesis about the code, it is what this class printed.
     *
     * <p><strong>Why it is nevertheless not a live vulnerability.</strong> The
     * protection is entirely external: Caddy, with {@code trusted_proxies}
     * unset, discards an incoming {@code X-Forwarded-For} and writes the real
     * peer, and {@code compose.prod.yml} gives the backend {@code expose} with
     * no published port, so nothing can reach it except through Caddy. Both
     * halves are load-bearing. Publish that port, put a CDN in front, or
     * configure {@code trusted_proxies}, and the 5/min limit on
     * {@code /api/v1/auth/**} becomes decoration — an attacker varies the fake
     * address and gets a fresh five attempts every request.
     *
     * <p>That argument covered {@code X-Forwarded-For} and nothing else: on
     * 2026-09-24 {@code Forwarded}, {@code X-Forwarded-Prefix} and a
     * percent-encoded path all went straight through Caddy. The three tests
     * after this one are those holes, closed in the application.
     *
     * <p><strong>The mechanism is not the one the code appears to use.</strong>
     * {@code application.yml} sets {@code server.forward-headers-strategy: framework},
     * which installs Spring's {@code ForwardedHeaderFilter} ahead of the security
     * chain. Its wrapper extends {@code ForwardedHeaderRemovingRequest}, so by
     * the time {@link AuthRateLimitFilter} runs {@code getHeader("X-Forwarded-For")}
     * is already null and {@code getRemoteAddr()} has been rewritten from the
     * forwarded value. The filter's own {@code X-Forwarded-For} branch is dead
     * code while that property is set — and the fallback it drops through to
     * trusts the forged value just the same. Removing the branch would change
     * nothing; the trust lives in the property, not in the filter.
     *
     * <p>Asserting the true behaviour rather than the desired one is deliberate.
     * A test that asserted {@code 429} here would have to be made to pass by
     * changing the deployment model, and it would go green while saying nothing
     * about whether the deployment still held. This one fails the moment the
     * application stops trusting the header, which is exactly when
     * {@code deploy/README.md} and {@code deploy/Caddyfile} need rereading.
     */
    @Test
    void aForgedForwardedForBuysAFreshBucket_whichIsWhyCaddyMustReplaceIt() {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            login("nobody@example.com", "203.0.113.%d".formatted(attempt + 1));
        }

        assertThat(login("nobody@example.com", "203.0.113.99").getStatusCode())
                .as("the application trusts X-Forwarded-For, so a forged one is a fresh bucket; "
                        + "if this is no longer 401, the trust model changed and the deploy docs "
                        + "and Caddyfile both need rereading")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * The counterpart: with no forwarded header to vary, the limiter does bite.
     *
     * <p>Together with the test above this separates the two questions that
     * {@code deploy/README.md} acceptance check 4a could not tell apart — whether
     * the limiter counts at all, and whether the key it counts on is forgeable.
     */
    @Test
    void withoutAForwardedHeaderTheBucketIsSharedAcrossAttempts() {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            login("nobody@example.com");
        }

        assertThat(login("nobody@example.com").getStatusCode())
                .as("same peer, no forwarded header: one bucket")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * RFC 7239 {@code Forwarded} is a second spelling of the client address, and
     * Spring's {@code ForwardedHeaderUtils} reads it <em>before</em>
     * {@code X-Forwarded-For}. Caddy overwrites only the {@code X-Forwarded-*}
     * trio and passes this one through untouched, so until 2026-09-24 it bought a
     * fresh bucket per forged address <em>through the deployed proxy</em> --
     * reproduced against a local copy of the production stack, seven for seven.
     *
     * <p>Unlike {@code X-Forwarded-For}, nothing upstream ever writes this header
     * for us, so the application refuses it outright rather than leaving the
     * defence to the proxy alone.
     */
    @Test
    void aForgedRfc7239ForwardedHeaderDoesNotBuyAFreshBucket() {
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            assertThat(loginWith(Map.of("Forwarded", "for=203.0.113.%d".formatted(attempt))).getStatusCode())
                    .as("attempt %d of %d is within the limit", attempt, LIMIT)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(loginWith(Map.of("Forwarded", "for=203.0.113.99")).getStatusCode())
                .as("a forged Forwarded header must not change which bucket is counted")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * {@code X-Forwarded-Prefix} rewrites {@code getRequestURI()}, so a limiter
     * that asks "does the URI start with /api/v1/auth/" was not bypassed so much
     * as never consulted: {@code /x/api/v1/auth/login} does not start with it, yet
     * still routes to login, because routing strips the (forged) context path.
     * One constant header, no variation needed.
     */
    @Test
    void aForgedForwardedPrefixCannotSkipTheLimiter() {
        drainTheBucket();

        assertThat(loginWith(Map.of("X-Forwarded-Prefix", "/x")).getStatusCode())
                .as("a forged prefix must not take the request outside the limiter")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * No header at all: {@code getRequestURI()} is the raw, still-encoded path,
     * while routing and Spring Security both match decoded segments. So
     * {@code /api/v1/%61uth/login} reached login with the limiter skipped.
     * The firewall rejects encoded {@code /}, {@code .} and {@code %}, but not an
     * encoded letter. A proxy cannot fix this one; only matching the path the
     * way routing does can.
     */
    @Test
    void aPercentEncodedPathCannotSkipTheLimiter() {
        drainTheBucket();

        // A URI, not a String: RestTemplate would otherwise re-encode the % sign.
        URI encoded = URI.create(rest.getRootUri() + "/api/v1/%61uth/login");
        ResponseEntity<String> response = rest.postForEntity(encoded,
                new HttpEntity<>(Map.of("email", "nobody@example.com", "password", "wrong-password-here"),
                        jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode())
                .as("an encoded letter in the path must not take the request outside the limiter")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Boot registers its own {@code ForwardedHeaderFilter} unless it sees one
     * already. If that back-off ever failed, two would run at the same
     * {@code HIGHEST_PRECEDENCE} in an unspecified order -- and the unfiltered one
     * running first would quietly reopen both header bypasses above.
     */
    @Test
    void exactlyOneForwardedHeaderFilterIsRegistered_andItIsTheAllowlist(
            @Autowired List<FilterRegistrationBean<?>> registrations) {
        List<Object> forwarded = registrations.stream()
                .<Object>map(FilterRegistrationBean::getFilter)
                .filter(ForwardedHeaderFilter.class::isInstance)
                .toList();

        assertThat(forwarded).singleElement().isInstanceOf(ForwardedHeaderConfig.ProxyWrittenOnly.class);
    }

    /** Registration is on the same surface and the same bucket as login. */
    @Test
    void appliesTheSameLimitToRegistration() {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            post("/api/v1/auth/register",
                    Map.of("email", "reg-%d@example.com".formatted(attempt),
                            "password", "correct-horse-battery-staple"));
        }

        assertThat(post("/api/v1/auth/register",
                Map.of("email", "reg-last@example.com", "password", "correct-horse-battery-staple"))
                .getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private ResponseEntity<String> login(String email) {
        return post("/api/v1/auth/login", Map.of("email", email, "password", "wrong-password-here"));
    }

    private ResponseEntity<String> login(String email, String forwardedFor) {
        return post("/api/v1/auth/login",
                Map.of("email", email, "password", "wrong-password-here"), forwardedFor);
    }

    private ResponseEntity<String> post(String path, Map<String, String> body) {
        return post(path, body, null);
    }

    private ResponseEntity<String> post(String path, Map<String, String> body, String forwardedFor) {
        HttpHeaders headers = jsonHeaders();
        if (forwardedFor != null) {
            headers.set("X-Forwarded-For", forwardedFor);
        }
        return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> loginWith(Map<String, String> extraHeaders) {
        HttpHeaders headers = jsonHeaders();
        extraHeaders.forEach(headers::set);
        return rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", "nobody@example.com", "password", "wrong-password-here"), headers),
                String.class);
    }

    /** Five plain attempts from this peer, asserting the bucket really is empty afterwards. */
    private void drainTheBucket() {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            login("nobody@example.com");
        }
        assertThat(login("nobody@example.com").getStatusCode())
                .as("precondition: the plain bucket is drained")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
