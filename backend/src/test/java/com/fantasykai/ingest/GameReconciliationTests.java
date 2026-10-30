package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fantasykai.projections.ProjectionDatabase;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.projections.TargetWeek;
import com.fantasykai.projections.TargetWeekResolver;
import com.fantasykai.query.PlayerQueryRepository;
import com.fantasykai.query.ScorableRow;
import com.fantasykai.scoring.StatKey;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The schedule as nflverse publishes it now, replayed from what it actually published.
 *
 * <p>Each fixture in {@code nflverse/schedule-history/} is the real {@code data/games.csv} of
 * nflverse/nfldata -- the file the schedules release is built from -- at one commit, header
 * verbatim, cut down to the games that moved plus an untouched control:
 *
 * <pre>
 * 2020-09-25.csv  566ef14979e4768e402c1c4884ba660148ebe5e2  2020_04_PIT_TEN listed (Sunday
 *                                                           Oct 4); NE-KC Sunday Oct 4
 * 2020-10-02.csv  0ac58c22581b7a9e6d61df2b1e1837e98ef561e8  PIT-TEN gone entirely: postponed,
 *                                                           no new date yet
 * 2020-10-06.csv  2af6313715bcb62e565eb2aeca6237db2cc640de  PIT-TEN back as 2020_07_PIT_TEN;
 *                                                           NE-KC the same id, now Monday,
 *                                                           played; 2020_07_PIT_BAL gone,
 *                                                           2020_08_PIT_BAL added
 * 2020-10-14.csv  32c6a398b55c578286ebcb1353fdbf8d8d76a4ea  2020_05_DEN_NE gone,
 *                                                           2020_06_DEN_NE added
 * 2023-01-05.csv  038f374658071dcda6c0e0128ed4e982e7f2698d  2022_17_BUF_CIN listed, kicked off
 *                                                           Jan 2, no score
 * 2023-01-06.csv  173413e9d7dc53aceb9ec00ba2dc40f1b87df618  BUF-CIN gone: declared no contest
 * </pre>
 *
 * <p>{@code stats_player_week_2020.csv} is three rows of today's 2020 stats file, for the
 * double-count tests below.
 *
 * <p>One more, {@code 2020-09-25-unreadable-row.csv}, is derived rather than recorded: the first
 * file with a single cell blanked, to prove an unusable row is not read as a dropped game.
 *
 * <p>No status column anywhere: a move across weeks and a cancellation are both a deleted row,
 * and a move within a week is the same id with a new date. Plain JUnit on the shared test
 * database -- no Spring context.
 */
class GameReconciliationTests {

    private final ProjectionDatabase db = ProjectionDatabase.get();

    /** The fixture served as games.csv; the client runs the real header check on it. */
    private String schedule;

    private final NflverseClient client = new NflverseClient(properties()) {
        @Override
        public <T> List<T> read(String release, String asset, Set<String> required,
                Function<CSVRecord, T> mapper) {
            // The schedule fixture in play, or a stats file served under its own name.
            String fixture = asset.startsWith("stats_player_week_") ? asset : schedule;
            try (var reader = new StringReader(new String(new ClassPathResource(
                    "nflverse/schedule-history/" + fixture).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8));
                    CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                            .get().parse(reader)) {
                verifyHeader(URI.create("fixture:" + schedule), parser.getHeaderMap().keySet(), required);
                List<T> mapped = new ArrayList<>();
                for (CSVRecord record : parser) {
                    T value = mapper.apply(record);
                    if (value != null) {
                        mapped.add(value);
                    }
                }
                return mapped;
            } catch (java.io.IOException e) {
                throw new IngestException("fixture " + schedule, e);
            }
        }
    };

    private final GameIngestor ingestor = new GameIngestor(client, ProjectionDatabase.get().jdbc);
    private final StatIngestor stats = new StatIngestor(client, ProjectionDatabase.get().jdbc);

    @BeforeEach
    void teams() {
        db.reset();
        for (String abbr : List.of("ARI", "BAL", "BUF", "CAR", "CHI", "CIN", "DEN", "DET", "IND", "KC",
                "NE", "PIT", "TEN")) {
            db.team(abbr);
        }
    }

