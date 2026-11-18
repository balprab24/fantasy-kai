package com.fantasykai.api;

import com.fantasykai.projections.ProjectionSource;

/**
 * The member's projection board. {@code state}: {@code READY}; {@code STALE} (the rows are for an
 * earlier week than the one being played, or every game of theirs is over 48 hours old);
 * {@code UNAVAILABLE} (a week to project, nothing stored for it); or {@code NO_UPCOMING_WEEK}
 * (the schedule has none left, and no rows are served).
 */
public record ProjectionBoard(String state, ProjectionSource source, PageResponse<ProjectionRow> page) {}
