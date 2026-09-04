package com.fantasykai.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-shot historical load, run with:
 *
 * <pre>./mvnw spring-boot:run -Dspring-boot.run.arguments=--fantasykai.ingest.backfill-on-startup=true</pre>
 *
 * <p>Deliberately a startup flag rather than an HTTP endpoint: there is no auth
 * until Phase 5, and an unauthenticated route that can hammer an upstream and
 * rewrite the stats table is not something to leave lying around.
 */
@Component
@ConditionalOnProperty(prefix = "fantasykai.ingest", name = "backfill-on-startup", havingValue = "true")
public class BackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillRunner.class);

    private final IngestService ingestService;

    public BackfillRunner(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        ingestService.backfill();
        log.info("backfill finished in {} ms", System.currentTimeMillis() - start);
    }
}
