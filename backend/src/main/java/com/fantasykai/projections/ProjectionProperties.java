package com.fantasykai.projections;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which projection method runs, and whether the daily job runs it.
 *
 * @param provider   a {@link ProjectionSource#id()}; {@code weighted-average-v1} is the only one
 * @param jobEnabled whether the daily pull runs {@link ProjectionJob} after the ingest. Off by
 *                   default (owner decision 2026-10-12), and turned on only deliberately: a row
 *                   the job writes is locked for good at kickoff, so production needs its one-time
 *                   backfill first ({@code DEPLOY-STEPS.md}). Since Slice 4 the job projects from
 *                   weekly rosters, not the interim {@code players.team_id}.
 */
@ConfigurationProperties(prefix = "fantasykai.projections")
public record ProjectionProperties(
        @DefaultValue("weighted-average-v1") String provider,
        @DefaultValue("false") boolean jobEnabled) {}
