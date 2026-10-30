package com.fantasykai.ingest;

import com.fantasykai.projections.AvailabilityRules;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * nflverse's injury reports into {@code injury_report} (V9): each player's official designation
 * for one regular-season week's game. Phase 6, Slice 4.
 *
 * <p>Source values are stored untouched -- a blank designation as NULL -- and
 * {@link AvailabilityRules} gives them meaning on read. Each week the file carries replaces that
 * week in one transaction; a week it does not carry is left alone.
 *
 * <p>Unlike the roster, there is no guard against a shrinking week. A lost injury row can only
 * remove a designation, and a player with none is ranked: the failure is visible and never makes
 * anyone disappear.
 *
 * <p>Only the columns every season shares are read: 2024's file has {@code date_modified} and no
 * {@code season_type}, 2025-26 the reverse. Two rows for one player-week (2024 had two such) are
 * resolved to the more restrictive designation ({@link AvailabilityRules#restrictiveness}), and
 * counted.
 */
@Component
public class InjuryReportIngestor {

    static final String SOURCE = "nflverse.injuries";

    static final Set<String> REQUIRED_COLUMNS = Set.of("season", "week", "game_type", "team", "gsis_id",
            "report_status", "practice_status", "report_primary_injury");

    private static final Logger log = LoggerFactory.getLogger(InjuryReportIngestor.class);

    private static final String INSERT = """
            INSERT INTO injury_report (season, week, player_id, team_id, report_status, practice_status,
                                       report_injury, ingested_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private record Row(int week, String gsisId, String team, String reportStatus, String practiceStatus,
            String injury) {}

    private final NflverseClient client;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public InjuryReportIngestor(NflverseClient client, JdbcTemplate jdbc, TransactionTemplate transaction,
            Clock clock) {
        this.client = client;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.clock = clock;
    }

    public IngestResult ingest(int season) {
        List<Row> rows = client.read("injuries", "injuries_%d.csv".formatted(season), REQUIRED_COLUMNS,
                record -> {
            Integer week = CsvValues.integer(record, "week");
            if (!"REG".equals(CsvValues.text(record, "game_type")) || week == null || week < 1 || week > 18) {
                return null;
            }
            return new Row(week, CsvValues.text(record, "gsis_id"), CsvValues.text(record, "team"),
                    CsvValues.text(record, "report_status"), CsvValues.text(record, "practice_status"),
                    CsvValues.text(record, "report_primary_injury"));
        });

        Map<String, Long> players = Lookups.players(jdbc);
        Map<String, Integer> teams = Lookups.teams(jdbc);
        Instant now = Instant.now(clock);
        Map<Integer, Map<Long, Row>> byWeek = new TreeMap<>();
        List<String> unmapped = new ArrayList<>();
        int blank = 0;
        int unknownTeam = 0;
        int duplicate = 0;
        Map<String, Integer> unknownStatus = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.gsisId() == null) {
                blank++;
                continue;
            }
            Long player = players.get(row.gsisId());
            if (player == null) {
                unmapped.add(row.gsisId());
                continue;
            }
            Integer team = teams.get(row.team());
            if (team == null) {
                unknownTeam++;
                continue;
            }
            if (row.reportStatus() != null && !AvailabilityRules.knownReportStatus(row.reportStatus())) {
                unknownStatus.merge(row.reportStatus(), 1, Integer::sum);
            }
            Map<Long, Row> week = byWeek.computeIfAbsent(row.week(), w -> new LinkedHashMap<>());
            Row earlier = week.get(player);
            if (earlier != null) {
                duplicate++;
                if (AvailabilityRules.restrictiveness(row.reportStatus())
                        <= AvailabilityRules.restrictiveness(earlier.reportStatus())) {
                    continue;
                }
            }
            week.put(player, row);
        }

        transaction.executeWithoutResult(status -> byWeek.forEach((week, weekRows) -> {
            jdbc.update("DELETE FROM injury_report WHERE season = ? AND week = ?", season, week);
            List<Object[]> args = new ArrayList<>(weekRows.size());
            weekRows.forEach((player, row) -> args.add(new Object[] {season, week, player,
                    teams.get(row.team()), row.reportStatus(), row.practiceStatus(), row.injury(),
                    Timestamp.from(now)}));
            jdbc.batchUpdate(INSERT, args);
        }));

        int written = byWeek.values().stream().mapToInt(Map::size).sum();
        if (!unmapped.isEmpty()) {
            List<String> unknown = unmapped.stream().distinct().toList();
            log.warn("{} {}: {} rows name {} players the master does not know, skipped; e.g. {}",
                    SOURCE, season, unmapped.size(), unknown.size(), unknown.subList(0, Math.min(5, unknown.size())));
        }
        if (blank + unknownTeam + duplicate > 0 || !unknownStatus.isEmpty()) {
            log.warn("{} {}: skipped {} without an id, {} with an unknown team; {} player-weeks listed twice,"
                    + " the more restrictive kept; designations with no meaning, stored as read: {}",
                    SOURCE, season, blank, unknownTeam, duplicate, unknownStatus);
        }
        log.info("{} {}: weeks {} replaced, {} rows", SOURCE, season, byWeek.keySet(), written);
        return IngestResult.of(SOURCE, rows.size(), written);
    }
}
