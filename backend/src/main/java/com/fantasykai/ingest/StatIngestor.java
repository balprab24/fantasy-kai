package com.fantasykai.ingest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loads weekly stat lines from nflverse {@code stats_player_week} -- the
 * backbone table.
 *
 * <p>Raw stats only. The source also ships {@code fantasy_points} and
 * {@code fantasy_points_ppr}; both are deliberately ignored, because a stored
 * point value is only correct for one ruleset and the entire architecture is
 * that points are computed on demand.
 */
@Component
public class StatIngestor {

    static final String SOURCE = "nflverse.stats_player_week";

    private static final Logger log = LoggerFactory.getLogger(StatIngestor.class);

    /** A destination column and how to pull it out of a source row. */
    private record Field(String column, Function<CSVRecord, Object> extractor) {}

    private static Field count(String column, String sourceColumn) {
        return new Field(column, record -> CsvValues.shortValue(record, sourceColumn));
    }

    /**
     * The stat columns, in order. The INSERT statement and the argument array are
     * both generated from this one list, so a column can never silently line up
     * against the wrong value.
     */
    private static final List<Field> FIELDS = List.of(
            count("pass_att", "attempts"),
            count("pass_cmp", "completions"),
            count("pass_yd", "passing_yards"),
            count("pass_td", "passing_tds"),
            count("pass_int", "passing_interceptions"),
            count("pass_2pt", "passing_2pt_conversions"),

            count("rush_att", "carries"),
            count("rush_yd", "rushing_yards"),
            count("rush_td", "rushing_tds"),
            count("rush_2pt", "rushing_2pt_conversions"),

            count("targets", "targets"),
            count("rec", "receptions"),
            count("rec_yd", "receiving_yards"),
            count("rec_td", "receiving_tds"),
            count("rec_2pt", "receiving_2pt_conversions"),

            count("fum_lost", "fumbles_lost_total"),
            count("ret_td", "special_teams_tds"),

            count("punt_ret", "punt_returns"),
            count("punt_ret_yd", "punt_return_yards"),
            count("kick_ret", "kickoff_returns"),
            count("kick_ret_yd", "kickoff_return_yards"),

            count("fg_att", "fg_att"),
            count("fg_made", "fg_made"),
            count("fg_missed", "fg_missed"),
            count("fg_blocked", "fg_blocked"),
            count("fg_long", "fg_long"),
            count("fg_made_0_19", "fg_made_0_19"),
            count("fg_made_20_29", "fg_made_20_29"),
            count("fg_made_30_39", "fg_made_30_39"),
            count("fg_made_40_49", "fg_made_40_49"),
            count("fg_made_50_59", "fg_made_50_59"),
            count("fg_made_60_plus", "fg_made_60_"),
            count("pat_att", "pat_att"),
            count("pat_made", "pat_made"),
            count("pat_missed", "pat_missed"),

            // Shared sacks are credited as 0.5, so this one is not an integer.
            new Field("def_sacks", record -> {
                BigDecimal sacks = CsvValues.decimal(record, "def_sacks");
                return sacks != null ? sacks : BigDecimal.ZERO;
            }),
            count("def_int", "def_interceptions"),
            count("def_td", "def_tds"),
            count("def_safety", "def_safeties"),
            count("def_fumbles_forced", "def_fumbles_forced"),
            count("def_fumble_rec", "fumble_recovery_opp"),
            count("def_pass_defended", "def_pass_defended"),
            count("def_tackles_solo", "def_tackles_solo"),
            count("def_tackle_assists", "def_tackle_assists"),
            count("def_tackles_for_loss", "def_tackles_for_loss"),
            count("def_qb_hits", "def_qb_hits"),
            new Field("def_blocked_kicks", record -> (short) (
                    CsvValues.shortValue(record, "def_punt_blocks")
                            + CsvValues.shortValue(record, "def_pat_blocks")
                            + CsvValues.shortValue(record, "def_fg_blocks"))));

    private static final List<String> KEY_COLUMNS =
            List.of("player_id", "game_id", "season", "week", "team_id");

    private static final String UPSERT = buildUpsert();

    private static String buildUpsert() {
        List<String> all = new ArrayList<>(KEY_COLUMNS);
        FIELDS.forEach(field -> all.add(field.column()));

        String columns = String.join(", ", all);
        String placeholders = all.stream().map(c -> "?").collect(Collectors.joining(", "));
        // Everything but the conflict target is refreshed, so a re-run corrects
        // upstream revisions instead of leaving stale numbers behind.
        String updates = all.stream()
                .filter(c -> !c.equals("player_id") && !c.equals("game_id"))
                .map(c -> c + " = EXCLUDED." + c)
                .collect(Collectors.joining(",\n                   "));

        return """
                INSERT INTO player_game_stats (%s)
                VALUES (%s)
                ON CONFLICT (player_id, game_id) DO UPDATE
                   SET %s
                """.formatted(columns, placeholders, updates);
    }

