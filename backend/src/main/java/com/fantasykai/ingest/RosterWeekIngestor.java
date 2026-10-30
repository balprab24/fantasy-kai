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
 * nflverse's weekly rosters into {@code roster_week} (V9): who was on which team, with which
 * status, each regular-season week. Phase 6, Slice 4.
 *
 * <p>Source values are stored untouched; {@link AvailabilityRules} gives them meaning on read.
 *
 * <p><strong>Each week the file carries replaces that week</strong>, in one transaction, so the
 * table is the source as of this run and a status that changed is never left stale. A week the
 * file does not carry is left as it is. A team's week rows are rewritten every day until the game
 * -- and after it, when INA appears -- so the latest read wins.
 *
 * <p>A truncated file must not empty a roster: membership is what makes a projection candidate,
 * so a file that would shrink any stored week below {@value #MIN_KEPT_PERCENT}% of its rows fails
 * the run and writes nothing. A week's rows do not shrink in normal operation -- a released
 * player stays listed, as CUT.
 *
 * <p>A row with no gsis id, or one the player master does not know, cannot be tied to a player:
 * it is counted, logged with examples, and skipped -- never guessed by name. Measured 2026-10-12:
 * 6 blank and 26 unknown of 13,048 rows, practice-squad players the master has not caught up with.
 */
@Component
public class RosterWeekIngestor {

    static final String SOURCE = "nflverse.weekly_rosters";

    static final Set<String> REQUIRED_COLUMNS = Set.of(
            "season", "week", "game_type", "team", "gsis_id", "status", "status_description_abbr");

    static final int MIN_KEPT_PERCENT = 90;

    private static final Logger log = LoggerFactory.getLogger(RosterWeekIngestor.class);

    private static final String INSERT = """
            INSERT INTO roster_week (season, week, player_id, team_id, status, status_detail, ingested_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    /** One usable source row. */
    private record Row(int week, String gsisId, String team, String status, String detail) {}

    private final NflverseClient client;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RosterWeekIngestor(NflverseClient client, JdbcTemplate jdbc, TransactionTemplate transaction,
            Clock clock) {
        this.client = client;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.clock = clock;
    }

    public IngestResult ingest(int season) {
        List<Row> rows = client.read("weekly_rosters", "roster_weekly_%d.csv".formatted(season),
                REQUIRED_COLUMNS, record -> {
            Integer week = CsvValues.integer(record, "week");
            if (!"REG".equals(CsvValues.text(record, "game_type")) || week == null || week < 1 || week > 18) {
                return null; // the postseason is not projected
            }
            return new Row(week, CsvValues.text(record, "gsis_id"), CsvValues.text(record, "team"),
                    CsvValues.text(record, "status"), CsvValues.text(record, "status_description_abbr"));
        });

        Map<String, Long> players = Lookups.players(jdbc);
        Map<String, Integer> teams = Lookups.teams(jdbc);
        Instant now = Instant.now(clock);
        Map<Integer, Map<Long, Object[]>> byWeek = new TreeMap<>();
        List<String> unmapped = new ArrayList<>();
        int blank = 0;
        int unknownTeam = 0;
        int duplicate = 0;
        Map<String, Integer> unknownStatus = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.gsisId() == null || row.status() == null) {
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
            if (!AvailabilityRules.knownRosterStatus(row.status())) {
                unknownStatus.merge(row.status(), 1, Integer::sum);
            }
            Object[] args = {season, row.week(), player, team, row.status(), row.detail(), Timestamp.from(now)};
            if (byWeek.computeIfAbsent(row.week(), w -> new LinkedHashMap<>()).putIfAbsent(player, args) != null) {
                duplicate++;
            }
        }

        refuseAShrinkingWeek(season, byWeek);
        transaction.executeWithoutResult(status -> byWeek.forEach((week, weekRows) -> {
            jdbc.update("DELETE FROM roster_week WHERE season = ? AND week = ?", season, week);
            jdbc.batchUpdate(INSERT, List.copyOf(weekRows.values()));
        }));

        int written = byWeek.values().stream().mapToInt(Map::size).sum();
        if (!unmapped.isEmpty()) {
            List<String> unknown = unmapped.stream().distinct().toList();
            log.warn("{} {}: {} rows name {} players the master does not know, skipped; e.g. {}",
                    SOURCE, season, unmapped.size(), unknown.size(), unknown.subList(0, Math.min(5, unknown.size())));
        }
        if (blank + unknownTeam + duplicate > 0 || !unknownStatus.isEmpty()) {
            log.warn("{} {}: skipped {} without an id or status, {} with an unknown team, {} repeated;"
                    + " statuses with no meaning yet, stored as read: {}",
                    SOURCE, season, blank, unknownTeam, duplicate, unknownStatus);
        }
        log.info("{} {}: weeks {} replaced, {} rows", SOURCE, season, byWeek.keySet(), written);
        return IngestResult.of(SOURCE, rows.size(), written);
    }

    private void refuseAShrinkingWeek(int season, Map<Integer, Map<Long, Object[]>> byWeek) {
        Map<Integer, Integer> stored = new TreeMap<>();
        jdbc.query("SELECT week, count(*) FROM roster_week WHERE season = ? GROUP BY week",
                rs -> {
                    stored.put(rs.getInt(1), rs.getInt(2));
                }, season);
        byWeek.forEach((week, rows) -> {
            int before = stored.getOrDefault(week, 0);
            if (rows.size() * 100L < before * (long) MIN_KEPT_PERCENT) {
                throw new IngestException("the " + season + " week " + week + " roster would shrink from "
                        + before + " rows to " + rows.size() + ", below " + MIN_KEPT_PERCENT
                        + "% -- a broken file, not roster moves; nothing was written");
            }
        });
    }
}
