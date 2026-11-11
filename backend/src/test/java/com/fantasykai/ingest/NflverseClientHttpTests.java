package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * The real {@link NflverseClient} against a local HTTP server, one server per
 * test. {@code IngestionTests} replaces {@code read()} wholesale with fixtures,
 * so until this class nothing exercised the network half of the client at all.
 *
 * <p>The failures reproduced here are the shapes the daily ingest died with on
 * 2026-09-22..24: a stall with no bound, and a reset mid-body that surfaced as
 * commons-csv's {@code UncheckedIOException("closed")} rather than an
 * {@link IngestException}.
 *
 * <p>What this class cannot show: {@code com.sun.net.httpserver} speaks HTTP/1.1
 * only, so production's pooled HTTP/2 connections are not exercised, and no
 * test here puts a Mac to sleep. These tests prove the bound and the error
 * mapping, not the cause.
 */
@Timeout(30) // a backstop: before the fix, a stalled fetch here waited out a 3-minute timeout
class NflverseClientHttpTests {

    /** Short, so a test that should time out does so quickly. */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(1);

    /** Far above {@link #FETCH_TIMEOUT}, far below the 3-minute production value. */
    private static final Duration TEST_LIMIT = Duration.ofSeconds(10);

    private static final Set<String> REQUIRED = Set.of("game_id");

    private final CountDownLatch release = new CountDownLatch(1);
    private final ExecutorService handlers = Executors.newCachedThreadPool();
    private HttpServer server;
    private ServerSocket raw;

    private final Logger clientLog = (Logger) LoggerFactory.getLogger(NflverseClient.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        clientLog.addAppender(logs);
    }

    @AfterEach
    void tearDown() throws IOException {
        clientLog.detachAppender(logs);
        release.countDown();
        if (server != null) {
            server.stop(0);
        }
        if (raw != null) {
            raw.close();
        }
        handlers.shutdownNow();
    }

    // ---- regression guards: these passed before the fix and must keep passing

    @Test
    void readsEveryRowThroughTheReleaseRedirect() throws Exception {
        byte[] games = fixture("games.csv");
        serve("/schedules/games.csv", exchange -> {
            exchange.getResponseHeaders().add("Location", "/mirror/games.csv");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/mirror/games.csv", exchange -> {
            exchange.sendResponseHeaders(200, games.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(games);
            }
        });

        List<String> ids = client().read("schedules", "games.csv", REQUIRED, r -> r.get("game_id"));

        assertThat(ids).isNotEmpty().isEqualTo(parseLocally(games));
    }