    /** Postponed with no date yet: the row is simply gone, so the stored game is delisted. */
    @Test
    void aGameTheScheduleDropsIsDelistedNotDeleted() {
        ingest("2020-09-25.csv", 2020);
        ingest("2020-10-02.csv", 2020);

        assertThat(delisted("2020_04_PIT_TEN")).as("postponed: off the schedule").isTrue();
        assertThat(delisted("2020_04_NE_KC")).isFalse();
        assertThat(delisted("2020_04_IND_CHI")).as("the control game").isFalse();
        assertThat(count("SELECT count(*) FROM games")).as("a flag, never a delete").isEqualTo(5);
    }

    /** Moved across weeks: a new id in the new week, the old one stays off the schedule. */
    @Test
    void aGameMovedToAnotherWeekFollowsItsNewIdAndTheOldOneStaysDelisted() {
        ingest("2020-09-25.csv", 2020);
        ingest("2020-10-02.csv", 2020);
        ingest("2020-10-06.csv", 2020);
        ingest("2020-10-14.csv", 2020);

        assertThat(delisted("2020_04_PIT_TEN")).isTrue();
        assertThat(delisted("2020_07_PIT_TEN")).isFalse();
        assertThat(week("2020_07_PIT_TEN")).isEqualTo(7);
        assertThat(delisted("2020_07_PIT_BAL")).isTrue();
        assertThat(delisted("2020_08_PIT_BAL")).isFalse();
        assertThat(delisted("2020_05_DEN_NE")).isTrue();
        assertThat(delisted("2020_06_DEN_NE")).isFalse();
    }

    /** Moved within its week: the same id, updated in place -- never delisted. */
    @Test
    void aGameMovedWithinItsWeekKeepsItsIdAndTakesTheNewKickoff() {
        ingest("2020-09-25.csv", 2020);
        assertThat(kickoff("2020_04_NE_KC")).isEqualTo(Instant.parse("2020-10-04T20:25:00Z"));

        ingest("2020-10-06.csv", 2020);

        assertThat(delisted("2020_04_NE_KC")).isFalse();
        assertThat(kickoff("2020_04_NE_KC")).as("Monday 7:05 PM Eastern").isEqualTo(Instant.parse("2020-10-05T23:05:00Z"));
    }

    /** Cancelled after kickoff: listed with no score for three days, then gone. */
    @Test
    void aCancelledGameIsDelistedWhenTheScheduleDropsIt() {
        ingest("2023-01-05.csv", 2022);
        assertThat(delisted("2022_17_BUF_CIN")).as("still listed on Jan 5").isFalse();

        ingest("2023-01-06.csv", 2022);

        assertThat(delisted("2022_17_BUF_CIN")).isTrue();
        assertThat(delisted("2022_17_CHI_DET")).isFalse();
    }

    /** The source is the authority both ways: a game listed again is on the schedule again. */
    @Test
    void aGameListedAgainIsNoLongerDelisted() {
        ingest("2023-01-05.csv", 2022);
        ingest("2023-01-06.csv", 2022);
        ingest("2023-01-05.csv", 2022);

        assertThat(delisted("2022_17_BUF_CIN")).isFalse();
    }

    /** Off the schedule means out of the projection week: no waiting, no 48-hour rule. */
    @Test
    void aGameOffTheScheduleIsNeverATargetGame() {
        ingest("2020-09-25.csv", 2020);
        ingest("2020-10-02.csv", 2020);

        TargetWeek week4 = new TargetWeekResolver(db.jdbc)
                .resolve(2020, Instant.parse("2020-10-03T10:00:00Z")).orElseThrow();

        assertThat(week4.week()).isEqualTo(new SeasonWeek(2020, 4));
        assertThat(week4.games()).extracting(TargetWeek.Game::id)
                .containsExactlyInAnyOrder(id("2020_04_NE_KC"), id("2020_04_IND_CHI"))
                .doesNotContain(id("2020_04_PIT_TEN"));
    }