    private static final String INSERT_MISSING_PLAYER = """
            INSERT INTO players (gsis_id, full_name, position, team_id, updated_at)
            VALUES (?, ?, ?, (SELECT id FROM teams WHERE abbr = ?), now())
            ON CONFLICT (gsis_id) DO NOTHING
            """;

    private final NflverseClient client;
    private final JdbcTemplate jdbc;

    public StatIngestor(NflverseClient client, JdbcTemplate jdbc) {
        this.client = client;
        this.jdbc = jdbc;
    }

    public IngestResult ingest(int season) {
        List<CSVRecord> records =
                client.read("stats_player", "stats_player_week_%d.csv".formatted(season), record -> record);

        ensurePlayersExist(records);

        Map<String, Long> playerIds = lookupLong("SELECT gsis_id, id FROM players WHERE gsis_id IS NOT NULL");
        Map<String, Long> gameIds = lookupLong("SELECT nflverse_game_id, id FROM games");
        Map<String, Integer> teamIds = lookupInt("SELECT abbr, id FROM teams");

        List<Object[]> batch = new ArrayList<>(records.size());
        for (CSVRecord record : records) {
            Object[] row = toRow(record, playerIds, gameIds, teamIds);
            if (row != null) {
                batch.add(row);
            }
        }

        jdbc.batchUpdate(UPSERT, batch);

        int skipped = records.size() - batch.size();
        if (skipped > 0) {
            log.warn("season {}: skipped {} of {} stat rows (no player id, or unknown game/team)",
                    season, skipped, records.size());
        }
        return IngestResult.of(SOURCE, records.size(), batch.size());
    }

    private Object[] toRow(CSVRecord record, Map<String, Long> playerIds,
            Map<String, Long> gameIds, Map<String, Integer> teamIds) {
        String gsisId = CsvValues.text(record, "player_id");
        String gameKey = CsvValues.text(record, "game_id");
        String teamAbbr = CsvValues.text(record, "team");
        Integer season = CsvValues.integer(record, "season");
        Integer week = CsvValues.integer(record, "week");
        if (gsisId == null || gameKey == null || teamAbbr == null || season == null || week == null) {
            return null;
        }

        Long playerId = playerIds.get(gsisId);
        Long gameId = gameIds.get(gameKey);
        Integer teamId = teamIds.get(teamAbbr);
        if (playerId == null || gameId == null || teamId == null) {
            return null;
        }

        Object[] row = new Object[KEY_COLUMNS.size() + FIELDS.size()];
        row[0] = playerId;
        row[1] = gameId;
        row[2] = season.shortValue();
        row[3] = week.shortValue();
        row[4] = teamId;
        for (int i = 0; i < FIELDS.size(); i++) {
            row[KEY_COLUMNS.size() + i] = FIELDS.get(i).extractor().apply(record);
        }
        return row;
    }

    /**
     * A handful of players appear in a stat line before they appear in the player
     * master. Create a minimal row for them so the stat line is not dropped; the
     * next players.csv pull fills in the detail.
     */
    private void ensurePlayersExist(List<CSVRecord> records) {
        Set<String> known = new HashSet<>(
                jdbc.queryForList("SELECT gsis_id FROM players WHERE gsis_id IS NOT NULL", String.class));

        Map<String, Object[]> missing = new LinkedHashMap<>();
        for (CSVRecord record : records) {
            String gsisId = CsvValues.text(record, "player_id", 16);
            if (gsisId == null || known.contains(gsisId) || missing.containsKey(gsisId)) {
                continue;
            }
            String name = CsvValues.text(record, "player_display_name", 96);
            String position = CsvValues.text(record, "position", 4);
            missing.put(gsisId, new Object[] {
                gsisId, name != null ? name : gsisId, position != null ? position : "UNK",
                CsvValues.text(record, "team", 4)
            });
        }
        if (!missing.isEmpty()) {
            log.info("creating {} players seen in stat lines but absent from the player master", missing.size());
            jdbc.batchUpdate(INSERT_MISSING_PLAYER, new ArrayList<>(missing.values()));
        }
    }

    private Map<String, Long> lookupLong(String sql) {
        Map<String, Long> map = new HashMap<>();
        jdbc.query(sql, rs -> {
            map.put(rs.getString(1), rs.getLong(2));
        });
        return map;
    }

    private Map<String, Integer> lookupInt(String sql) {
        Map<String, Integer> map = new HashMap<>();
        jdbc.query(sql, rs -> {
            map.put(rs.getString(1), rs.getInt(2));
        });
        return map;
    }
}
