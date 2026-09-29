package com.fantasykai.ingest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TeamIngestor.class);

    /**
     * The columns a run cannot do without. {@code team_logo_espn} is read but not
     * listed: a logo is display-only, and teams run first -- a rename there would
     * otherwise stop every source after it.
     */
    static final Set<String> REQUIRED_COLUMNS =
            Set.of("team_abbr", "team_name", "team_conf", "team_division");

    private static final String UPSERT = """
            INSERT INTO teams (abbr, name, conference, division, logo_url)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (abbr) DO UPDATE
               SET name = EXCLUDED.name,
                   conference = EXCLUDED.conference,
                   division = EXCLUDED.division,
                   logo_url = EXCLUDED.logo_url
            """;

    private final NflverseClient client;
    private final JdbcTemplate jdbc;

    public TeamIngestor(NflverseClient client, JdbcTemplate jdbc) {
        this.client = client;
        this.jdbc = jdbc;
    }

    public IngestResult ingest() {
        // Optional to the run, not invisible: a logo that is missing or not https
        // is counted and said.
        AtomicInteger noLogo = new AtomicInteger();
        List<Object[]> rows = client.read("teams", "teams_colors_logos.csv", REQUIRED_COLUMNS, record -> {
            String abbr = CsvValues.text(record, "team_abbr", 4);
            String name = CsvValues.text(record, "team_name", 64);
            if (abbr == null || name == null) {
                return null;
            }
            String logo = httpsOnly(CsvValues.text(record, "team_logo_espn"));
            if (logo == null) {
                noLogo.incrementAndGet();
            }
            return new Object[] {
                abbr, name,
                CsvValues.text(record, "team_conf", 4),
                CsvValues.text(record, "team_division", 16),
                logo
            };
        });
        if (noLogo.get() > 0) {
            log.warn("{} of {} teams have no https team_logo_espn and will show no logo",
                    noLogo.get(), rows.size());
        }

        jdbc.batchUpdate(UPSERT, rows);
        return IngestResult.of(SOURCE, rows.size(), rows.size());
    }

    /**
     * The logo ends up in an {@code <img src>}, so anything but an https URL is
     * dropped to null -- the team still loads, it just shows no logo. Every row
     * in the source was https on 2026-09-28; V6's CHECK holds the same line.
     */
    private static String httpsOnly(String url) {
        return url != null && url.startsWith("https://") ? url : null;
    }
}
