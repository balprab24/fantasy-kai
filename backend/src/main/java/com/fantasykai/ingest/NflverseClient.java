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
import java.util.List;
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
     * @return the mapped rows, in file order
     */
    public <T> List<T> read(String release, String asset, Function<CSVRecord, T> mapper) {
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
            if (response.statusCode() == 404) {
                // nflverse has not published this asset yet -- normal early in a season.
                throw new AssetNotPublishedException("not published yet: " + uri);
            }
            if (response.statusCode() != 200) {
                throw new IngestException("GET %s returned %d".formatted(uri, response.statusCode()));
            }
            try (var reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8);
                    CSVParser parser = CSVFormat.DEFAULT.builder()
                            .setHeader()
                            .setSkipHeaderRecord(true)
                            .get()
                            .parse(reader)) {
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
}
