package com.fantasykai.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * One current-season pull, then exit. This is what the daily launchd job runs.
 *
 * <p>Separate from {@link IngestScheduler} on purpose. The scheduler needs a
 * long-lived JVM, which means keeping the app running on a laptop that sleeps;
 * a one-shot process that launchd starts and reaps is the thing that actually
 * accumulates a daily history in {@code ingest_runs}. The same entrypoint
 * becomes the deployed cron command later, so nothing here is throwaway.
 *
 * <p>Exits non-zero on failure so launchd, and anything watching it, can tell a
 * bad run from a quiet one -- {@code ingest_runs} records the detail either way.
 */
@Component
@ConditionalOnProperty(prefix = "fantasykai.ingest", name = "once", havingValue = "true")
public class IngestOnceRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestOnceRunner.class);

    private final IngestService ingestService;
    private final ConfigurableApplicationContext context;

    public IngestOnceRunner(IngestService ingestService, ConfigurableApplicationContext context) {
        this.ingestService = ingestService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code = pull();
        // The context holds a connection pool and, unless disabled, a task
        // scheduler with non-daemon threads; neither lets the JVM fall out of
        // main on its own.
        System.exit(SpringApplication.exit(context, () -> code));
    }

    private int pull() {
        long start = System.currentTimeMillis();
        try {
            ingestService.ingestCurrentSeason();
            log.info("daily pull finished in {} ms", System.currentTimeMillis() - start);
            return 0;
        } catch (RuntimeException e) {
            // Already recorded as FAILED in ingest_runs by IngestService.
            log.error("daily pull failed after {} ms", System.currentTimeMillis() - start, e);
            return 1;
        }
    }
}
