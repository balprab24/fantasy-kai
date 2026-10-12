package com.fantasykai.projections;

import java.util.Optional;

/**
 * What one run of {@link ProjectionJob} did. Counts, never points.
 *
 * @param season     the season it looked at
 * @param week       the week it projected; empty when the season has none left to project
 * @param candidates players it considered for the week's open games
 * @param written    rows it wrote -- candidates with a history and no locked row this week
 * @param locked     rows it locked because their game had kicked off
 * @param withdrawn  open rows it removed because their game left the schedule
 */
public record ProjectionRun(int season, Optional<SeasonWeek> week, int candidates, int written,
        int locked, int withdrawn) {}
