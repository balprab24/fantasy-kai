package com.fantasykai.leagues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The one outbound client, against a real loopback server: every refusal is tried, not read. */
class LeagueHttpTests {

    private static final int CAP = 1024;
    private final List<String> served = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private LeagueHttp http;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/ok", 200, "{\"a\":1}");
        respond("/null", 200, "null");
        respond("/empty", 200, "");
        respond("/missing", 404, "{}");
        respond("/busy", 429, "{}");
        respond("/broken", 500, "{}");
        respond("/forbidden", 403, "{}");
        respond("/notjson", 200, "<html>");
        respond("/huge", 200, "[" + "1,".repeat(CAP) + "1]");
        respond("/secret", 200, "{\"stolen\":true}");
        server.createContext("/redirect", exchange -> {
            served.add("/redirect");
            exchange.getResponseHeaders().add("Location", base + "/secret");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            served.add("/slow");
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        http = new LeagueHttp(new ObjectMapper(), new LeagueProperties(base, 600, Duration.ofMinutes(5),
                Duration.ofMillis(500), CAP, false), Set.of("127.0.0.1"), false);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsJson() {
        assertThat(http.get(URI.create(base + "/ok")).path("a").asInt()).isEqualTo(1);
    }

    /** Sleeper's unknown user is {@code 200 null}; a 404 and an empty body mean the same: not there. */
    @Test
    void notThereHasOneShape() {
        assertThat(http.get(URI.create(base + "/null")).isNull()).isTrue();
        assertThat(http.get(URI.create(base + "/empty")).isNull()).isTrue();
        assertThat(http.get(URI.create(base + "/missing")).isNull()).isTrue();
    }

    @Test
    void aHostOffTheAllowlistIsNeverContacted() {
        assertThatThrownBy(() -> http.get(URI.create("http://localhost:" + server.getAddress().getPort() + "/ok")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("allowlist");
        assertThatThrownBy(() -> http.get(URI.create("http://169.254.169.254/latest/meta-data")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(served).isEmpty();
    }

    /** The production allowlist: four hosts, https only, and no way to add one by configuration. */
    @Test
    void productionIsHttpsToFourHosts() {
        LeagueHttp production = new LeagueHttp(new ObjectMapper(), FixtureLeagueHttp.PROPS);

        assertThat(LeagueHttp.ALLOWED_HOSTS).containsExactlyInAnyOrder("api.sleeper.app",
                "lm-api-reads.fantasy.espn.com", "fantasysports.yahooapis.com", "api.login.yahoo.com");
        assertThatThrownBy(() -> production.get(URI.create("http://api.sleeper.app/v1/state/nfl")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("https");
        assertThatThrownBy(() -> production.get(URI.create(base + "/ok")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(served).isEmpty();
    }

    /** A redirect would take the request wherever the far side says. It is refused, not followed. */
    @Test
    void aRedirectIsRefusedAndNotFollowed() {
        assertThatThrownBy(() -> http.get(URI.create(base + "/redirect")))
                .isInstanceOf(LeagueDataException.class).hasMessageContaining("302");
        assertThat(served).containsExactly("/redirect");
    }

    @Test
    void aBodyPastTheCapIsRefusedNotTruncated() {
        assertThatThrownBy(() -> http.get(URI.create(base + "/huge")))
                .isInstanceOf(LeagueDataException.class).hasMessageContaining("too large");
    }

    @Test
    void anOverloadedOrBrokenPlatformIsUnavailable_andAnyOtherStatusIsBadData() {
        assertThatThrownBy(() -> http.get(URI.create(base + "/busy"))).isInstanceOf(LeagueUnavailableException.class);
        assertThatThrownBy(() -> http.get(URI.create(base + "/broken"))).isInstanceOf(LeagueUnavailableException.class);
        assertThatThrownBy(() -> http.get(URI.create(base + "/forbidden"))).isInstanceOf(LeagueDataException.class);
        assertThatThrownBy(() -> http.get(URI.create(base + "/notjson"))).isInstanceOf(LeagueDataException.class);
    }

    @Test
    void aSlowPlatformTimesOut() {
        assertThatThrownBy(() -> http.get(URI.create(base + "/slow"))).isInstanceOf(LeagueUnavailableException.class);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            served.add(path);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
    }
}
