package com.fantasykai.ingest;

import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily in-season pull.
 *
 * <p>Daily rather than weekly on purpose: nflverse revises the current week
 * mid-week as corrections land, the upsert is idempotent, and a day-old number
 * is the difference between a useful waiver view and a stale one.
 */
@Component
@ConditionalOnProperty(prefix = "fantasykai.ingest", name = "scheduled-enabled", havingValue = "true")
public class IngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestScheduler.class);

    private final IngestService ingestService;
    private final Clock clock;

    public IngestScheduler(IngestService ingestService, Clock clock) {
        this.ingestService = ingestService;
        this.clock = clock;
    }

    @Scheduled(cron = "${fantasykai.ingest.cron}", zone = "America/New_York")
    public void pullCurrentSeason() {
        LocalDate today = LocalDate.now(clock);
        if (!IngestProperties.inSeason(today)) {
            log.debug("out of season on {}, skipping the daily pull", today);
            return;
        }
        ingestService.ingestCurrentSeason();
    }
}
