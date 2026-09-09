package com.fantasykai.ingest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loads the schedule. One file covers every season, so it is filtered to the
 * backfill window rather than fetched per year.
 *
 * <p>Phase 4 took this from 8 of the source's 46 columns to 18. The scores, the
 * betting lines and the weather were already being downloaded and discarded, so
 * nothing about the fetch changed -- only what is kept.
 */
@Component
public class GameIngestor {

    static final String SOURCE = "nflverse.schedules";

    /** nflverse publishes kickoff times in US Eastern. */
    private static final ZoneId LEAGUE_ZONE = ZoneId.of("America/New_York");

    /** A destination column and how to pull it out of a source row. */
    private record Field(String column, Function<CSVRecord, Object> extractor) {}

    /**
     * The identifying columns, in order, and the value expression each one binds
     * through. Two resolve a team abbreviation with a subselect rather than
     * binding an id, so this half is written out instead of generated.
     */
    private static final List<String> KEY_COLUMNS = List.of(
            "nflverse_game_id", "season", "week", "season_type",
            "home_team_id", "away_team_id", "kickoff_at");

    private static final List<String> KEY_VALUES = List.of(
            "?", "?", "?", "?",
            "(SELECT id FROM teams WHERE abbr = ?)",
            "(SELECT id FROM teams WHERE abbr = ?)",
            "?");

    /**
     * What Phase 4 added, in order. The INSERT list, the DO UPDATE SET list and
     * the tail of the argument array are all generated from this one list, so a
     * spread can never silently line up against a total -- the same device as
     * {@code StatIngestor.FIELDS}, and at seventeen bind positions the same reason: a
     * positional mistake still type-checks.
     *
     * <p>None of these may use {@link CsvValues#shortValue}, which defaults a
     * missing value to 0. A blank score, temperature or wind reading means "not
     * recorded", and 0 is a real value for all three.
     */
    private static final List<Field> EXTRAS = List.of(
            new Field("home_score", record -> CsvValues.shortOrNull(record, "home_score")),
            new Field("away_score", record -> CsvValues.shortOrNull(record, "away_score")),
            new Field("spread_line", record -> CsvValues.decimal(record, "spread_line")),
            new Field("total_line", record -> CsvValues.decimal(record, "total_line")),
            new Field("home_moneyline", record -> CsvValues.integer(record, "home_moneyline")),
            new Field("away_moneyline", record -> CsvValues.integer(record, "away_moneyline")),
            new Field("roof", record -> CsvValues.text(record, "roof", 12)),
            new Field("surface", record -> CsvValues.text(record, "surface", 16)),
            new Field("temp", record -> CsvValues.shortOrNull(record, "temp")),
            new Field("wind", record -> CsvValues.shortOrNull(record, "wind")));

    private static final String UPSERT = buildUpsert();

    private static String buildUpsert() {
        List<String> columns = new ArrayList<>(KEY_COLUMNS);
        EXTRAS.forEach(field -> columns.add(field.column()));

        List<String> values = new ArrayList<>(KEY_VALUES);
        EXTRAS.forEach(field -> values.add("?"));

        // Everything but the conflict target is refreshed. Betting lines land
        // about a week ahead of kickoff, so a game is almost always inserted
        // without one and filled by a later run -- a column missing from this
        // list would never be populated at all, and nothing would say so.
        String updates = columns.stream()
                .filter(column -> !column.equals("nflverse_game_id"))
                .map(column -> column + " = EXCLUDED." + column)
                .collect(Collectors.joining(",\n                   "));

        return """
                INSERT INTO games (%s)
                VALUES (%s)
                ON CONFLICT (nflverse_game_id) DO UPDATE
                   SET %s
                """.formatted(String.join(", ", columns), String.join(", ", values), updates);
    }

    private final NflverseClient client;
    private final JdbcTemplate jdbc;

    public GameIngestor(NflverseClient client, JdbcTemplate jdbc) {
        this.client = client;
        this.jdbc = jdbc;
    }

    /** @param seasons the seasons to keep; the source file spans 1999-present */
    public IngestResult ingest(List<Integer> seasons) {
        List<Object[]> rows = client.read("schedules", "games.csv", record -> {
            Integer season = CsvValues.integer(record, "season");
            if (season == null || !seasons.contains(season)) {
                return null;
            }
            String gameId = CsvValues.text(record, "game_id", 20);
            Integer week = CsvValues.integer(record, "week");
            String home = CsvValues.text(record, "home_team", 4);
            String away = CsvValues.text(record, "away_team", 4);
            if (gameId == null || week == null || home == null || away == null) {
                return null;
            }

            Object[] args = new Object[KEY_COLUMNS.size() + EXTRAS.size()];
            args[0] = gameId;
            args[1] = season.shortValue();
            args[2] = week.shortValue();
            args[3] = CsvValues.text(record, "game_type", 8);
            args[4] = home;
            args[5] = away;
            args[6] = kickoff(record);
            for (int i = 0; i < EXTRAS.size(); i++) {
                args[KEY_COLUMNS.size() + i] = EXTRAS.get(i).extractor().apply(record);
            }
            return args;
        });

        jdbc.batchUpdate(UPSERT, rows);
        return IngestResult.of(SOURCE, rows.size(), rows.size());
    }

    private static OffsetDateTime kickoff(CSVRecord record) {
        String day = CsvValues.text(record, "gameday");
        String time = CsvValues.text(record, "gametime");
        if (day == null) {
            return null;
        }
        LocalTime localTime = time == null ? LocalTime.NOON : LocalTime.parse(time);
        return LocalDate.parse(day).atTime(localTime).atZone(LEAGUE_ZONE).toOffsetDateTime();
    }
}
