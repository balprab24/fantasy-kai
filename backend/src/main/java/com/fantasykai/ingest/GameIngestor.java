package com.fantasykai.ingest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loads the schedule. One file covers every season, so it is filtered to the
 * backfill window rather than fetched per year.
 *
 * <p>Phase 4 took this from 8 of the source's 46 columns to 18. The scores, the
 * betting lines and the weather were already being downloaded and discarded, so
 * nothing about the fetch changed -- only what is kept.
 *
 * <p><strong>The file is the schedule as it is now, and a game can leave it.</strong>
 * nflverse has no status column: a game moved to another week is deleted and added
 * again under a new {@code game_id}, and a cancelled game is deleted (V8 records the
 * measured cases). An upsert alone would keep every such game here for good, with no
 * stats, and the projection job would project both teams for it. So each season this
 * run reads is reconciled against the file: a stored game the file no longer lists is
 * marked {@code delisted_at}, and one that comes back is listed again. Marked, never
 * deleted -- stats, appearances and locked projections may point at it.
 *
 * <p>Three guards keep a broken file from rewriting the schedule. A row this run cannot
 * use still counts as listed -- "could not read it" is not "it is gone". A season the file
 * has no row for is not reconciled at all. And a run that would delist more than
 * {@value #MAX_DELISTED_PER_SEASON} games of one season -- more than a week's slate at
 * once -- fails instead, delisting nothing.
 */
@Component
public class GameIngestor {

    static final String SOURCE = "nflverse.schedules";

    private static final Logger log = LoggerFactory.getLogger(GameIngestor.class);

    /**
     * More than this many games leaving one season in one run is a truncated or broken
     * file, not a schedule change: the most nflverse has ever moved at once is a handful
     * (2020), and one week's slate is 16.
     */
    static final int MAX_DELISTED_PER_SEASON = 16;

    /** nflverse publishes kickoff times in US Eastern. */
    private static final ZoneId LEAGUE_ZONE = ZoneId.of("America/New_York");

    /**
     * A destination column, the source column it reads, and how to pull it out.
     *
     * <p>The two names happen to match for all ten of these; they are still
     * written out separately, because the required-header set is generated from
     * this list and a coincidence is not a contract.
     */
    private record Field(String column, String sourceColumn,
            Function<CSVRecord, Object> extractor) {}

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
            new Field("home_score", "home_score", record -> CsvValues.shortOrNull(record, "home_score")),
            new Field("away_score", "away_score", record -> CsvValues.shortOrNull(record, "away_score")),
            new Field("spread_line", "spread_line", record -> CsvValues.decimal(record, "spread_line")),
            new Field("total_line", "total_line", record -> CsvValues.decimal(record, "total_line")),
            new Field("home_moneyline", "home_moneyline", record -> CsvValues.integer(record, "home_moneyline")),
            new Field("away_moneyline", "away_moneyline", record -> CsvValues.integer(record, "away_moneyline")),
            new Field("roof", "roof", record -> CsvValues.text(record, "roof", 12)),
            new Field("surface", "surface", record -> CsvValues.text(record, "surface", 16)),
            new Field("temp", "temp", record -> CsvValues.shortOrNull(record, "temp")),
            new Field("wind", "wind", record -> CsvValues.shortOrNull(record, "wind")));

    /**
     * Every source column this ingestor reads: the ten above, plus the ones the
     * row mapper resolves a game against. Generated from {@link #EXTRAS}.
     */
    static final Set<String> REQUIRED_COLUMNS = requiredColumns();

    private static Set<String> requiredColumns() {
        Set<String> required = new LinkedHashSet<>(List.of(
                "game_id", "season", "week", "game_type", "home_team", "away_team",
                "gameday", "gametime"));
        EXTRAS.forEach(field -> required.add(field.sourceColumn()));
        return Set.copyOf(required);
    }

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

        // A game the file lists is on the schedule, whatever an earlier run concluded.
        return """
                INSERT INTO games (%s)
                VALUES (%s)
                ON CONFLICT (nflverse_game_id) DO UPDATE
                   SET %s,
                       delisted_at = NULL
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
        // Every game the file lists, by season -- including a row this run cannot use. A row we
        // could not read is not a game the source dropped, and must never be delisted as one.
        Map<Integer, Set<String>> listed = new TreeMap<>();
        List<Object[]> rows = client.read("schedules", "games.csv", REQUIRED_COLUMNS, record -> {
            Integer season = CsvValues.integer(record, "season");
            if (season == null || !seasons.contains(season)) {
                return null;
            }
            String gameId = CsvValues.text(record, "game_id", 20);
            if (gameId != null) {
                listed.computeIfAbsent(season, s -> new HashSet<>()).add(gameId);
            }
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
        listed.forEach(this::reconcile);
        return IngestResult.of(SOURCE, rows.size(), rows.size());
    }

    /**
     * Marks the stored games of {@code season} that the file no longer lists. Called only for
     * a season the file has rows for, so a file missing a season never empties it.
     */
    private void reconcile(int season, Set<String> listedIds) {
        List<String> gone = jdbc.queryForList(
                "SELECT nflverse_game_id FROM games WHERE season = ? AND delisted_at IS NULL ORDER BY nflverse_game_id",
                String.class, season).stream()
                .filter(id -> !listedIds.contains(id))
                .toList();
        if (gone.isEmpty()) {
            return;
        }
        if (gone.size() > MAX_DELISTED_PER_SEASON) {
            throw new IngestException("the schedule no longer lists " + gone.size() + " stored " + season
                    + " games, more than " + MAX_DELISTED_PER_SEASON + " at once -- a broken file, not a"
                    + " schedule change; nothing was delisted. First: " + gone.subList(0, 5));
        }
        jdbc.batchUpdate("UPDATE games SET delisted_at = now() WHERE nflverse_game_id = ?",
                gone.stream().map(id -> new Object[] {id}).toList());
        log.warn("{}: {} {} game(s) no longer on nflverse's schedule, delisted: {}",
                SOURCE, gone.size(), season, gone);
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
