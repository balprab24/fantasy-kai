package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The §7 contract: shapes, pagination, and what happens when a request is wrong. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReadApiTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ParameterizedTypeReference<PageResponse<PlayerSummary>> PLAYERS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ScoringProfileSummary>> PROFILES =
            new ParameterizedTypeReference<>() {};

    private static ApiFixture.Seeded seeded;

    @Autowired
    private TestRestTemplate rest;

    @BeforeAll
    static void seed(@Autowired JdbcTemplate jdbc) {
        seeded = ApiFixture.seed(jdbc);
    }

    @Test
    void listsPlayersInPagesRatherThanAllAtOnce() {
        PageResponse<PlayerSummary> first = players("?size=2&sort=id");

        assertThat(first.content()).hasSize(2);
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.total()).isEqualTo(6);
        assertThat(first.totalPages()).isEqualTo(3);

        PageResponse<PlayerSummary> second = players("?size=2&sort=id&page=1");
        assertThat(second.content()).doesNotContainAnyElementsOf(first.content());
    }

    @Test
    void filtersPlayersByPositionAndTeam() {
        assertThat(players("?position=QB").content())
                .extracting(PlayerSummary::position).containsOnly("QB");
        assertThat(players("?team=BAL").content())
                .extracting(PlayerSummary::team).containsOnly("BAL");
    }

    /** {@code ?season=} means "played that season", not "exists". */
    @Test
    void filtersPlayersToThoseWithAStatLineInTheSeason() {
        assertThat(players("?season=2025").total()).isEqualTo(5);
        assertThat(players("?season=2024").total()).isZero();
    }

    /**
     * 832 names in the real players table are shared. Both Josh Allens come back,
     * and the id is what tells them apart.
     */
    @Test
    void returnsBothPlayersWhoShareAName() {
        List<PlayerSummary> allens = players("?size=200").content().stream()
                .filter(player -> player.name().equals("Josh Allen"))
                .toList();

        assertThat(allens).hasSize(2)
                .extracting(PlayerSummary::position).containsExactlyInAnyOrder("QB", "C");
        assertThat(allens).extracting(PlayerSummary::id)
                .containsExactlyInAnyOrder(seeded.allen(), seeded.allenCenter());
    }

    @Test
    void servesTheFourSeededPresets() {
        List<ScoringProfileSummary> presets = rest
                .exchange("/api/v1/scoring-profiles", HttpMethod.GET, null, PROFILES).getBody();

        assertThat(presets).extracting(ScoringProfileSummary::name)
                .contains("Standard", "Half PPR", "Full PPR", "TE Premium");
    }

    @Test
    void carriesTheMatchupAndSnapShareOnAGameLogWeek() {
        GamelogResponse log = rest.getForObject(
                "/api/v1/players/%d/gamelog?season=2025&profileId=1".formatted(seeded.allen()),
                GamelogResponse.class);

        GamelogWeek week = log.weeks().get(0);
        assertThat(week.week()).isEqualTo(1);
        assertThat(week.opponent()).isEqualTo("BAL");
        assertThat(week.seasonType()).isEqualTo("REG");
        assertThat(week.snapPct()).isEqualTo(100.0);
        // Raw stats under their ruleset names, which are also the column names.
        assertThat(week.stats()).containsEntry("pass_yd", 394.0).containsEntry("rush_td", 2.0);
    }

    @Test
    void returns404AsProblemJsonForAnUnknownPlayer() {
        ResponseEntity<String> response =
                rest.getForEntity("/api/v1/players/99999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("no player with id 99999999");
    }

    /**
     * Before this phase there was no exception advice at all, so an unknown
     * profile id surfaced as a 500. It is a 404: the request was well-formed and
     * the thing it named does not exist.
     */
    @Test
    void returns404AsProblemJsonForAnUnknownScoringProfile() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/rankings?profileId=999999&season=2025", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("no scoring profile with id 999999");
    }

    @Test
    void requiresAProfileToRankAgainst() {
        assertThat(rest.getForEntity("/api/v1/rankings?season=2025", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * 2026 has a loaded schedule and no published stat lines, so the default
     * season is legitimately empty. Empty is a 200 with no rows, not a failure --
     * and this is the case that would silently invalidate a k6 baseline aimed at
     * the default.
     */
    @Test
    void returnsAnEmptyPageRatherThanFailingForASeasonWithNoStats() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/rankings?profileId=1&season=2026", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"content\":[]").contains("\"total\":0");
    }

    private PageResponse<PlayerSummary> players(String query) {
        return rest.exchange("/api/v1/players" + query, HttpMethod.GET, null, PLAYERS).getBody();
    }
}
