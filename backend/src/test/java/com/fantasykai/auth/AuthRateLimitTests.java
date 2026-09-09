package com.fantasykai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    private ResponseEntity<String> post(String path, Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }
}
