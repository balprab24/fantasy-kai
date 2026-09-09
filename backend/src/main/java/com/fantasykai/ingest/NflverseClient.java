package com.fantasykai.ingest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches CSV assets from nflverse GitHub releases. No auth, no rate limit --
 * the data is CC BY 4.0 and published as plain files, which is the whole reason
 * it is the backbone rather than a scraper.
 */
@Component
public class NflverseClient {

    private static final Logger log = LoggerFactory.getLogger(NflverseClient.class);

    private final HttpClient http;
    private final IngestProperties props;

    public NflverseClient(IngestProperties props) {
        this.props = props;
        // GitHub redirects release downloads to objects.githubusercontent.com.
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * Streams one release asset, mapping each row. A mapper may return null to
     * skip a row -- nflverse ships a small number of rows with no player id.
     *
     * @param required source columns that must be present in the header. A
     *                 caller passes the columns it reads; see the class note on
     *                 why an absent one has to fail rather than default
     * @return the mapped rows, in file order
     */
    public <T> List<T> read(String release, String asset, Set<String> required,
            Function<CSVRecord, T> mapper) {
        URI uri = URI.create("%s/%s/%s".formatted(props.baseUrl(), release, asset));
        log.info("nflverse fetch {}", uri);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(3))
                .header("Accept", "text/csv")
                .GET()
                .build();

        try {
            HttpResponse<java.io.InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            // Close the body on every non-200 path. ofInputStream hands back an
            // open stream regardless of status, and the 404 branch runs on every
            // off-season day -- it used to throw before reaching the
            // try-with-resources below and leak a connection each time.
            if (response.statusCode() != 200) {
                response.body().close();
                if (response.statusCode() == 404) {
                    // nflverse has not published this asset yet -- normal early in a season.
                    throw new AssetNotPublishedException("not published yet: " + uri);
                }
                throw new IngestException("GET %s returned %d".formatted(uri, response.statusCode()));
            }
            try (var reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8);
                    CSVParser parser = CSVFormat.DEFAULT.builder()
                            .setHeader()
                            .setSkipHeaderRecord(true)
                            .get()
                            .parse(reader)) {
                verifyHeader(uri, parser.getHeaderMap().keySet(), required);
                List<T> mapped = new ArrayList<>();
                for (CSVRecord record : parser) {
                    T value = mapper.apply(record);
                    if (value != null) {
                        mapped.add(value);
                    }
                }
                return mapped;
            }
        } catch (IOException e) {
            throw new IngestException("failed reading " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestException("interrupted reading " + uri, e);
        }
    }

    /**
     * Fails the run when a column an ingestor reads is not in the file.
     *
     * <p>This is the loud half of a silent failure. {@code CsvValues} tolerates
     * an absent column by design -- {@code shortValue} maps it to 0, which is
     * the right answer for "did not record this" in a box score. But it is the
     * wrong answer for "upstream renamed the column": every row parses, the row
     * counts still match, {@code IntegrityChecks} only compares season and week,
     * and the run reports SUCCESS with a stat quietly zeroed for a whole season.
     * You would find out from a user.
     *
     * <p>Safe as a hard failure because it was measured, not assumed: the
     * headers of {@code stats_player_week} (150 columns) and {@code snap_counts}
     * (16) are byte-identical across all six loaded seasons, 2020-2025.
     */
    static void verifyHeader(URI uri, Set<String> header, Set<String> required) {
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(header);
        if (!missing.isEmpty()) {
            throw new IngestException(
                    "%s is missing %d column(s) this ingestor reads: %s".formatted(
                            uri, missing.size(), String.join(", ", missing)));
        }
    }
}