    /**
     * A row this run cannot use is still a game the source lists. 2020-09-25-unreadable-row.csv
     * is the real 2020-09-25 file with one cell blanked -- NE-KC's home_team -- the only fixture
     * here that is not a verbatim nfldata version. Reading "could not parse it" as "it is gone"
     * would delist a game that is on the schedule.
     */
    @Test
    void aRowThatCannotBeReadIsNotAGameTheSourceDropped() {
        ingest("2020-09-25.csv", 2020);

        ingest("2020-09-25-unreadable-row.csv", 2020);

        assertThat(delisted("2020_04_NE_KC")).isFalse();
        assertThat(count("SELECT count(*) FROM games WHERE delisted_at IS NOT NULL")).isZero();
    }

    /** A file with no rows for a season is no evidence about that season: nothing is delisted. */
    @Test
    void aSeasonTheFileDoesNotCoverIsLeftAlone() {
        db.game(2022, 17, team("CIN"), team("BUF"), Instant.parse("2023-01-03T01:30:00Z"));

        schedule = "2020-09-25.csv"; // no 2022 rows at all
        ingestor.ingest(List.of(2020, 2022));

        assertThat(count("SELECT count(*) FROM games WHERE season = 2022 AND delisted_at IS NULL")).isOne();
    }

    /**
     * A truncated file must not rewrite the schedule. More than a week's slate leaving one season
     * at once fails the run and delists nothing.
     */
    @Test
    void aFileThatDropsMoreThanAWeeksSlateFailsAndDelistsNothing() {
        ingest("2020-09-25.csv", 2020);
        for (int week = 1; week <= GameIngestor.MAX_DELISTED_PER_SEASON; week++) {
            db.game(2020, week, team("ARI"), team("CAR"), Instant.parse("2020-09-13T17:00:00Z"));
        }

        assertThatThrownBy(() -> ingest("2020-10-02.csv", 2020))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("17 stored 2020 games")
                .hasMessageContaining("nothing was delisted");
        assertThat(count("SELECT count(*) FROM games WHERE delisted_at IS NOT NULL")).isZero();
    }

    /** Exactly at the cap is still a schedule change. */
    @Test
    void aWeeksSlateLeavingAtOnceIsStillAllowed() {
        ingest("2020-09-25.csv", 2020);
        for (int week = 1; week < GameIngestor.MAX_DELISTED_PER_SEASON; week++) {
            db.game(2020, week, team("ARI"), team("CAR"), Instant.parse("2020-09-13T17:00:00Z"));
        }

        ingest("2020-10-02.csv", 2020);

        assertThat(count("SELECT count(*) FROM games WHERE delisted_at IS NOT NULL"))
                .isEqualTo(GameIngestor.MAX_DELISTED_PER_SEASON);
    }

    // --- stats: could a moved or cancelled game be counted twice? -----------------------

    /**
     * The double count this codebase was suspected of, replayed -- and it does not happen.
     *
     * <p>A stat row's identity is (player, game), the game resolved from the stats file's own
     * {@code game_id}, and {@code StatIngestor} only ever upserts. So the code alone would keep
     * one performance twice if the stats file ever carried it under two ids. The source never
     * has: nflverse builds player stats from the plays of a game as it was played, under its id
     * at that time, and 2020's PIT-TEN was moved before kickoff. Measured 2026-10-12 over every
     * 2020-26 stats file: 0 rows under an id the schedule no longer lists, 0 player-weeks with
     * two regular-season rows, and 69 rows for 2020_07_PIT_TEN against 0 for 2020_04_PIT_TEN.
     *
     * <p>stats_player_week_2020.csv here is three real rows from that file: Henry and Diontae
     * Johnson in PIT-TEN as played in week 7, and Mahomes in NE-KC, the game moved within its
     * week.
     */
    @Test
    void aGameMovedToAnotherWeekBeforeItWasPlayedIsCountedOnceUnderItsNewId() {
        ingest("2020-09-25.csv", 2020);   // 2020_04_PIT_TEN on the schedule
        ingest("2020-10-02.csv", 2020);   // postponed: gone
        ingest("2020-10-06.csv", 2020);   // back as 2020_07_PIT_TEN
        stats.ingest(2020);               // the stats, published once it was played

        List<ScorableRow> henry = rankingRows(2020, "Derrick Henry");
        assertThat(henry).singleElement().satisfies(row -> {
            assertThat(row.week()).isEqualTo(7);
            assertThat(row.line().values()[StatKey.RUSH_YD.index()]).isEqualTo(75);
        });
        assertThat(rankingRows(2020, "Diontae Johnson")).hasSize(1);
        assertThat(count("SELECT count(*) FROM player_game_stats WHERE game_id = " + id("2020_04_PIT_TEN")))
                .as("the delisted id never had a stat").isZero();
        assertThat(new IntegrityChecks(db.jdbc).statsOnDelistedGames()).isZero();
    }

