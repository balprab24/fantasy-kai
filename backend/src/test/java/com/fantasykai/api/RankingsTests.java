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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Scoring, all the way through HTTP.
 *
 * <p>The scoring engine is already covered by its own unit tests; what these
 * assert is that the API does not undo any of it -- that the position reaches
 * the override, that the postseason is excluded, that a bonus is paid per game,
 * and that rounding happens once at this boundary and nowhere earlier.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingsTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ParameterizedTypeReference<PageResponse<RankingRow>> RANKING =
            new ParameterizedTypeReference<>() {};

    private static ApiFixture.Seeded seeded;

    @Autowired
    private TestRestTemplate rest;

    @BeforeAll
    static void seed(@Autowired JdbcTemplate jdbc) {
        seeded = ApiFixture.seed(jdbc);
    }

    /**
     * The line §6 hand-computes. Allen caught nothing, so the four presets --
     * which differ only in what a reception is worth -- must agree exactly.
     */
    @Test
    void scoresTheRealWeekOneLineAt3876UnderEveryPreset() {
        for (long profile : List.of(ApiFixture.Seeded.STANDARD, ApiFixture.Seeded.HALF_PPR,
                ApiFixture.Seeded.FULL_PPR, ApiFixture.Seeded.TE_PREMIUM)) {

            GamelogResponse log = gamelog(seeded.allen(), profile);

            assertThat(log.weeks().get(0).points())
                    .as("profile %d on Josh Allen's 2025 week 1", profile)
                    .isEqualTo(38.76);
        }
    }

    /**
     * A threshold bonus belongs to a game. Two 100-yard games and one 50-yard
     * game is 25 points of rushing plus <em>two</em> bonuses, not the one that
     * scoring a 250-yard season total would pay.
     */
    @Test
    void paysAThresholdBonusOncePerQualifyingGameNotOncePerSeason() {
        RankingRow century = onlyRow(rank(seeded.centuryProfile(), "RB", "season"));

        assertThat(century.gamesPlayed()).isEqualTo(3);
        // 10 + 3, 10 + 3, 5 + 0
        assertThat(century.points()).isEqualTo(31.0);
        // What pre-aggregating the season and scoring it once would have given.
        assertThat(century.points()).isNotEqualTo(28.0);
    }

    /**
     * 3.3 + 3.333 + 3.333 is 9.966. Rounded once it is 9.97; rounded weekly and
     * then summed it is 9.96. The invariant is that the API reports the first.
     */
    @Test
    void roundsASeasonTotalOnceRatherThanSummingRoundedWeeks() {
        // Two quarterbacks are seeded; this assertion is about one of them.
        RankingRow thirds = rowFor(rank(seeded.thirdsProfile(), "QB", "season"), seeded.thirds());

        assertThat(thirds.points()).isEqualTo(9.97);
        assertThat(thirds.points()).isNotEqualTo(9.96);

        // And the game log agrees with the ranking rather than with itself.
        GamelogResponse log = gamelog(seeded.thirds(), seeded.thirdsProfile());
        double sumOfRoundedWeeks = log.weeks().stream().mapToDouble(GamelogWeek::points).sum();
        assertThat(log.totalPoints()).isEqualTo(9.97);
        assertThat(sumOfRoundedWeeks).isEqualTo(9.96);
    }

    /**
     * Fantasy leagues do not score the postseason, and the data runs to week 22.
     * Allen's ranking is weeks 1 and 2; his game log still shows the playoff week,
     * because that is a record of what he did rather than a ranking.
     */
    @Test
    void excludesThePostseasonFromRankingsButNotFromTheGameLog() {
        RankingRow allen = rowFor(rank(ApiFixture.Seeded.STANDARD, "QB", "season"), seeded.allen());

        assertThat(allen.gamesPlayed()).isEqualTo(2);
        assertThat(allen.points()).isEqualTo(51.76);

        GamelogResponse log = gamelog(seeded.allen(), ApiFixture.Seeded.STANDARD);
        assertThat(log.gamesPlayed()).isEqualTo(3);
        assertThat(log.weeks()).extracting(GamelogWeek::seasonType).contains("POST");
        assertThat(log.totalPoints()).isEqualTo(85.76);
    }

    /** A TE-premium override applies to the tight end and to nobody else. */
    @Test
    void appliesAPositionOverrideOnlyToThatPosition() {
        // Identical 8-catch, 76-yard lines; only the position differs.
        double receiverFullPpr = weekOne(seeded.receiver(), ApiFixture.Seeded.FULL_PPR);
        double tightEndFullPpr = weekOne(seeded.tightEnd(), ApiFixture.Seeded.FULL_PPR);
        assertThat(receiverFullPpr).isEqualTo(15.6).isEqualTo(tightEndFullPpr);

        double receiverPremium = weekOne(seeded.receiver(), ApiFixture.Seeded.TE_PREMIUM);
        double tightEndPremium = weekOne(seeded.tightEnd(), ApiFixture.Seeded.TE_PREMIUM);
        assertThat(receiverPremium).isEqualTo(15.6);
        assertThat(tightEndPremium).isEqualTo(19.6);
    }

    /** §6's four presets separating on one line: 7.6 / 11.6 / 15.6 / 19.6. */
    @Test
    void separatesTheFourPresetsOnASingleTightEndLine() {
        assertThat(weekOne(seeded.tightEnd(), ApiFixture.Seeded.STANDARD)).isEqualTo(7.6);
        assertThat(weekOne(seeded.tightEnd(), ApiFixture.Seeded.HALF_PPR)).isEqualTo(11.6);
        assertThat(weekOne(seeded.tightEnd(), ApiFixture.Seeded.FULL_PPR)).isEqualTo(15.6);
        assertThat(weekOne(seeded.tightEnd(), ApiFixture.Seeded.TE_PREMIUM)).isEqualTo(19.6);
    }

    /** {@code per_game} orders on the average; the totals alone would invert it. */
    @Test
    void perGameRanksOnTheAverageRatherThanTheTotal() {
        RankingRow receiver = rowFor(
                rank(ApiFixture.Seeded.FULL_PPR, "WR", "per_game"), seeded.receiver());

        assertThat(receiver.gamesPlayed()).isEqualTo(3);
        assertThat(receiver.points()).isEqualTo(35.6);
        assertThat(receiver.pointsPerGame()).isEqualTo(11.87);
    }

    /**
     * The window counts back from the last week with data, not from week 18 --
     * mid-season the last four played weeks are what a waiver decision is about.
     */
    @Test
    void last4WindowsOnTheFinalFourRegularSeasonWeeksThatHaveData() {
        PageResponse<RankingRow> last4 = rank(ApiFixture.Seeded.FULL_PPR, "WR", "last4");
        RankingRow receiver = rowFor(last4, seeded.receiver());

        // Weeks 5 and 6 fall inside; week 1 does not.
        assertThat(receiver.gamesPlayed()).isEqualTo(2);
        assertThat(receiver.points()).isEqualTo(20.0);

        // Allen played only weeks 1-2, so he is outside the window entirely.
        assertThat(rank(ApiFixture.Seeded.STANDARD, "QB", "last4").content())
                .extracting(RankingRow::playerId)
                .doesNotContain(seeded.allen());
    }

    /** Rank is a position in the whole ranking, not an index into the page. */
    @Test
    void numbersRanksAcrossTheWholeRankingNotThePage() {
        PageResponse<RankingRow> secondPage = rest.exchange(
                "/api/v1/rankings?profileId=%d&season=2025&scope=season&page=1&size=1"
                        .formatted(ApiFixture.Seeded.FULL_PPR),
                HttpMethod.GET, null, RANKING).getBody();

        assertThat(secondPage.content()).hasSize(1);
        assertThat(secondPage.content().get(0).rank()).isEqualTo(2);
        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.total()).isGreaterThan(1);
    }

    private PageResponse<RankingRow> rank(long profileId, String position, String scope) {
        return rest.exchange(
                "/api/v1/rankings?profileId=%d&season=2025&position=%s&scope=%s&size=50"
                        .formatted(profileId, position, scope),
                HttpMethod.GET, null, RANKING).getBody();
    }

    private GamelogResponse gamelog(long playerId, long profileId) {
        return rest.getForObject("/api/v1/players/%d/gamelog?season=2025&profileId=%d"
                .formatted(playerId, profileId), GamelogResponse.class);
    }

    private double weekOne(long playerId, long profileId) {
        return gamelog(playerId, profileId).weeks().get(0).points();
    }

    private static RankingRow onlyRow(PageResponse<RankingRow> page) {
        assertThat(page.content()).hasSize(1);
        return page.content().get(0);
    }

    private static RankingRow rowFor(PageResponse<RankingRow> page, long playerId) {
        return page.content().stream()
                .filter(row -> row.playerId() == playerId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("player " + playerId + " missing from ranking"));
    }
}
