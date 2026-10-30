package com.fantasykai.leagues;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The one way league code reaches another server.
 *
 * <p>Every request URL is built from configuration plus a validated id, but a
 * fetch that can be pointed somewhere is a server-side request forgery waiting
 * for its first bug. So the hosts are an allowlist in code -- not
 * configuration, which is one environment variable from being anything --
 * redirects are refused rather than followed, and a body past the cap is an
 * error rather than a truncation that parses as something else.
 *
 * <p>Logs a host and a status, never a URL or a header: a URL carries a
 * member's username, and from S4 a header carries an ESPN cookie.
 */
@Component
public class LeagueHttp {

    /** Plan S1. ESPN and Yahoo hosts are listed now so S4/S5 add a provider, not a hole. */
    static final Set<String> ALLOWED_HOSTS = Set.of(
            "api.sleeper.app",
            "lm-api-reads.fantasy.espn.com",
            "fantasysports.yahooapis.com",
            "api.login.yahoo.com");

    private static final Logger log = LoggerFactory.getLogger(LeagueHttp.class);

    private final ObjectMapper json;
    private final LeagueProperties props;
    private final Set<String> allowedHosts;
    private final boolean httpsOnly;
    private final HttpClient http;

    @Autowired
    public LeagueHttp(ObjectMapper json, LeagueProperties props) {
        this(json, props, ALLOWED_HOSTS, true);
    }

    /** Tests only: a loopback server needs its own host and plain HTTP. */
    LeagueHttp(ObjectMapper json, LeagueProperties props, Set<String> allowedHosts, boolean httpsOnly) {
        this.json = json;
        this.props = props;
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.httpsOnly = httpsOnly;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(props.timeout())
                .build();
    }

    /**
     * GET a JSON document.
     *
     * @return {@link NullNode} for a 404, an empty body or a literal {@code null} -- Sleeper
     *     answers an unknown user with {@code 200 null} -- so "not there" has one shape
     */
    public JsonNode get(URI uri) {
        if (uri.getHost() == null || !allowedHosts.contains(uri.getHost())) {
            throw new IllegalArgumentException("host not on the league allowlist: " + uri.getHost());
        }
        if (httpsOnly && !"https".equals(uri.getScheme())) {
            throw new IllegalArgumentException("league requests are https only");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(props.timeout())
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status == 404) {
                    return NullNode.getInstance();
                }
                if (status == 429 || status >= 500) {
                    log.warn("league request to {} returned {}", uri.getHost(), status);
                    throw new LeagueUnavailableException("The league platform is not answering right now.");
                }
                if (status != 200) {
                    // 3xx lands here too: a redirect is refused, not followed.
                    log.warn("league request to {} returned {}", uri.getHost(), status);
                    throw new LeagueDataException("The league platform answered " + status + ".");
                }
                byte[] bytes = body.readNBytes(props.maxBodyBytes() + 1);
                if (bytes.length > props.maxBodyBytes()) {
                    throw new LeagueDataException("The league platform's response was too large.");
                }
                JsonNode node = json.readTree(bytes);
                return node == null || node.isMissingNode() ? NullNode.getInstance() : node;
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new LeagueDataException("The league platform's response was not JSON.", e);
        } catch (IOException e) {
            // Includes HttpTimeoutException and a refused connection.
            log.warn("league request to {} failed: {}", uri.getHost(), e.getClass().getSimpleName());
            throw new LeagueUnavailableException("The league platform is not answering right now.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LeagueUnavailableException("Interrupted while reaching the league platform.", e);
        }
    }
}
