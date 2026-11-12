package com.fantasykai.ingest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
        // Absent would mean "wait forever" -- the failure this bound exists to end.
        Objects.requireNonNull(props.fetchTimeout(), "fantasykai.ingest.fetch-timeout is not set");
        this.props = props;
        // GitHub redirects release downloads to objects.githubusercontent.com.
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * Downloads one release asset, then maps each row. A mapper may return null
     * to skip a row -- nflverse ships a small number of rows with no player id.
     *
     * <p><b>The whole download is bounded, body included.</b> This used to be
     * {@code send(ofInputStream())} with a request timeout, and a request timeout
     * only covers the wait for response headers: a body that stalled, or
     * trickled, was read forever. Now the body is buffered ({@code ofByteArray})
     * and the future that delivers it is awaited for {@code fetchTimeout}. The
     * largest asset is 8.7 MB (measured 2026-09-24), which is small next to the
     * mapped rows this method already holds.
     *
     * <p><b>The deadline is monotonic on purpose.</b> {@code Future.get} counts
     * {@code System.nanoTime}, which on macOS does not advance while the machine
     * sleeps, so a Mac that sleeps mid-fetch does not fail the fetch by sleeping.
     * If the connection died while it slept, the fetch fails on wake anyway, as
     * an IOException. The daily ingest of 2026-09-22..24 died exactly that way,
     * inside the brief Power Nap wake that launchd started it in.
     *
     * <p>Every fetch logs one line with both clocks. {@code wall_ms} minus
     * {@code monotonic_ms} is roughly how long the process was suspended, which
     * is what tells "the Mac slept" apart from "the server failed".
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
                .header("Accept", "text/csv")
                .GET()
                .build();

        long startNanos = System.nanoTime();
        long startMillis = System.currentTimeMillis();
        CompletableFuture<HttpResponse<byte[]>> pending =
                http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            HttpResponse<byte[]> response;
            try {
                response = pending.get(props.fetchTimeout().toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                // cancel(true) aborts the exchange and closes its connection;
                // NflverseClientHttpTests proves the server sees the close.
                pending.cancel(true);
                throw new IngestException("GET %s timed out after %s".formatted(uri, props.fetchTimeout()), e);
            } catch (ExecutionException e) {
                throw new IngestException("failed reading " + uri, e.getCause());
            }
            if (response.statusCode() != 200) {
                if (response.statusCode() == 404) {
                    // nflverse has not published this asset yet -- normal early in a season.
                    throw new AssetNotPublishedException("not published yet: " + uri);
                }
                throw new IngestException("GET %s returned %d".formatted(uri, response.statusCode()));
            }
            byte[] body = response.body();
            List<T> mapped = parse(uri, body, required, mapper);
            log.info("nflverse fetch {} ok status=200 bytes={} rows={} monotonic_ms={} wall_ms={}",
                    uri, body.length, mapped.size(), elapsedNanosAsMillis(startNanos),
                    System.currentTimeMillis() - startMillis);
            return mapped;
        } catch (AssetNotPublishedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("nflverse fetch {} failed ({}) monotonic_ms={} wall_ms={}",
                    uri, e.getCause() != null ? e.getCause() : e, elapsedNanosAsMillis(startNanos),
                    System.currentTimeMillis() - startMillis);
            throw e;
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new IngestException("interrupted reading " + uri, e);
        }
    }

    private static <T> List<T> parse(URI uri, byte[] body, Set<String> required,
            Function<CSVRecord, T> mapper) {
        try (var reader = new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8);
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
        } catch (IOException | UncheckedIOException e) {
            // commons-csv's record iterator wraps IOException as UncheckedIOException;
            // from a byte array only a malformed file can raise it, but it is still
            // a failed read, and callers only handle IngestException.
            throw new IngestException("failed parsing " + uri, e);
        }
    }

    private static long elapsedNanosAsMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
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
