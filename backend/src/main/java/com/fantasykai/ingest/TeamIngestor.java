package com.fantasykai.ingest;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seeds the 32 current franchises plus their historical abbreviations.
 *
 * <p>The alias rows matter: nflverse writes the Rams as {@code LA} in 2020-2025
 * stat lines, not {@code LAR}, and both appear in the source file. Loading every
 * row means a team lookup never misses on a relocation or a rebrand.
 */
@Component
public class TeamIngestor {

    static final String SOURCE = "nflverse.teams";

    private static final String UPSERT = """
            INSERT INTO teams (abbr, name, conference, division)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (abbr) DO UPDATE
               SET name = EXCLUDED.name,
                   conference = EXCLUDED.conference,
                   division = EXCLUDED.division
            """;

    private final NflverseClient client;
    private final JdbcTemplate jdbc;

    public TeamIngestor(NflverseClient client, JdbcTemplate jdbc) {
        this.client = client;
        this.jdbc = jdbc;
    }

    public IngestResult ingest() {
        List<Object[]> rows = client.read("teams", "teams_colors_logos.csv", record -> {
            String abbr = CsvValues.text(record, "team_abbr", 4);
            String name = CsvValues.text(record, "team_name", 64);
            if (abbr == null || name == null) {
                return null;
            }
            return new Object[] {
                abbr, name,
                CsvValues.text(record, "team_conf", 4),
                CsvValues.text(record, "team_division", 16)
            };
        });

        jdbc.batchUpdate(UPSERT, rows);
        return IngestResult.of(SOURCE, rows.size(), rows.size());
    }
}
