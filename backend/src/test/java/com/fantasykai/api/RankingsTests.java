package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fantasykai.query.Usage;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static ApiFixture.History history;

    @Autowired
    private TestRestTemplate rest;

    @BeforeAll
    static void seed(@Autowired JdbcTemplate jdbc) {
        seeded = ApiFixture.seed(jdbc);
        history = ApiFixture.seedHistory(jdbc, seeded);
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
        assertThat(log.weeks()).extracting(GamelogWeek::seasonType).contains("DIV");
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

    /**
     * The player workspace shows a season's points, per-game and games from the
     * career endpoint, right beside a rank the board derived -- so they must be the
     * board's own three numbers, under every ruleset. The two custom profiles are
     * the ones that can tell: Thirds lands off the hundredths (rounded once or per
     * week?) and Century pays a per-game bonus (scored per game, or on the summed
     * season?). The presets cannot show either.
     */
    @Test
    void aCareerSeasonIsTheSameThreeNumbersAsItsRankingRow() {
        for (long profile : everyProfile()) {
            for (int season : List.of(2025, 2023)) {
                for (RankingRow row : board(profile, season).content()) {
                    CareerSeason career = careerSeason(row.playerId(), profile, season);
                    String who = "player %d, %d, profile %d".formatted(row.playerId(), season, profile);
                    assertThat(career.points()).as(who).isEqualTo(row.points());
                    assertThat(career.pointsPerGame()).as(who).isEqualTo(row.pointsPerGame());
                    assertThat(career.gamesPlayed()).as(who).isEqualTo(row.gamesPlayed());
                }
            }
        }
        assertThat(careerSeason(seeded.thirds(), seeded.thirdsProfile(), 2025).points())
                .as("the rounded sum of the weeks, not the sum of the rounded weeks")
                .isEqualTo(9.97);
        assertThat(careerSeason(seeded.century(), seeded.centuryProfile(), 2025).points())
                .as("two 100-yard games are two bonuses")
                .isEqualTo(31.0);
    }

    /**
     * The claim is not "close to the board": it is the positional rank the board
     * derives, derived here exactly the way {@code lib/board.ts} does it -- walk
     * the all-positions ranking in order and count each position. 2023 has two
     * receivers level on points, so the id tiebreak is exercised.
     */
    @Test
    void aCareerRankIsThePositionalRankTheBoardDerives() {
        for (long profile : everyProfile()) {
            for (int season : List.of(2025, 2023)) {
                Map<String, Integer> seen = new HashMap<>();
                for (RankingRow row : board(profile, season).content()) {
                    int derived = seen.merge(row.position(), 1, Integer::sum);
                    assertThat(careerSeason(row.playerId(), profile, season).posRank())
                            .as("player %d, %d, profile %d", row.playerId(), season, profile)
                            .isEqualTo(derived);
                }
            }
        }
    }

    /**
     * Two tie rules. A season place is one per player, lower id first, because
     * it must equal the board's. A week has no board to match, so two players on
     * the same points share the place.
     */
    @Test
    void levelSeasonsSplitByIdButLevelWeeksShareAPlace() {
        CareerSeason first = careerSeason(seeded.receiver(), ApiFixture.Seeded.HALF_PPR, 2023);
        CareerSeason second = careerSeason(history.tiedReceiver(), ApiFixture.Seeded.HALF_PPR, 2023);

        assertThat(first.points()).isEqualTo(second.points()).isEqualTo(15.0);
        assertThat(first.posRank()).isEqualTo(1);
        assertThat(second.posRank()).isEqualTo(2);

        assertThat(first.weeks().get(0).posRank()).isEqualTo(1);
        assertThat(second.weeks().get(0).posRank()).isEqualTo(1);

        // Ranked by season points, not per game: the one-game receiver has the
        // best per-game figure and the lowest total, so he is third.
        CareerSeason oneGame =
                careerSeason(history.oneGameReceiver(), ApiFixture.Seeded.HALF_PPR, 2023);
        assertThat(oneGame.pointsPerGame()).isGreaterThan(first.pointsPerGame());
        assertThat(oneGame.posRank()).isEqualTo(3);

        // And a week that is not a tie: Allen's 38.76 beats Thirds' 4.0.
        CareerSeason thirds = careerSeason(seeded.thirds(), ApiFixture.Seeded.STANDARD, 2025);
        assertThat(thirds.weeks().get(0).posRank()).isEqualTo(2);
    }

    /** Regular season only, like a ranking; the log keeps the wild-card game. */
    @Test
    void aCareerLeavesOutThePlayoffsTheGameLogKeeps() {
        CareerSeason allen = careerSeason(seeded.allen(), ApiFixture.Seeded.STANDARD, 2023);
        assertThat(allen.gamesPlayed()).isEqualTo(2);
        assertThat(allen.weeks()).extracting(CareerWeek::week).containsExactly(1, 3);
        // 250/2/20/0 -> 20.0 and 300/3/10/1 -> 31.0.
        assertThat(allen.points()).isEqualTo(51.0);
        assertThat(allen.stats()).containsEntry("pass_yd", 550.0).containsEntry("rush_td", 1.0);

        GamelogResponse log = gamelog(seeded.allen(), ApiFixture.Seeded.STANDARD, 2023);
        assertThat(log.gamesPlayed()).isEqualTo(3);
        assertThat(log.weeks()).extracting(GamelogWeek::seasonType).containsExactly("REG", "REG", "WC");
    }

    /**
     * Newest first. A season with no regular-season line has no career row --
     * but it is in {@code seasonsPlayed}, so a page can still reach its playoff
     * games.
     */
    @Test
    void listsEverySeasonOnRecordNewestFirst() {
        CareerResponse career = career(seeded.allen(), ApiFixture.Seeded.FULL_PPR);
        assertThat(career.seasons()).extracting(CareerSeason::season).containsExactly(2025, 2023);
        assertThat(career.seasonsPlayed()).containsExactly(2025, 2023);
        assertThat(career(seeded.tightEnd(), ApiFixture.Seeded.FULL_PPR).seasons())
                .extracting(CareerSeason::season).containsExactly(2025);

        CareerResponse playoffOnly = career(history.tiedReceiver(), ApiFixture.Seeded.FULL_PPR);
        assertThat(playoffOnly.seasons()).extracting(CareerSeason::season).containsExactly(2023);
        assertThat(playoffOnly.seasonsPlayed()).containsExactly(2023, 2022);
    }

    /**
     * The receiver is traded to BAL before week 3. A week carries that week's
     * team, and {@code home} is read off the game rather than assumed.
     */
    @Test
    void aTradedPlayerCarriesBothTeamsAndEachGamesVenue() {
        assertThat(careerSeason(seeded.receiver(), ApiFixture.Seeded.HALF_PPR, 2023).teams())
                .containsExactly("BUF", "BAL");

        List<GamelogWeek> receiver =
                gamelog(seeded.receiver(), ApiFixture.Seeded.HALF_PPR, 2023).weeks();
        assertThat(receiver).extracting(GamelogWeek::team).containsExactly("BUF", "BAL");
        assertThat(receiver).extracting(GamelogWeek::opponent).containsExactly("BAL", "BUF");
        assertThat(receiver).extracting(GamelogWeek::home).containsExactly(true, true);

        List<GamelogWeek> allen = gamelog(seeded.allen(), ApiFixture.Seeded.HALF_PPR, 2023).weeks();
        assertThat(allen).extracting(GamelogWeek::home).containsExactly(true, false, true);
        assertThat(careerSeason(seeded.allen(), ApiFixture.Seeded.HALF_PPR, 2023).weeks())
                .extracting(CareerWeek::home).containsExactly(true, false);
    }

    /** Attempts, completions, carries and targets: shown, summed, never scored. */
    @Test
    void carriesVolumeBesideTheScorableLineWithoutScoringIt() {
        GamelogWeek week1 = gamelog(seeded.allen(), ApiFixture.Seeded.STANDARD, 2023).weeks().get(0);
        assertThat(week1.usage()).isEqualTo(new Usage(35, 24, 4, 0));
        assertThat(week1.stats()).doesNotContainKeys("pass_att", "pass_cmp", "rush_att", "targets");

        assertThat(careerSeason(seeded.allen(), ApiFixture.Seeded.STANDARD, 2023).usage())
                .isEqualTo(new Usage(75, 53, 7, 0));
        assertThat(careerSeason(seeded.receiver(), ApiFixture.Seeded.STANDARD, 2023).usage())
                .isEqualTo(new Usage(0, 0, 1, 16));
    }

    /**
     * A center has a stat line and a page, and v1 does not rank his position. He
     * gets his points and no rank -- not a 400 from the position whitelist.
     */
    @Test
    void aPositionV1DoesNotRankStillHasACareerWithoutRanks() {
        CareerResponse center = career(seeded.allenCenter(), ApiFixture.Seeded.FULL_PPR);

        assertThat(center.seasons()).hasSize(1);
        CareerSeason season = center.seasons().get(0);
        assertThat(season.points()).isEqualTo(1.4);
        assertThat(season.posRank()).isNull();
        assertThat(season.weeks()).extracting(CareerWeek::posRank).containsOnlyNulls();
    }

    /** Identity for display: an ESPN id only when well-formed, and a season's age. */
    @Test
    void carriesDisplayIdentityOnlyWhenItIsWellFormed() {
        PlayerDetail allen = rest.getForObject("/api/v1/players/" + seeded.allen(), PlayerDetail.class);
        assertThat(allen.espnId()).isEqualTo("3918298");
        assertThat(allen.birthDate()).isEqualTo(LocalDate.of(1996, 5, 21));
        assertThat(allen.teamName()).isEqualTo("Buffalo Bills");

        PlayerDetail receiver =
                rest.getForObject("/api/v1/players/" + seeded.receiver(), PlayerDetail.class);
        assertThat(receiver.espnId()).as("\"4262921.0\" is not an id").isNull();
        assertThat(receiver.birthDate()).isNull();

        PageResponse<RankingRow> board = board(ApiFixture.Seeded.FULL_PPR, 2025);
        assertThat(rowFor(board, seeded.allen()).espnId()).isEqualTo("3918298");
        assertThat(rowFor(board, seeded.receiver()).espnId()).isNull();
        assertThat(rowFor(board, seeded.tightEnd()).espnId()).isNull();

        // Born 21 May 1996: 29 on 1 September 2025, 27 on 1 September 2023.
        assertThat(career(seeded.allen(), ApiFixture.Seeded.FULL_PPR).seasons())
                .extracting(CareerSeason::age).containsExactly(29, 27);
        assertThat(careerSeason(seeded.receiver(), ApiFixture.Seeded.FULL_PPR, 2025).age()).isNull();
    }

    /** A page past the end is empty, and the identity lookup is not asked about nobody. */
    @Test
    void aPagePastTheEndIsEmptyRatherThanAnError() {
        PageResponse<RankingRow> past = rest.exchange(
                "/api/v1/rankings?profileId=1&season=2025&page=50&size=200",
                HttpMethod.GET, null, RANKING).getBody();
        assertThat(past.content()).isEmpty();
        assertThat(past.total()).isPositive();
    }

    @Test
    void aCareerFor404sLikeTheGameLog() {
        assertThat(rest.getForEntity("/api/v1/players/99999999/career?profileId=1", String.class)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(rest.getForEntity("/api/v1/players/%d/career?profileId=999999"
                .formatted(seeded.allen()), String.class).getStatusCode().value()).isEqualTo(404);
    }

    private List<Long> everyProfile() {
        return List.of(ApiFixture.Seeded.STANDARD, ApiFixture.Seeded.HALF_PPR,
                ApiFixture.Seeded.FULL_PPR, ApiFixture.Seeded.TE_PREMIUM,
                seeded.thirdsProfile(), seeded.centuryProfile());
    }

    private PageResponse<RankingRow> board(long profileId, int season) {
        return rest.exchange("/api/v1/rankings?profileId=%d&season=%d&scope=season&size=200"
                .formatted(profileId, season), HttpMethod.GET, null, RANKING).getBody();
    }

    private CareerResponse career(long playerId, long profileId) {
        return rest.getForObject("/api/v1/players/%d/career?profileId=%d"
                .formatted(playerId, profileId), CareerResponse.class);
    }

    private CareerSeason careerSeason(long playerId, long profileId, int season) {
        return career(playerId, profileId).seasons().stream()
                .filter(s -> s.season() == season)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no %d season for player %d"
                        .formatted(season, playerId)));
    }

    private GamelogResponse gamelog(long playerId, long profileId, int season) {
        return rest.getForObject("/api/v1/players/%d/gamelog?season=%d&profileId=%d"
                .formatted(playerId, season, profileId), GamelogResponse.class);
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
