package com.fantasykai.leagues;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sleeper, served from {@code src/test/resources/sleeper/}: the real provider,
 * mapper and service run against it, and nothing leaves the machine. A path
 * with no fixture answers as Sleeper's 404 does -- {@link NullNode}.
 */
class FixtureLeagueHttp extends LeagueHttp {

    static final String OWNER = "200000000000000001";
    static final String LEAGUE = "100000000000000001";
    static final String USERNAME = "fk_fixture_owner";

    static final LeagueProperties PROPS = new LeagueProperties("https://api.sleeper.app", 600,
            Duration.ofMinutes(5), Duration.ofSeconds(10), 2_097_152, false);

    private final ObjectMapper json;
    private final Map<String, JsonNode> overrides = new HashMap<>();
    final List<String> requested = new ArrayList<>();

    FixtureLeagueHttp(ObjectMapper json) {
        super(json, PROPS, Set.of("api.sleeper.app"), true);
        this.json = json;
    }

    /** Replaces one path's answer, e.g. a roster that changed before a sync. */
    void answer(String path, JsonNode body) {
        overrides.put(path, body);
    }

    void reset() {
        overrides.clear();
        requested.clear();
    }

    JsonNode fixture(String name) {
        try (InputStream in = getClass().getResourceAsStream("/sleeper/" + name)) {
            return json.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public JsonNode get(URI uri) {
        String path = uri.getPath();
        requested.add(path);
        if (overrides.containsKey(path)) {
            return overrides.get(path);
        }
        String league = "/v1/league/" + LEAGUE;
        String file = switch (path) {
            case "/v1/user/" + USERNAME -> "user.json";
            case "/v1/user/" + OWNER + "/leagues/nfl/2026" -> "user_leagues.json";
            case "/v1/state/nfl" -> "state.json";
            default -> path.equals(league) ? "league.json"
                    : path.equals(league + "/users") ? "users.json"
                    : path.equals(league + "/rosters") ? "rosters.json"
                    : path.startsWith(league + "/matchups/")
                            ? "matchups_" + path.substring((league + "/matchups/").length()) + ".json"
                    : null;
        };
        if (file == null || getClass().getResource("/sleeper/" + file) == null) {
            return NullNode.getInstance();
        }
        return fixture(file);
    }
}
