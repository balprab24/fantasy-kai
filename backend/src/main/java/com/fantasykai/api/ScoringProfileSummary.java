package com.fantasykai.api;

/**
 * {@code /scoring-profiles}. Phase 3 serves the four seeded presets only; the
 * caller's own profiles arrive with auth in Phase 5, filtered on the JWT subject
 * in the repository query rather than the service layer (§8).
 */
public record ScoringProfileSummary(long id, String name, boolean preset) {}
