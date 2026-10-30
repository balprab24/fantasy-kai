package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fantasykai.projections.Availability;
import com.fantasykai.projections.AvailabilityService;
import com.fantasykai.projections.AvailabilityStatus;
import com.fantasykai.projections.ProjectionDatabase;
import com.fantasykai.projections.SeasonWeek;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The roster and injury feeds, from real rows to a classification.
 *
 * <p>{@code nflverse/availability/} holds real rows, verbatim, of nflverse's 2026 weekly roster
 * and injury report (read 2026-10-12, weeks 3-5) for players who between them cover every case,
 * plus 2024's injury report for the one player-week it listed twice:
 *
 * <pre>
 * Ja'Marr Chase      CIN  active; Questionable in week 5
 * Josh Palmer        BUF  active and Out in week 5                  -- the "active + Out" conflict
 * Trey Lance         LAC  injured reserve and Doubtful in week 5    -- reserve outranks a designation
 * Zay Flowers        BAL  Questionable every week
 * Eli Raridon        NE   Questionable in week 4, nothing in week 5 -- a designation must not carry
 * David Njoku        LAC  injured reserve, no injury row
 * Sterling Shepard   NYJ  active, then practice squad in week 5
 * Andy Dalton        PHI  active, then released in week 5
 * Odell Beckham Jr.  NYG  released in week 4, then MIN, game-day inactive, in week 5
 * J.J. McCarthy      MIN -> NYG in week 4: a trade
 * Al-Jay Henderson   a row with no gsis id
 * Maceo Beard        a player the master does not know
 * Cade Stover (2024) HOU  week 15 listed twice, Questionable then Out
 * </pre>
 *
 * <p>Plain JUnit on the shared test database; no Spring context.
 */
class AvailabilityIngestTests {

    private static final Instant MONDAY = Instant.parse("2026-10-12T10:00:00Z");
    private static final SeasonWeek WEEK_5 = new SeasonWeek(2026, 5);

    private final ProjectionDatabase db = ProjectionDatabase.get();

    /** A fixture's text, or a variant of it a test builds -- served under the asset's name. */
    private final Map<String, String> served = new HashMap<>();

    private final NflverseClient client = new NflverseClient(new IngestProperties("https://example.invalid",
            "https://example.invalid", 2020, "0 0 6 * * *", false, false, false)) {
        @Override
        public <T> List<T> read(String release, String asset, Set<String> required,
                Function<CSVRecord, T> mapper) {
            try (var reader = new StringReader(served.computeIfAbsent(asset, AvailabilityIngestTests::fixture));
                    CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                            .get().parse(reader)) {
                verifyHeader(URI.create("fixture:" + asset), parser.getHeaderMap().keySet(), required);
                List<T> mapped = new ArrayList<>();
                for (CSVRecord record : parser) {
                    T value = mapper.apply(record);
                    if (value != null) {
                        mapped.add(value);
                    }
                }
                return mapped;
            } catch (java.io.IOException e) {
                throw new IngestException("fixture " + asset, e);
            }
        }
    };

    private final Map<String, Long> ids = new HashMap<>();
    private final Map<String, Integer> teams = new HashMap<>();

    @BeforeEach
    void players() {
        db.reset();
        served.clear();
        for (String abbr : List.of("BAL", "BUF", "CIN", "HOU", "LAC", "MIN", "NE", "NYG", "NYJ", "PHI")) {
            teams.put(abbr, db.team(abbr));
        }
        // Their real gsis ids. Beard and Henderson are deliberately absent: unmappable.
        seed("00-0036900", "Ja'Marr Chase", "WR", "CIN");
        seed("00-0036988", "Josh Palmer", "WR", "BUF");
        seed("00-0037012", "Trey Lance", "QB", "LAC");
        seed("00-0039064", "Zay Flowers", "WR", "BAL");
        seed("00-0041395", "Eli Raridon", "TE", "NE");
        seed("00-0033885", "David Njoku", "TE", "LAC");
        seed("00-0032385", "Sterling Shepard", "WR", "NYJ");
        seed("00-0027973", "Andy Dalton", "QB", "PHI");
        seed("00-0031235", "Odell Beckham Jr.", "WR", "MIN");
        seed("00-0039923", "J.J. McCarthy", "QB", "NYG");
        seed("00-0039359", "Cade Stover", "TE", "HOU");
    }