    /** Moved within its week: one id throughout, so one row, whatever the schedule did. */
    @Test
    void aGameMovedWithinItsWeekIsCountedOnce() {
        ingest("2020-09-25.csv", 2020);
        ingest("2020-10-06.csv", 2020);   // NE-KC: Sunday became Monday, same id
        stats.ingest(2020);
        stats.ingest(2020);               // and a second pull upserts, never adds

        assertThat(rankingRows(2020, "Patrick Mahomes")).singleElement()
                .satisfies(row -> assertThat(row.week()).isEqualTo(4));
    }

    /**
     * Choice B, pinned as it is rather than as it might be: rankings do not consult
     * {@code delisted_at}. If stats ever did land on a game nflverse later drops -- a game played
     * in part and then abandoned, as BUF-CIN was, if its partial stats had ever been published
     * (today's 2022 files carry none, and no snapshot from January 2023 survives) -- the ranking
     * would still count them once, and the after-ingest check is what says so. Change the read
     * path and this test, and the docs that explain why, together.
     */
    @Test
    void rankingsStillCountStatsOnADelistedGameAndTheIntegrityCheckFlagsThem() {
        ingest("2023-01-05.csv", 2022);
        long player = db.player("Partial Game Receiver", "WR", team("CIN"));
        db.played(player, id("2022_17_BUF_CIN"), team("CIN"));
        ingest("2023-01-06.csv", 2022);

        assertThat(delisted("2022_17_BUF_CIN")).isTrue();
        assertThat(rankingRows(2022, "Partial Game Receiver")).hasSize(1);
        assertThat(new IntegrityChecks(db.jdbc).statsOnDelistedGames()).isOne();
    }

    /** Stats on a game the schedule dropped mean a played game was re-identified: say so. */
    @Test
    void integrityChecksCountStatsOnADelistedGame() {
        ingest("2023-01-05.csv", 2022);
        long player = db.player("Receiver", "WR", team("CIN"));
        db.played(player, id("2022_17_BUF_CIN"), team("CIN"));
        IntegrityChecks checks = new IntegrityChecks(db.jdbc);
        assertThat(checks.statsOnDelistedGames()).isZero();

        ingest("2023-01-06.csv", 2022);

        assertThat(checks.statsOnDelistedGames()).isOne();
    }

    private void ingest(String fixture, int season) {
        schedule = fixture;
        ingestor.ingest(List.of(season));
    }

    /** What the rankings read for one player, through the query the board uses. */
    private List<ScorableRow> rankingRows(int season, String name) {
        return new PlayerQueryRepository(db.jdbc)
                .findScorableRows(season, List.of("QB", "RB", "WR", "TE"), null).stream()
                .filter(row -> row.name().equals(name))
                .toList();
    }

    private boolean delisted(String gameId) {
        return db.jdbc.queryForObject("SELECT delisted_at IS NOT NULL FROM games WHERE nflverse_game_id = ?",
                Boolean.class, gameId);
    }

    private long id(String gameId) {
        return db.jdbc.queryForObject("SELECT id FROM games WHERE nflverse_game_id = ?", Long.class, gameId);
    }

    private int week(String gameId) {
        return db.jdbc.queryForObject("SELECT week FROM games WHERE nflverse_game_id = ?", Integer.class, gameId);
    }

    private Instant kickoff(String gameId) {
        return db.jdbc.queryForObject("SELECT kickoff_at FROM games WHERE nflverse_game_id = ?",
                OffsetDateTime.class, gameId).toInstant();
    }

    private int team(String abbr) {
        return db.jdbc.queryForObject("SELECT id FROM teams WHERE abbr = ?", Integer.class, abbr);
    }

    private long count(String sql) {
        return db.jdbc.queryForObject(sql, Long.class);
    }

    private static IngestProperties properties() {
        return new IngestProperties("https://example.invalid", "https://example.invalid", 2020,
                "0 0 6 * * *", false, false, false);
    }
}
