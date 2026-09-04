package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    @TestConfiguration
    static class Fixtures {

        /** Serves release assets from src/test/resources/nflverse instead of GitHub. */
        @Bean
        @Primary
        NflverseClient fixtureClient(IngestProperties props) {
            return new NflverseClient(props) {
                @Override
                public <T> List<T> read(String release, String asset, Function<CSVRecord, T> mapper) {
                    try (var reader = new InputStreamReader(
                                    new ClassPathResource("nflverse/" + asset).getInputStream(),
                                    StandardCharsets.UTF_8);
                            CSVParser parser = CSVFormat.DEFAULT.builder()
                                    .setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
                        List<T> mapped = new ArrayList<>();
                        for (CSVRecord record : parser) {
                            T value = mapper.apply(record);
                            if (value != null) {
                                mapped.add(value);
                            }
                        }
                        return mapped;
                    } catch (Exception e) {
                        throw new IngestException("fixture " + asset, e);
                    }
                }
            };
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

    @BeforeEach
    void runTheIngest() {
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
}
