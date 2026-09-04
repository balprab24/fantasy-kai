package com.fantasykai.ingest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loads the schedule. One file covers every season, so it is filtered to the
 * backfill window rather than fetched per year.
 */
@Component
public class GameIngestor {

    static final String SOURCE = "nflverse.schedules";

    /** nflverse publishes kickoff times in US Eastern. */
    private static final ZoneId LEAGUE_ZONE = ZoneId.of("America/New_York");

    private static final String UPSERT = """
            INSERT INTO games (nflverse_game_id, season, week, season_type,
                               home_team_id, away_team_id, kickoff_at)
            VALUES (?, ?, ?, ?,
                    (SELECT id FROM teams WHERE abbr = ?),
                    (SELECT id FROM teams WHERE abbr = ?),
                    ?)
            ON CONFLICT (nflverse_game_id) DO UPDATE
               SET season = EXCLUDED.season,
                   week = EXCLUDED.week,
                   season_type = EXCLUDED.season_type,
                   home_team_id = EXCLUDED.home_team_id,
                   away_team_id = EXCLUDED.away_team_id,
                   kickoff_at = EXCLUDED.kickoff_at
            """;

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
            return new Object[] {
                gameId, season.shortValue(), week.shortValue(),
                CsvValues.text(record, "game_type", 8),
                home, away,
                kickoff(record)
            };
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
