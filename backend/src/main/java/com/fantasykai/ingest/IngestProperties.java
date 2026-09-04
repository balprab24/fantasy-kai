package com.fantasykai.ingest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ingestion configuration; defaults live in application.yml.
 *
 * @param baseUrl           nflverse release download root
 * @param sleeperBaseUrl    Sleeper API root
 * @param firstSeason       earliest season a backfill reaches
 * @param cron              schedule for the in-season pull
 * @param scheduledEnabled  master switch for the scheduled job (off in tests)
 * @param backfillOnStartup runs a full backfill once at boot, for the initial load
 * @param once              pulls the current season once, then exits; the daily launchd job
 */
@ConfigurationProperties(prefix = "fantasykai.ingest")
public record IngestProperties(
        String baseUrl,
        String sleeperBaseUrl,
        int firstSeason,
        String cron,
        boolean scheduledEnabled,
        boolean backfillOnStartup,
        boolean once) {

    /** September through February. Outside this the scheduled pull is a no-op. */
    private static final Set<Month> IN_SEASON = EnumSet.of(
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER,
            Month.JANUARY, Month.FEBRUARY);

    /**
     * The NFL season a date belongs to. A season is named for the calendar year it
     * starts in, so January and February belong to the previous season -- the 2025
     * season's playoffs are played in 2026.
     */
    public static int seasonFor(LocalDate date) {
        return date.getMonthValue() >= 3 ? date.getYear() : date.getYear() - 1;
    }

    public static boolean inSeason(LocalDate date) {
        return IN_SEASON.contains(date.getMonth());
    }

    public int currentSeason(Clock clock) {
        return seasonFor(LocalDate.now(clock));
    }
}