    @Test
    void anUnpublishedAssetIsStillSkippedNotFailed() {
        serve("/schedules/games.csv", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        assertThrows(AssetNotPublishedException.class,
                () -> client().read("schedules", "games.csv", REQUIRED, r -> r.get("game_id")));
    }

    // ---- the bound: each of these hung, or threw the wrong type, before the fix

    @Test
    void aBodyThatStallsAfterTheHeadersFailsWithinTheTimeout() throws Exception {
        byte[] games = fixture("games.csv");
        serve("/schedules/games.csv", exchange -> {
            exchange.sendResponseHeaders(200, games.length);
            OutputStream out = exchange.getResponseBody();
            out.write(games, 0, games.length / 2);
            out.flush();
            awaitRelease();
        });

        IngestException e = assertTimeoutPreemptively(TEST_LIMIT, () -> assertThrows(IngestException.class,
                () -> client().read("schedules", "games.csv", REQUIRED, r -> r.get("game_id"))));

        assertThat(e).hasMessageContaining("timed out");
    }

    @Test
    void headersThatNeverArriveFailWithinTheTimeout() {
        serve("/schedules/games.csv", exchange -> awaitRelease());

        assertTimeoutPreemptively(TEST_LIMIT, () -> assertThrows(IngestException.class,
                () -> client().read("schedules", "games.csv", REQUIRED, r -> r.get("game_id"))));
    }

    /**
     * A body that keeps trickling is never "stalled", so only a deadline over the
     * whole fetch catches it. The second half is the leak check: after the
     * client gives up, its next write must fail, which means the timed-out
     * exchange really was cancelled rather than abandoned with its socket open.
     */
    @Test
    void aTricklingBodyIsCutOffAndItsConnectionClosed() throws Exception {
        CountDownLatch serverSawClose = new CountDownLatch(1);
        serve("/schedules/games.csv", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            try {
                out.write("game_id\n".getBytes(StandardCharsets.UTF_8));
                while (release.getCount() > 0) {
                    out.write('x');
                    out.flush();
                    Thread.sleep(50);
                }
            } catch (IOException e) {
                serverSawClose.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertTimeoutPreemptively(TEST_LIMIT, () -> assertThrows(IngestException.class,
                () -> client().read("schedules", "games.csv", REQUIRED, r -> r.get("game_id"))));

        assertThat(serverSawClose.await(5, TimeUnit.SECONDS))
                .as("the server's writes should fail once the client cancels the exchange")
                .isTrue();
    }

    /**
     * 2026-09-24's shape: headers and part of the body arrive, then the peer
     * resets. Before the fix this escaped as commons-csv's
     * {@code UncheckedIOException("closed")}, which is not an IngestException.
     */
    @Test
    void aResetMidBodyIsAnIngestException() throws Exception {
        byte[] games = fixture("games.csv");
        raw = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        handlers.submit(() -> {
            try (Socket socket = raw.accept()) {
                readRequestHead(socket);
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/csv\r\nContent-Length: "
                        + games.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(games, 0, games.length / 2);
                out.flush();
                // Let the client get past the headers and into the body first.
                Thread.sleep(300);
                socket.setSoLinger(true, 0); // close with RST, not FIN
            }
            return null;
        });

        assertTimeoutPreemptively(TEST_LIMIT, () -> assertThrows(IngestException.class,
                () -> client(raw.getLocalPort()).read("schedules", "games.csv", REQUIRED,
                        r -> r.get("game_id"))));
    }

    /**
     * The body now arrives whole, so a reset surfaces from the future, not the
     * parser. The parser can still throw commons-csv's UncheckedIOException on a
     * malformed file -- an unterminated quote -- and that must not escape as
     * anything but an IngestException either.
     */
    @Test
    void aMalformedBodyIsAnIngestException() {
        byte[] body = "game_id\n\"never closed\n".getBytes(StandardCharsets.UTF_8);
        serve("/schedules/games.csv", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        IngestException e = assertThrows(IngestException.class,
                () -> client().read("schedules", "games.csv", REQUIRED, r -> r.get("game_id")));

        assertThat(e).hasMessageContaining("failed parsing");
    }

    // ---- observability

    /**
     * One line per fetch carrying both clocks. The gap between them is time the
     * process spent suspended -- the evidence the 2026-09-22..24 diagnosis had to
     * reconstruct from {@code pmset -g log}.
     */
    @Test
    void everyFetchLogsBothClocksOnSuccessAndOnFailure() throws Exception {
        byte[] games = fixture("games.csv");
        serve("/schedules/games.csv", exchange -> {
            exchange.sendResponseHeaders(200, games.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(games);
            }
        });
        server.createContext("/schedules/stalls.csv", exchange -> awaitRelease());

        client().read("schedules", "games.csv", REQUIRED, r -> r.get("game_id"));
        assertThrows(IngestException.class,
                () -> client().read("schedules", "stalls.csv", REQUIRED, r -> r.get("game_id")));

        List<String> ok = messages(Level.INFO, " ok ");
        assertThat(ok).hasSize(1);
        assertThat(ok.getFirst()).contains("bytes=" + games.length, "monotonic_ms=", "wall_ms=");

        List<String> failed = messages(Level.WARN, " failed ");
        assertThat(failed).hasSize(1);
        assertThat(failed.getFirst()).contains("stalls.csv", "monotonic_ms=", "wall_ms=");
    }

    // ---- helpers

    private void serve(String path, HttpHandler handler) {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // Handlers block on purpose; the default executor runs them on the
        // dispatcher thread, where one stalled handler would stall the server.
        server.setExecutor(handlers);
        server.createContext(path, handler);
        server.start();
    }

    private NflverseClient client() {
        return client(server.getAddress().getPort());
    }

    private NflverseClient client(int port) {
        return new NflverseClient(new IngestProperties("http://127.0.0.1:" + port, null, 2020,
                "-", false, false, false, FETCH_TIMEOUT));
    }

    private void awaitRelease() {
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> messages(Level level, String marker) {
        return logs.list.stream()
                .filter(e -> e.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains(marker))
                .toList();
    }

    private static byte[] fixture(String asset) throws IOException {
        return new ClassPathResource("nflverse/" + asset).getInputStream().readAllBytes();
    }

    private static List<String> parseLocally(byte[] csv) throws IOException {
        try (var reader = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            List<String> ids = new ArrayList<>();
            for (CSVRecord record : parser) {
                ids.add(record.get("game_id"));
            }
            return ids;
        }
    }

    /** Consumes the request line and headers, up to the blank line. */
    private static void readRequestHead(Socket socket) throws IOException {
        var in = socket.getInputStream();
        byte[] tail = new byte[4];
        int b;
        while ((b = in.read()) != -1) {
            System.arraycopy(tail, 1, tail, 0, 3);
            tail[3] = (byte) b;
            if (Arrays.equals(tail, new byte[] {'\r', '\n', '\r', '\n'})) {
                return;
            }
        }
    }
}
