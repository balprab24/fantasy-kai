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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Attaches independently matched Sleeper and Yahoo identities during the daily ingest.
 *
 * <p>Sleeper is free for non-commercial use only, which this project is, and its
 * full player map is documented as a once-a-day call at most: one fetch per ingest.
 */
@Component
public class SleeperCrosswalk {

    static final String SOURCE = "sleeper.players";

    private static final Logger log = LoggerFactory.getLogger(SleeperCrosswalk.class);

    private static final String ATTACH = """
            UPDATE players
               SET external_ids = external_ids || jsonb_build_object('sleeper', CAST(? AS text))
                   || CASE WHEN CAST(? AS text) IS NULL THEN '{}'::jsonb
                           ELSE jsonb_build_object('yahoo', CAST(? AS text)) END,
                   updated_at = now()
             WHERE id = ?
               AND (external_ids->>'sleeper' IS NULL OR external_ids->>'sleeper' = ?)
            """;

    private final HttpClient http;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final IngestProperties props;
    private final TransactionTemplate transaction;

    public SleeperCrosswalk(ObjectMapper json, JdbcTemplate jdbc, IngestProperties props,
            PlatformTransactionManager transactions) {
        this.json = json;
        this.jdbc = jdbc;
        this.props = props;
        this.transaction = new TransactionTemplate(transactions);
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public IngestResult ingest() {
        JsonNode snapshot = fetch();
        return transaction.execute(status -> attach(snapshot));
    }

    /** Package-visible for fixture tests; caller supplies the transaction. */
    IngestResult attach(JsonNode snapshot) {
        if (!snapshot.isObject()) throw new IngestException("Sleeper player response is not an object");
        List<PlayerMatcher.Player> players = jdbc.query(
                "SELECT id, gsis_id, full_name, position, birth_date, external_ids::text FROM players",
                (rs, n) -> {
                    Map<String, String> ids = new HashMap<>();
                    try {
                        json.readTree(rs.getString("external_ids")).fields().forEachRemaining(entry -> {
                            if (!entry.getValue().isNull()) ids.put(entry.getKey(), entry.getValue().asText());
                        });
                    } catch (IOException e) {
                        throw new IngestException("Invalid stored player identities", e);
                    }
                    return new PlayerMatcher.Player(rs.getLong("id"), rs.getString("gsis_id"),
                            rs.getString("full_name"), rs.getString("position"),
                            rs.getObject("birth_date", LocalDate.class), ids);
                });
        // Stable GSIS keys make the reviewed exceptions portable across databases.
        seedOverride("11068", "00-0038839", "Mitchell/Mitch Tinsley; WR and birth date agree");
        seedOverride("8122", "00-0037157", "Bam/Zonovan Knight; RB and birth date agree");
        seedOverride("8489", "00-0037292", "Andrew/Drew Ogletree; TE and birth date agree");
        seedOverride("13324", "00-0040879", "Matthew/Matt Hibner; TE and birth date agree");
        Map<String, Long> overrides = new HashMap<>();
        jdbc.query("SELECT platform, external_id, player_id FROM player_id_overrides", rs -> {
            overrides.put(rs.getString("platform") + ":" + rs.getString("external_id"), rs.getLong("player_id"));
        });
        PlayerMatcher matcher = new PlayerMatcher(players, overrides);
        Map<Long, List<Map.Entry<String, JsonNode>>> matches = new HashMap<>();
        snapshot.fields().forEachRemaining(entry -> {
            JsonNode data = entry.getValue();
            String name = value(data, "full_name");
            if (name == null) name = (data.path("first_name").asText("") + " "
                    + data.path("last_name").asText("")).trim();
            var hit = matcher.match(new PlayerMatcher.Identity("sleeper", entry.getKey(),
                    value(data, "gsis_id"), value(data, "espn_id"), name,
                    value(data, "position"), date(value(data, "birth_date"))));
            if (hit.isPresent()) matches.computeIfAbsent(hit.getAsLong(), ignored -> new ArrayList<>()).add(entry);
        });
        Map<String, Set<Long>> yahooOwners = new HashMap<>();
        for (var player : players) {
            String yahoo = player.externalIds().get("yahoo");
            if (yahoo != null) yahooOwners.computeIfAbsent(yahoo, ignored -> new HashSet<>()).add(player.id());
        }
        matches.forEach((id, entries) -> {
            if (entries.size() == 1) {
                String yahoo = value(entries.getFirst().getValue(), "yahoo_id");
                if (yahoo != null) yahooOwners.computeIfAbsent(yahoo, ignored -> new HashSet<>()).add(id);
            }
        });
        Map<Long, PlayerMatcher.Player> byId = new HashMap<>();
        players.forEach(player -> byId.put(player.id(), player));
        List<Object[]> updates = new ArrayList<>();
        matches.forEach((id, entries) -> {
            // Never let iteration order choose between two provider records for one player.
            if (entries.size() != 1) return;
            var entry = entries.getFirst();
            String yahoo = value(entry.getValue(), "yahoo_id");
            String storedYahoo = byId.get(id).externalIds().get("yahoo");
            if (yahoo != null && (yahooOwners.get(yahoo).size() != 1
                    || storedYahoo != null && !storedYahoo.equals(yahoo))) yahoo = null;
            updates.add(new Object[] {entry.getKey(), yahoo, yahoo, id, entry.getKey()});
        });
        int matched = 0;
        for (int count : jdbc.batchUpdate(ATTACH, updates)) matched += Math.max(count, 0);
        log.info("sleeper crosswalk: {} read, {} attached", snapshot.size(), matched);
        return new IngestResult(SOURCE, snapshot.size(), matched, snapshot.size() - matched);
    }

    private void seedOverride(String sleeper, String gsis, String reason) {
        jdbc.update("""
                INSERT INTO player_id_overrides (platform, external_id, player_id, reason)
                SELECT 'sleeper', ?, id, ? FROM players WHERE gsis_id = ?
                ON CONFLICT DO NOTHING
                """, sleeper, reason, gsis);
    }

    private static String value(JsonNode data, String key) {
        JsonNode value = data.get(key);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException e) { return null; }
    }

    private JsonNode fetch() {
        URI uri = URI.create("%s/v1/players/nfl".formatted(props.sleeperBaseUrl()));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IngestException("GET %s returned %d".formatted(uri, response.statusCode()));
                }
                byte[] bytes = body.readNBytes(32 * 1024 * 1024 + 1);
                if (bytes.length > 32 * 1024 * 1024) {
                    throw new IngestException("Sleeper player response exceeds 32 MiB");
                }
                return json.readTree(bytes);
            }
        } catch (IOException e) {
            throw new IngestException("failed reading " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestException("interrupted reading " + uri, e);
        }
    }
}
