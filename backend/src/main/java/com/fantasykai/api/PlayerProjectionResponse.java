package com.fantasykai.api;

import com.fantasykai.projections.ProjectionSource;
import java.util.List;

/**
 * One player's projection. {@code state}: {@code PROJECTED} or {@code STALE} with a
 * projection; otherwise {@code UNAVAILABLE}, {@code NO_UPCOMING_WEEK}, {@code BYE},
 * {@code NO_HISTORY} or {@code NOT_ROSTERED}, and none.
 *
 * @param presets the same stored line scored under each of the system presets, in id order
 *                (Standard, Half PPR, Full PPR, TE Premium): "same line, different value". Present
 *                exactly when {@code projection} is, and null otherwise. The member's own ruleset
 *                is {@code projection.points}, not an entry here
 */
public record PlayerProjectionResponse(String state, ProjectionSource source, ProjectionRow projection,
        List<PresetPoints> presets) {

    /**
     * The line's points under one preset, rounded once.
     *
     * @param profileId the preset's profile id, as {@code /scoring-profiles} lists it
     * @param name      its name as stored
     */
    public record PresetPoints(long profileId, String name, double points) {}
}