    @Test
    void storesTheRosterAsShippedAndSkipsWhatCannotBeTiedToAPlayer() {
        IngestResult result = rosters(MONDAY).ingest(2026);

        assertThat(result.rowsRead()).isEqualTo(32);
        assertThat(result.rowsUpserted()).as("all but the blank id and the unknown player").isEqualTo(30);
        assertThat(db.jdbc.queryForMap("""
                SELECT r.status, r.status_detail, t.abbr AS team, r.ingested_at FROM roster_week r
                  JOIN teams t ON t.id = r.team_id WHERE r.season = 2026 AND r.week = 5 AND r.player_id = ?
                """, ids.get("Trey Lance")))
                .containsEntry("status", "RES").containsEntry("status_detail", "R01").containsEntry("team", "LAC")
                .containsEntry("ingested_at", Timestamp.from(MONDAY));
        assertThat(count("SELECT count(*) FROM roster_week WHERE player_id IS NULL")).isZero();
    }

    @Test
    void storesTheInjuryReportAsShipped() {
        IngestResult result = injuries(MONDAY).ingest(2026);

        assertThat(result.rowsUpserted()).isEqualTo(10);
        assertThat(db.jdbc.queryForMap("""
                SELECT report_status, practice_status, report_injury FROM injury_report
                 WHERE season = 2026 AND week = 5 AND player_id = ?
                """, ids.get("Josh Palmer")))
                .containsEntry("report_status", "Out")
                .containsEntry("practice_status", "Did Not Participate In Practice")
                .containsEntry("report_injury", "Shoulder");
    }

    /** The real players, week 5, through the production service: every case, end to end. */
    @Test
    void theRealWeekFiveClassifiesAsTheApprovedRuleSays() {
        rosters(MONDAY).ingest(2026);
        injuries(MONDAY).ingest(2026);

        Map<Long, Availability> week5 = new AvailabilityService(db.jdbc).forWeek(WEEK_5);

        assertThat(status(week5, "Ja'Marr Chase")).isEqualTo(AvailabilityStatus.QUESTIONABLE);
        assertThat(status(week5, "Josh Palmer")).as("active and Out").isEqualTo(AvailabilityStatus.OUT);
        assertThat(status(week5, "Trey Lance")).as("reserve and Doubtful").isEqualTo(AvailabilityStatus.RESERVE);
        assertThat(status(week5, "Zay Flowers")).isEqualTo(AvailabilityStatus.QUESTIONABLE);
        assertThat(status(week5, "Eli Raridon")).as("week 4's Questionable does not carry")
                .isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(status(week5, "David Njoku")).isEqualTo(AvailabilityStatus.RESERVE);
        assertThat(status(week5, "Sterling Shepard")).isEqualTo(AvailabilityStatus.NOT_ON_ROSTER);
        assertThat(status(week5, "Andy Dalton")).isEqualTo(AvailabilityStatus.NOT_ON_ROSTER);
        assertThat(status(week5, "Odell Beckham Jr.")).as("game-day inactive is never a reason")
                .isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(week5.get(ids.get("Odell Beckham Jr.")).teamId()).isEqualTo(teams.get("MIN"));
        assertThat(week5.get(ids.get("Josh Palmer")))
                .satisfies(a -> {
                    assertThat(a.ranked()).isFalse();
                    assertThat(a.rosterStatus()).isEqualTo("ACT");
                    assertThat(a.reportStatus()).isEqualTo("Out");
                    assertThat(a.rosterIsCurrent()).isTrue();
                    assertThat(a.rosterAsOf()).isEqualTo(MONDAY);
                    assertThat(a.reportAsOf()).isEqualTo(MONDAY);
                });
    }

    /** A trade: the player follows his newest roster row, MIN in week 3 and NYG from week 4. */
    @Test
    void aTradedPlayerIsOnHisNewTeamFromTheWeekHeMoved() {
        rosters(MONDAY).ingest(2026);
        AvailabilityService service = new AvailabilityService(db.jdbc);

        assertThat(service.of(ids.get("J.J. McCarthy"), new SeasonWeek(2026, 3)).teamId()).isEqualTo(teams.get("MIN"));
        assertThat(service.of(ids.get("J.J. McCarthy"), new SeasonWeek(2026, 4)).teamId()).isEqualTo(teams.get("NYG"));
    }

