package com.fantasykai.api;

import com.fantasykai.projections.ProjectionSource;

/**
 * The member's projection board. {@code state}: {@code READY}, {@code STALE} or
 * {@code UNAVAILABLE}.
 */
public record ProjectionBoard(String state, ProjectionSource source, PageResponse<ProjectionRow> page) {}
