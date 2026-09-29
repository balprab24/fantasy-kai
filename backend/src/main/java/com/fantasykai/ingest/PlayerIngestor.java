package com.fantasykai.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loads the player master.
 *
 * <p>Note what is <em>not</em> here: nflverse's players.csv carries espn, pfr,
 * nfl and esb ids but <strong>no Sleeper id</strong>. The Sleeper crosswalk has
 * to come from Sleeper's own API, matched back on gsis_id -- see
 * {@link SleeperCrosswalk}.
 */
@Component
public class PlayerIngestor {

    static final String SOURCE = "nflverse.players";

    private static final Logger log = LoggerFactory.getLogger(PlayerIngestor.class);

    /** nflverse column -> key inside players.external_ids. */
    private static final Map<String, String> EXTERNAL_IDS = Map.of(
            "espn_id", "espn",
            "pfr_id", "pfr",
            "nfl_id", "nfl",
            "esb_id", "esb");

    /**
     * Every source column this ingestor reads -- the identity columns plus
     * whatever {@link #EXTERNAL_IDS} maps, so adding a crosswalk id adds itself
     * to the header check. The identity list is hand-written: a column read in
     * {@link #ingest()} has to be named here too, or a rename upstream nulls it
     * silently instead of failing the run.
     *
     * <p>{@code birth_date} is read but deliberately <em>not</em> required: it is
     * display-only, and a rename upstream should cost ages on screen, not the
     * whole daily pull.
     */
    static final Set<String> REQUIRED_COLUMNS = requiredColumns();

    private static Set<String> requiredColumns() {
        Set<String> required = new LinkedHashSet<>(List.of(
                "gsis_id", "display_name", "position", "latest_team", "status"));
        required.addAll(EXTERNAL_IDS.keySet());
        return Set.copyOf(required);
    }

    private static final String UPSERT = """
            INSERT INTO players (gsis_id, external_ids, full_name, position, team_id, status,
                                 birth_date, updated_at)
            VALUES (?, ?::jsonb, ?, ?, (SELECT id FROM teams WHERE abbr = ?), ?, ?, now())
            ON CONFLICT (gsis_id) DO UPDATE
               SET external_ids = EXCLUDED.external_ids,
                   full_name = EXCLUDED.full_name,
                   position = EXCLUDED.position,
                   team_id = EXCLUDED.team_id,
                   status = EXCLUDED.status,
                   birth_date = EXCLUDED.birth_date,
                   updated_at = now()
            """;

    private final NflverseClient client;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PlayerIngestor(NflverseClient client, JdbcTemplate jdbc, ObjectMapper json) {
        this.client = client;
        this.jdbc = jdbc;
        this.json = json;
    }

    public IngestResult ingest() {
        // birth_date is optional to the run, not invisible: a missing column or a
        // value that will not parse is counted and said, so "no ages" is never
        // mistaken for "nflverse has no birth dates".
        AtomicInteger unreadableBirthDates = new AtomicInteger();
        AtomicInteger noBirthDateColumn = new AtomicInteger();
        List<Object[]> rows = client.read("players", "players.csv", REQUIRED_COLUMNS, record -> {
            String gsisId = CsvValues.text(record, "gsis_id", 16);
            if (gsisId == null) {
                return null; // no canonical id, nothing downstream can reference it
            }
            String name = CsvValues.text(record, "display_name", 96);
            if (!record.isMapped("birth_date")) {
                noBirthDateColumn.incrementAndGet();
            }
            LocalDate born = CsvValues.date(record, "birth_date");
            if (born == null && CsvValues.text(record, "birth_date") != null) {
                unreadableBirthDates.incrementAndGet();
            }
            String position = CsvValues.text(record, "position", 4);
            return new Object[] {
                gsisId,
                externalIds(record),
                name != null ? name : gsisId,
                position != null ? position : "UNK",
                CsvValues.text(record, "latest_team", 4),
                CsvValues.text(record, "status", 16),
                born
            };
        });
        if (noBirthDateColumn.get() > 0) {
            log.warn("players.csv has no birth_date column: every age will be empty until it returns");
        } else if (unreadableBirthDates.get() > 0) {
            log.warn("{} birth dates in players.csv did not parse as YYYY-MM-DD and were stored as null",
                    unreadableBirthDates.get());
        }

        jdbc.batchUpdate(UPSERT, rows);
        return IngestResult.of(SOURCE, rows.size(), rows.size());
    }

    private String externalIds(CSVRecord record) {
        Map<String, String> ids = new LinkedHashMap<>();
        EXTERNAL_IDS.forEach((column, key) -> {
            String value = CsvValues.text(record, column);
            if (value != null) {
                ids.put(key, value);
            }
        });
        try {
            return json.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new IngestException("could not serialise external_ids", e);
        }
    }
}
