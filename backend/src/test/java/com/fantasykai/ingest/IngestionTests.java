package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 1 acceptance test: a real nflverse row lands in Postgres with the right
 * numbers in the right columns.
 *
 * <p>Fixtures are genuine 2024 rows, not invented ones, so the mapping is tested
 * against the shapes the source actually ships -- including a stat line with no
 * player id, which must be skipped rather than break the load. No network: the
 * client is replaced with one reading the same CSVs off the classpath.
 *
 * <p>Phase 4 added the schedule half: the betting, result and weather columns
 * that GameIngestor used to download and discard. Those shapes are real too --
 * a dome with no temperature reading, an away favourite, a 2026 game that has a
 * line but no score, and one that has neither.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
    "fantasykai.ingest.first-season=2024",
    "fantasykai.ingest.scheduled-enabled=false"
})
class IngestionTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Ja'Marr Chase, 2024 week 10 at Baltimore: 17 targets, 11-264-3. */
    private static final String CHASE = "00-0036900";
    /** Aidan Hutchinson, 2024 week 2 vs Tampa Bay: 4.5 sacks. */
    private static final String HUTCHINSON = "00-0037236";
    /** Austin Seibert, 2024 week 2 vs the Giants: 7 field goals. */
    private static final String SEIBERT = "00-0035145";

    /**
     * Set to rename one header cell in every fixture served, then cleared. The
     * only mutable state in this class, and it exists because the failure it
     * proves -- upstream renames a column -- cannot be simulated any other way.
     */
    private static Map.Entry<String, String> renameInHeader;

    @TestConfiguration
    static class Fixtures {

        /**
         * Serves release assets from src/test/resources/nflverse instead of
         * GitHub -- and runs the same header check the real client does, so the
         * required-column contract is exercised here rather than only in
         * production.
         *
         * <p>{@link #renameInHeader} rewrites one header cell on the way past.
         * That is how a renamed upstream column is tested: by doing it.
         */
        @Bean
        @Primary
        NflverseClient fixtureClient(IngestProperties props) {
            return new NflverseClient(props) {
                @Override
                public <T> List<T> read(String release, String asset, Set<String> required,
                        Function<CSVRecord, T> mapper) {
                    try (var reader = new StringReader(fixtureText(asset));
                            CSVParser parser = CSVFormat.DEFAULT.builder()
                                    .setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
                        NflverseClient.verifyHeader(
                                URI.create("fixture:" + asset), parser.getHeaderMap().keySet(), required);
                        List<T> mapped = new ArrayList<>();
                        for (CSVRecord record : parser) {
                            T value = mapper.apply(record);
                            if (value != null) {
                                mapped.add(value);
                            }
                        }
                        return mapped;
                    } catch (IngestException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IngestException("fixture " + asset, e);
                    }
                }
            };
        }

        /** The fixture CSV, with {@link #renameInHeader} applied to the header row. */
        private static String fixtureText(String asset) throws java.io.IOException {
            String text = new String(new ClassPathResource("nflverse/" + asset)
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Map.Entry<String, String> rename = renameInHeader;
            if (rename == null) {
                return text;
            }
            int end = text.indexOf('\n');
            String header = text.substring(0, end).replaceAll(
                    "(^|,)" + rename.getKey() + "(,|\r?$)", "$1" + rename.getValue() + "$2");
            return header + text.substring(end);
        }

        /** Mid-2024, so backfill() resolves to exactly the fixture season. */
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2024-11-15T12:00:00Z"), ZoneId.of("America/New_York"));
        }
    }

    @MockitoBean
    private SleeperCrosswalk sleeper; // calls Sleeper's API; not this test's concern

    @Autowired
    private IngestService ingestService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GameIngestor games; // reached directly for the seasons backfill() does not cover

    @BeforeEach
    void runTheIngest() {
        renameInHeader = null;
        when(sleeper.ingest()).thenReturn(new IngestResult(SleeperCrosswalk.SOURCE, 0, 0, 0));
        jdbc.update("TRUNCATE ingest_runs");
        ingestService.backfill();
    }

    @Test
    void mapsAReceivingLineIntoTheRightColumns() {
        Map<String, Object> row = statLine(CHASE);

        // NB: the pgjdbc driver hands smallint back as Integer, not Short.
        assertThat(row).containsEntry("targets", 17)
                .containsEntry("rec", 11)
                .containsEntry("rec_yd", 264)
                .containsEntry("rec_td", 3)
                .containsEntry("season", 2024)
                .containsEntry("week", 10);
    }

    @Test
    void preservesFractionalSacks() {
        // The whole reason def_sacks is NUMERIC and not SMALLINT.
        assertThat((BigDecimal) statLine(HUTCHINSON).get("def_sacks"))
                .isEqualByComparingTo("4.5");
    }

    @Test
    void mapsKickingIncludingDistanceBuckets() {
        Map<String, Object> row = statLine(SEIBERT);
        assertThat(row).containsEntry("fg_made", 7);

        // Every made kick must land in exactly one distance bucket.
        int bucketed = List.of("fg_made_0_19", "fg_made_20_29", "fg_made_30_39",
                        "fg_made_40_49", "fg_made_50_59", "fg_made_60_plus").stream()
                .mapToInt(column -> ((Number) row.get(column)).intValue())
                .sum();
        assertThat(bucketed).isEqualTo(7);
    }

    @Test
    void convertsSnapShareFromFractionToPercent() {
        // The source ships 0.94; the column is a percentage.
        assertThat((BigDecimal) statLine(CHASE).get("snap_pct")).isEqualByComparingTo("94.00");
    }

    @Test
    void skipsStatLinesWithNoPlayerId() {
        // Four fixture rows; one has an empty player_id and cannot be referenced.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player_game_stats", Long.class))
                .isEqualTo(3L);
    }

    /**
     * The silent-failure mode, proved by causing it.
     *
     * <p>{@code CsvValues.shortValue} maps an absent column to 0 on purpose --
     * that is what "did not record this" means in a box score. The cost is that
     * a rename upstream is indistinguishable from a zero: every row parses, the
     * row counts match, {@code IntegrityChecks} only compares season and week,
     * and the run reports SUCCESS with every quarterback on zero passing yards.
     * Without the header check this test would pass while the data was wrong.
     */
    @Test
    void aRenamedUpstreamColumnFailsTheRunInsteadOfZeroingTheStat() {
        renameInHeader = Map.entry("receiving_yards", "rec_yards");

        assertThatThrownBy(() -> ingestService.backfill())
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("receiving_yards");

        Map<String, Object> failed = jdbc.queryForMap("""
                SELECT status, error FROM ingest_runs
                 WHERE source = ? AND status = 'FAILED' ORDER BY id DESC LIMIT 1
                """, StatIngestor.SOURCE);

        assertThat(failed).containsEntry("status", "FAILED");
        assertThat((String) failed.get("error"))
                .as("the run says which column went missing, not just that it failed")
                .contains("receiving_yards");
    }

    /**
     * What the previous test would have cost. Chase's 264 receiving yards are
     * the number that goes silently to 0 if {@code receiving_yards} is renamed
     * upstream and nothing checks the header -- so the column being renamed
     * there has to be one that actually carries data, or the test proves
     * nothing.
     */
    @Test
    void theRenamedColumnIsOneThatCarriesRealData() {
        Integer recYards = jdbc.queryForObject("""
                SELECT max(rec_yd) FROM player_game_stats
                """, Integer.class);

        assertThat(recYards).isEqualTo(264);
    }

    @Test
    void namesEveryMissingColumnRatherThanTheFirst() {
        assertThatThrownBy(() -> NflverseClient.verifyHeader(
                        URI.create("fixture:test.csv"),
                        Set.of("season", "week"),
                        Set.of("season", "week", "spread_line", "total_line")))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("spread_line")
                .hasMessageContaining("total_line")
                .hasMessageContaining("2 column(s)");
    }

    @Test
    void aHeaderCarryingEveryRequiredColumnPasses() {
        // Extra columns are fine -- stats_player_week ships 150 and we read 52.
        NflverseClient.verifyHeader(URI.create("fixture:test.csv"),
                Set.of("season", "week", "unused_extra"), Set.of("season", "week"));
    }

    /**
     * The required set is generated from the field list, not maintained beside
     * it -- including the three columns def_blocked_kicks sums and the ones the
     * row mapper resolves a stat line against.
     */
    @Test
    void theRequiredColumnSetIsDerivedFromTheFieldsThatReadThem() {
        assertThat(StatIngestor.REQUIRED_COLUMNS)
                .contains("passing_yards", "receiving_yards", "fumbles_lost_total")
                .contains("def_punt_blocks", "def_pat_blocks", "def_fg_blocks")
                .contains("player_id", "game_id", "team", "season", "week");

        assertThat(GameIngestor.REQUIRED_COLUMNS)
                .contains("spread_line", "total_line", "temp", "wind", "roof", "surface")
                .contains("game_id", "home_team", "away_team", "gameday");
    }

    @Test
    void neverStoresTheSourcesPrecomputedFantasyPoints() {
        List<String> columns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_name = 'player_game_stats'
                """, String.class);

        assertThat(columns).noneMatch(column -> column.contains("fantasy_point"));
    }

    @Test
    void recordsEveryStepInIngestRuns() {
        List<Map<String, Object>> runs = jdbc.queryForList(
                "SELECT source, status FROM ingest_runs ORDER BY id");

        assertThat(runs).isNotEmpty()
                .allSatisfy(run -> assertThat(run).containsEntry("status", "SUCCESS"))
                .extracting(run -> run.get("source"))
                .contains("nflverse.teams", "nflverse.players", "nflverse.schedules",
                        "nflverse.stats_player_week", "nflverse.snap_counts");
    }

    @Test
    void denormalizedSeasonAndWeekAgreeWithTheJoinedGame() {
        assertThat(jdbc.queryForObject(IntegrityChecks.SEASON_WEEK_DRIFT, Long.class)).isZero();
    }

    @Test
    void anIngestedRowScoresCorrectlyStraightOutOfTheDatabase() {
        // The loop the rest of the suite leaves open: nflverse CSV -> upsert ->
        // stat columns -> StatKey -> points. StatKey names are the column names,
        // so the SELECT is generated from the enum rather than written beside it
        // -- if the two ever drift this fails, instead of scoring a silent zero.
        String columns = Arrays.stream(StatKey.values()).map(StatKey::json)
                .collect(Collectors.joining(", "));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT " + columns + " FROM player_game_stats s"
                        + " JOIN players p ON p.id = s.player_id WHERE p.gsis_id = ?",
                CHASE);

        Map<StatKey, Number> stats = new EnumMap<>(StatKey.class);
        Arrays.stream(StatKey.values())
                .forEach(stat -> stats.put(stat, (Number) row.get(stat.json())));

        // Ja'Marr Chase, 2024 week 10: 11-264-3. Full PPR = 11 + 26.4 + 18.
        // nflverse's own fantasy_points_ppr for this line is 55.4. We never store
        // that column; this is the number computed without it.
        double points = ScoringEngine.score(
                StatLine.of("WR", stats), ResolvedRuleset.compile(fullPpr()));

        assertThat(ScoringEngine.roundForDisplay(points)).isEqualTo(55.4);
    }

    @Test
    void mapsTheScoreLineAndConditionsIntoTheRightColumns() {
        // The canary for a positional mistake: every Phase 4 column on one row,
        // against the values the source actually ships. BAL 20 at KC 27, Kansas
        // City favoured by 3. A spread that landed in total_line would still
        // type-check, which is why the upsert is generated from one ordered list.
        Map<String, Object> row = game("2024_01_BAL_KC");

        assertThat(row).containsEntry("home_score", 27)
                .containsEntry("away_score", 20)
                .containsEntry("home_moneyline", -148)
                .containsEntry("away_moneyline", 124)
                .containsEntry("roof", "outdoors")
                .containsEntry("surface", "grass")
                .containsEntry("temp", 67)
                .containsEntry("wind", 8);
        assertThat((BigDecimal) row.get("spread_line")).isEqualByComparingTo("3");
        assertThat((BigDecimal) row.get("total_line")).isEqualByComparingTo("46");
    }

    @Test
    void preservesAFractionalSpread() {
        // The whole reason spread_line and total_line are NUMERIC and not SMALLINT:
        // 3,321 of 7,388 spreads in the source carry a half point.
        Map<String, Object> row = game("2024_02_TB_DET");

        assertThat((BigDecimal) row.get("spread_line")).isEqualByComparingTo("7.5");
        assertThat((BigDecimal) row.get("total_line")).isEqualByComparingTo("51.5");
    }

    @Test
    void keepsTheSignOfAnAwayFavourite() {
        // A positive spread_line means the home team is favoured, so an away
        // favourite has to survive as a negative rather than as its magnitude.
        assertThat((BigDecimal) game("2024_05_BAL_CIN").get("spread_line"))
                .isEqualByComparingTo("-2.5");
    }

    @Test
    void aDomeGamesBlankTemperatureReadsBackNullNotZero() {
        // 0 degrees and 0 mph are both real readings -- wind is 0 in 29 rows since
        // 2020 -- so a blank field cannot collapse to zero the way a box score
        // does. This is the one thing CsvValues.shortValue would have got wrong.
        Map<String, Object> row = game("2024_02_TB_DET");

        assertThat(row).containsEntry("roof", "dome");
        assertThat(row.get("temp")).isNull();
        assertThat(row.get("wind")).isNull();
    }

    @Test
    @Transactional
    void anUnplayedGameCarriesItsLineButNoScore() {
        // 2026 is outside the window backfill() resolves to under the fixed clock,
        // so ask for it directly. Transactional so those rows do not outlive the
        // test that wanted them.
        games.ingest(List.of(2026));
        Map<String, Object> row = game("2026_01_TB_CIN");

        assertThat((BigDecimal) row.get("spread_line")).isEqualByComparingTo("3.5");
        assertThat((BigDecimal) row.get("total_line")).isEqualByComparingTo("50.5");
        assertThat(row.get("home_score")).isNull();
        assertThat(row.get("away_score")).isNull();
    }

    @Test
    @Transactional
    void aGameWithNoLineYetHasNullBettingColumns() {
        // Lines land roughly a week ahead of kickoff, so a week 11 game in
        // September has none. The row still exists; the betting columns are empty.
        games.ingest(List.of(2026));
        Map<String, Object> row = game("2026_11_TB_DET");

        assertThat(row).containsEntry("roof", "dome");
        assertThat(row.get("spread_line")).isNull();
        assertThat(row.get("total_line")).isNull();
        assertThat(row.get("home_moneyline")).isNull();
        assertThat(row.get("away_moneyline")).isNull();
    }

    @Test
    @Transactional
    void theUpsertRefreshesBettingColumnsOnARerun() {
        // Prove it by breaking it. Because the line arrives after the schedule
        // does, a column present in the INSERT list but missing from DO UPDATE SET
        // would stay empty forever and nothing would report it.
        jdbc.update("UPDATE games SET spread_line = NULL WHERE nflverse_game_id = ?",
                "2024_02_TB_DET");
        assertThat(game("2024_02_TB_DET").get("spread_line")).isNull();

        games.ingest(List.of(2024));

        assertThat((BigDecimal) game("2024_02_TB_DET").get("spread_line"))
                .isEqualByComparingTo("7.5");
    }

    @Test
    void neverStoresAResultItCanDerive() {
        // Checked against all 7,276 played games with zero exceptions:
        // total = home_score + away_score and result = home_score - away_score.
        // Storing either is the implied_team_total mistake -- a computed value a
        // corrected score would leave stale.
        List<String> columns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_name = 'games'
                """, String.class);

        assertThat(columns).doesNotContain("result", "total", "implied_team_total");
    }

    /** Full PPR, built here so this test does not depend on the V3 seed. */
    private static Ruleset fullPpr() {
        Map<StatKey, Double> base = new EnumMap<>(StatKey.class);
        base.put(StatKey.PASS_YD, 0.04);
        base.put(StatKey.PASS_TD, 4.0);
        base.put(StatKey.PASS_INT, -2.0);
        base.put(StatKey.RUSH_YD, 0.1);
        base.put(StatKey.RUSH_TD, 6.0);
        base.put(StatKey.REC, 1.0);
        base.put(StatKey.REC_YD, 0.1);
        base.put(StatKey.REC_TD, 6.0);
        base.put(StatKey.FUM_LOST, -2.0);
        base.put(StatKey.RET_TD, 6.0);
        return new Ruleset(1, base, Map.of(), List.of());
    }

    private Map<String, Object> statLine(String gsisId) {
        return jdbc.queryForMap("""
                SELECT s.* FROM player_game_stats s
                JOIN players p ON p.id = s.player_id
                WHERE p.gsis_id = ?
                """, gsisId);
    }

    private Map<String, Object> game(String nflverseGameId) {
        return jdbc.queryForMap(
                "SELECT * FROM games WHERE nflverse_game_id = ?", nflverseGameId);
    }
}