    /** Players are tied by gsis id only: a second player with the same name is never touched. */
    @Test
    void aSharedNameNeverBorrowsAnotherPlayersRow() {
        long namesake = db.player("00-0099999", "Zay Flowers", "WR", teams.get("BAL"));

        rosters(MONDAY).ingest(2026);
        injuries(MONDAY).ingest(2026);

        assertThat(count("SELECT count(*) FROM roster_week WHERE player_id = " + namesake)).isZero();
        assertThat(count("SELECT count(*) FROM injury_report WHERE player_id = " + namesake)).isZero();
    }

    @Test
    void aSecondPullOfTheSameFileChangesNothingButWhenItWasRead() {
        rosters(MONDAY).ingest(2026);
        injuries(MONDAY).ingest(2026);
        List<Map<String, Object>> first = snapshot();

        Instant tuesday = MONDAY.plusSeconds(86400);
        rosters(tuesday).ingest(2026);
        injuries(tuesday).ingest(2026);

        assertThat(snapshot()).isEqualTo(first);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM roster_week WHERE ingested_at <> ?", Long.class,
                Timestamp.from(tuesday))).as("every row says when it was last read").isZero();
    }

    /** A status that changed upstream is replaced, not kept beside the old one. */
    @Test
    void aStatusChangedUpstreamReplacesTheStoredOne() {
        rosters(MONDAY).ingest(2026);
        served.put("roster_weekly_2026.csv", variant("roster_weekly_2026.csv",
                row -> row.get("full_name").equals("Eli Raridon") && row.get("week").equals("5"),
                Map.of("status", "RES", "status_description_abbr", "R01")));

        rosters(MONDAY.plusSeconds(3600)).ingest(2026);

        assertThat(db.jdbc.queryForObject(
                "SELECT status FROM roster_week WHERE season = 2026 AND week = 5 AND player_id = ?",
                String.class, ids.get("Eli Raridon"))).isEqualTo("RES");
        assertThat(count("SELECT count(*) FROM roster_week WHERE player_id = " + ids.get("Eli Raridon")))
                .isEqualTo(3);
    }

    /** A week the file does not carry is no evidence about that week, and is left alone. */
    @Test
    void aWeekTheFileDoesNotCarryIsLeftAsItWas() {
        db.roster(2026, 2, ids.get("Ja'Marr Chase"), teams.get("CIN"), "ACT");

        rosters(MONDAY).ingest(2026);

        assertThat(count("SELECT count(*) FROM roster_week WHERE week = 2")).isOne();
    }

    /**
     * A truncated roster must not empty the board: a file that would shrink a stored week below
     * 90% fails the run and writes nothing at all.
     */
    @Test
    void aRosterFileThatWouldShrinkAWeekFailsAndWritesNothing() {
        rosters(MONDAY).ingest(2026);
        for (int i = 0; i < 2; i++) {
            long extra = db.player("00-00000" + (10 + i), "Extra " + i, "WR", teams.get("CIN"));
            db.roster(2026, 5, extra, teams.get("CIN"), "ACT");
        }
        db.jdbc.update("UPDATE roster_week SET status = 'XXX' WHERE week = 4");

        assertThatThrownBy(() -> rosters(MONDAY.plusSeconds(3600)).ingest(2026))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("week 5")
                .hasMessageContaining("nothing was written");
        assertThat(count("SELECT count(*) FROM roster_week WHERE week = 4 AND status = 'XXX'"))
                .as("not even the weeks that would have passed").isEqualTo(10);
    }

    /**
     * Postseason rows are not projected and are not stored -- including one whose week number
     * looks regular: 2020's wild card round is week 18.
     */
    @Test
    void thePostseasonIsNotRead() {
        served.put("roster_weekly_2026.csv", fixture("roster_weekly_2026.csv").replace(",5,REG,", ",18,WC,"));
        served.put("injuries_2026.csv", variant("injuries_2026.csv", row -> row.get("week").equals("5"),
                Map.of("week", "18", "game_type", "WC")));

        rosters(MONDAY).ingest(2026);
        injuries(MONDAY).ingest(2026);

        assertThat(count("SELECT count(*) FROM roster_week WHERE week = 18")).isZero();
        assertThat(count("SELECT count(*) FROM roster_week")).isEqualTo(20);
        assertThat(count("SELECT count(*) FROM injury_report WHERE week = 18")).isZero();
    }

    /**
     * 2024's file has a different column list (date_modified, no season_type) and listed one
     * player-week twice: the shared columns are all that is read, and the stricter report is kept.
     */
    @Test
    void the2024FormatIsReadAndATwiceListedWeekKeepsTheStricterReport() {
        IngestResult result = injuries(MONDAY).ingest(2024);

        assertThat(result.rowsRead()).isEqualTo(2);
        assertThat(db.jdbc.queryForList("SELECT report_status FROM injury_report WHERE season = 2024", String.class))
                .containsExactly("Out");

        // In whichever order the file lists them: the stricter wins, not the first or the last.
        // Parsed, not split on newlines: Stover's practice_status is a quoted field with a newline in it.
        served.put("injuries_2024.csv", reversedRows("injuries_2024.csv"));
        injuries(MONDAY).ingest(2024);
        assertThat(db.jdbc.queryForList("SELECT report_status FROM injury_report WHERE season = 2024", String.class))
                .containsExactly("Out");
    }

    @Test
    void theRequiredColumnsAreOnlyTheOnesEverySeasonShips() {
        assertThat(InjuryReportIngestor.REQUIRED_COLUMNS).doesNotContain("season_type", "date_modified");
        assertThat(RosterWeekIngestor.REQUIRED_COLUMNS).contains("gsis_id", "status", "team", "week", "game_type");
    }

    // --- helpers -------------------------------------------------------------------------

    private RosterWeekIngestor rosters(Instant now) {
        return new RosterWeekIngestor(client, db.jdbc, db.transaction, Clock.fixed(now, ZoneOffset.UTC));
    }

    private InjuryReportIngestor injuries(Instant now) {
        return new InjuryReportIngestor(client, db.jdbc, db.transaction, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void seed(String gsis, String name, String position, String team) {
        ids.put(name, db.player(gsis, name, position, teams.get(team)));
    }

    private AvailabilityStatus status(Map<Long, Availability> week, String name) {
        return week.get(ids.get(name)).status();
    }

    private List<Map<String, Object>> snapshot() {
        return db.jdbc.queryForList("""
                SELECT 'r' AS t, season, week, player_id, team_id, status AS a, status_detail AS b, NULL AS c
                  FROM roster_week
                UNION ALL
                SELECT 'i', season, week, player_id, team_id, report_status, practice_status, report_injury
                  FROM injury_report
                ORDER BY 1, 2, 3, 4
                """);
    }

    private long count(String sql) {
        return db.jdbc.queryForObject(sql, Long.class);
    }

    /** A real fixture with one cell or two changed in the rows {@code which} picks -- a test's variant. */
    private static String variant(String asset, java.util.function.Predicate<Map<String, String>> which,
            Map<String, String> changes) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
                .parse(new StringReader(fixture(asset)))) {
            List<String> header = parser.getHeaderNames();
            StringBuilder out = new StringBuilder();
            try (var printer = new org.apache.commons.csv.CSVPrinter(out,
                    CSVFormat.DEFAULT.builder().setHeader(header.toArray(String[]::new)).get())) {
                for (CSVRecord record : parser) {
                    Map<String, String> row = new java.util.LinkedHashMap<>(record.toMap());
                    if (which.test(row)) {
                        row.putAll(changes);
                    }
                    printer.printRecord(header.stream().map(row::get).toList());
                }
            }
            return out.toString();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A real fixture with its data rows in reverse order, through the CSV parser. */
    private static String reversedRows(String asset) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
                .parse(new StringReader(fixture(asset)))) {
            List<String> header = parser.getHeaderNames();
            List<CSVRecord> records = new ArrayList<>(parser.getRecords());
            java.util.Collections.reverse(records);
            StringBuilder out = new StringBuilder();
            try (var printer = new org.apache.commons.csv.CSVPrinter(out,
                    CSVFormat.DEFAULT.builder().setHeader(header.toArray(String[]::new)).get())) {
                for (CSVRecord record : records) {
                    printer.printRecord(record.values());
                }
            }
            return out.toString();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fixture(String asset) {
        try {
            return new String(new ClassPathResource("nflverse/availability/" + asset).getInputStream()
                    .readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
