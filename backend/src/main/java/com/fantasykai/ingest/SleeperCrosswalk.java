package com.fantasykai.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Attaches Sleeper player ids to the player master.
 *
 * <p>This cannot come from nflverse: players.csv carries espn, pfr, nfl and esb
 * ids but no Sleeper id. Sleeper's own API returns a gsis_id per player, so the
 * match is made there and written back into players.external_ids.
 *
 * <p>Sleeper is free for non-commercial use only, which this project is. The
 * position filter keeps each response small -- the unfiltered player map is
 * roughly 5MB and is documented as a once-a-day call at most.
 */
@Component
public class SleeperCrosswalk {

    static final String SOURCE = "sleeper.players";

    private static final Logger log = LoggerFactory.getLogger(SleeperCrosswalk.class);

    /** v1 scores these positions; K and DST arrive with the v2 ruleset. */
    private static final List<String> POSITIONS = List.of("QB", "RB", "WR", "TE");

    private static final String ATTACH = """
            UPDATE players
               SET external_ids = external_ids || jsonb_build_object('sleeper', ?),
                   updated_at = now()
             WHERE gsis_id = ?
            """;

    private final HttpClient http;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final IngestProperties props;

    public SleeperCrosswalk(ObjectMapper json, JdbcTemplate jdbc, IngestProperties props) {
        this.json = json;
        this.jdbc = jdbc;
        this.props = props;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public IngestResult ingest() {
        List<Object[]> pairs = new ArrayList<>();
        int read = 0;

        for (String position : POSITIONS) {
            JsonNode players = fetch(position);
            read += players.size();
            for (Iterator<Map.Entry<String, JsonNode>> it = players.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> entry = it.next();
                JsonNode gsis = entry.getValue().get("gsis_id");
                if (gsis == null || gsis.isNull()) {
                    continue; // most inactive Sleeper players have no gsis mapping
                }
                // Sleeper pads the id with a leading space: " 00-0035057".
                String gsisId = gsis.asText().trim();
                if (!gsisId.isEmpty()) {
                    pairs.add(new Object[] {entry.getKey(), gsisId});
                }
            }
        }

        int[] applied = jdbc.batchUpdate(ATTACH, pairs);
        int matched = 0;
        for (int count : applied) {
            matched += Math.max(count, 0);
        }
        log.info("sleeper crosswalk: {} ids with a gsis match, {} attached", pairs.size(), matched);
        return new IngestResult(SOURCE, read, matched, read - matched);
    }

    private JsonNode fetch(String position) {
        URI uri = URI.create("%s/v1/players/nfl?position=%s&active=true"
                .formatted(props.sleeperBaseUrl(), position));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IngestException("GET %s returned %d".formatted(uri, response.statusCode()));
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new IngestException("failed reading " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestException("interrupted reading " + uri, e);
        }
    }
}
