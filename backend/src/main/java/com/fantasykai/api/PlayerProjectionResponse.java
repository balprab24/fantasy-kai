package com.fantasykai.api;

import com.fantasykai.projections.ProjectionSource;

/**
 * One player's projection. {@code state}: {@code PROJECTED} or {@code STALE} with a
 * projection; otherwise {@code UNAVAILABLE}, {@code NO_UPCOMING_WEEK}, {@code BYE},
 * {@code NO_HISTORY} or {@code NOT_ROSTERED}, and none.
 */
public record PlayerProjectionResponse(String state, ProjectionSource source, ProjectionRow projection) {}
