package com.fantasykai.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVRecord;
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

    /** nflverse column -> key inside players.external_ids. */
    private static final Map<String, String> EXTERNAL_IDS = Map.of(
            "espn_id", "espn",
            "pfr_id", "pfr",
            "nfl_id", "nfl",
            "esb_id", "esb");

    private static final String UPSERT = """
            INSERT INTO players (gsis_id, external_ids, full_name, position, team_id, status, updated_at)
            VALUES (?, ?::jsonb, ?, ?, (SELECT id FROM teams WHERE abbr = ?), ?, now())
            ON CONFLICT (gsis_id) DO UPDATE
               SET external_ids = EXCLUDED.external_ids,
                   full_name = EXCLUDED.full_name,
                   position = EXCLUDED.position,
                   team_id = EXCLUDED.team_id,
                   status = EXCLUDED.status,
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
        List<Object[]> rows = client.read("players", "players.csv", record -> {
            String gsisId = CsvValues.text(record, "gsis_id", 16);
            if (gsisId == null) {
                return null; // no canonical id, nothing downstream can reference it
            }
            String name = CsvValues.text(record, "display_name", 96);
            String position = CsvValues.text(record, "position", 4);
            return new Object[] {
                gsisId,
                externalIds(record),
                name != null ? name : gsisId,
                position != null ? position : "UNK",
                CsvValues.text(record, "latest_team", 4),
                CsvValues.text(record, "status", 16)
            };
        });

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
