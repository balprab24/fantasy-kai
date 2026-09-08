package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * §8, proved by trying to violate it rather than by reading the code.
 *
 * <p>The dynamic sort and filter parameters on the read endpoints are the one
 * place this API is tempted to build SQL from a request, because {@code ORDER BY}
 * cannot take a bind parameter. Each of these sends something that would be
 * destructive if it reached the database, then checks the table is still there.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuerySafetyTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DROP = "full_name; DROP TABLE players--";
    private static final String TAUTOLOGY = "BUF' OR '1'='1";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    static void seed(@Autowired JdbcTemplate jdbc) {
        ApiFixture.seed(jdbc);
    }

    /** Not sanitized, not escaped -- refused, because it is not one of four names. */
    @Test
    void rejectsASortColumnThatIsNotOnTheWhitelist() {
        ResponseEntity<String> response = get("/api/v1/players", "sort", DROP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .contains("unknown sort")
                .contains("allowed: name, position, team, id");
        assertThat(playersTableStillExists()).isTrue();
    }

    @Test
    void acceptsEverySortNameOnTheWhitelistAndNothingElse() {
        for (String sort : new String[] {"name", "position", "team", "id"}) {
            assertThat(get("/api/v1/players", "sort", sort).getStatusCode())
                    .as("sort=%s", sort).isEqualTo(HttpStatus.OK);
        }
        assertThat(get("/api/v1/players", "sort", "full_name").getStatusCode())
                .as("the underlying column name is not the public name")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsAScopeThatIsNotOnTheWhitelist() {
        ResponseEntity<String> response = rest.getForEntity(
                UriComponentsBuilder.fromPath("/api/v1/rankings")
                        .queryParam("profileId", 1).queryParam("season", 2025)
                        .queryParam("scope", "season; DELETE FROM players")
                        .toUriString(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("unknown scope");
        assertThat(playersTableStillExists()).isTrue();
    }

    @Test
    void rejectsAPositionThatIsNotScorable() {
        ResponseEntity<String> response = rest.getForEntity(
                UriComponentsBuilder.fromPath("/api/v1/rankings")
                        .queryParam("profileId", 1).queryParam("season", 2025)
                        .queryParam("position", "K' OR 1=1--")
                        .toUriString(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // K is real, and deferred to v2 -- so the message names what v1 scores.
        assertThat(response.getBody()).contains("allowed: QB, RB, WR, TE");
    }

    /**
     * The team filter is a bound value rather than a whitelisted name, so the
     * tautology is not rejected -- it is looked up as a team abbreviation,
     * matches nothing, and returns an empty page. That is the correct outcome:
     * bound parameters make it data, not syntax.
     */
    @Test
    void treatsAnInjectedFilterValueAsAValueAndFindsNothing() {
        ResponseEntity<String> response = get("/api/v1/players", "team", TAUTOLOGY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"total\":0");
        assertThat(playersTableStillExists()).isTrue();
    }

    /** §7: never unbounded lists. */
    @Test
    void refusesAPageSizePastTheCap() {
        assertThat(get("/api/v1/players", "size", "5000").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/v1/players", "size", String.valueOf(PageResponse.MAX_SIZE))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/players", "size", "0").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/v1/players", "page", "-1").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> get(String path, String param, String value) {
        return rest.getForEntity(UriComponentsBuilder.fromPath(path)
                .queryParam(param, value).toUriString(), String.class);
    }

    private boolean playersTableStillExists() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass('public.players') IS NOT NULL", Boolean.class));
    }
}
