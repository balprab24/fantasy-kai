package com.fantasykai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The CORS allowlist arrives as one comma-separated environment variable and has
 * to land as several origins.
 *
 * <p>This looks like a test of Spring rather than of us, and it is deliberately
 * written anyway. {@code allowed-origins} was a hardcoded yml list carrying the
 * comment "the Vercel origin is added at deploy" -- and nothing added it, because
 * no mechanism existed. Phase 5d gave it one, and the failure mode of the new
 * shape is silent: a {@code List<String>} bound from a single string either
 * splits into the origins you meant, or collapses into one origin that is the
 * whole comma-joined blob and matches nothing. Both start the application. The
 * second one is a production site whose every browser call is CORS-blocked, with
 * a green test suite and a correct-looking config value.
 *
 * <p>So the assertion is the split, not the plumbing.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "fantasykai.ingest.scheduled-enabled=false",
    "ALLOWED_ORIGINS=https://fantasykai.example,https://www.fantasykai.example"
})
class CorsBindingTests {

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AuthProperties props;

    @Test
    void splitsOneCommaSeparatedEnvVarIntoSeveralOrigins() {
        assertThat(props.allowedOrigins())
                .containsExactly("https://fantasykai.example", "https://www.fantasykai.example");
    }

    /**
     * The blob-instead-of-a-list failure, named explicitly so a regression says
     * what broke rather than just failing a count.
     */
    @Test
    void doesNotCollapseIntoASingleCommaJoinedOrigin() {
        assertThat(props.allowedOrigins())
                .as("a single element containing a comma is the silent-failure shape")
                .noneMatch(origin -> origin.contains(","));
    }
}
