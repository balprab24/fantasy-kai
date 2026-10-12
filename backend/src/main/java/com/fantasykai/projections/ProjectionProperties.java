package com.fantasykai.projections;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which projection method runs, and whether the daily job runs it.
 *
 * @param provider   a {@link ProjectionSource#id()}; {@code weighted-average-v1} is the only one
 * @param jobEnabled whether the daily pull runs {@link ProjectionJob} after the ingest. Off by
 *                   default until Slice 4 gives the job real eligibility: until then a player's
 *                   week-W team is {@code players.team_id}, which retired and released players
 *                   keep, and a row the job writes is locked for good at kickoff. Owner decision
 *                   2026-10-12.
 */
@ConfigurationProperties(prefix = "fantasykai.projections")
public record ProjectionProperties(
        @DefaultValue("weighted-average-v1") String provider,
        @DefaultValue("false") boolean jobEnabled) {}
