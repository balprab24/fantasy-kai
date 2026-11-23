package com.fantasykai.api;

import com.fantasykai.projections.ProjectionSource;
import java.util.List;

/**
 * One player's projection. {@code state}: {@code PROJECTED} or {@code STALE} with a
 * projection; otherwise {@code UNAVAILABLE}, {@code NO_UPCOMING_WEEK}, {@code BYE},
 * {@code NO_HISTORY} or {@code NOT_ROSTERED}, and none.
 *
 * @param presets the same stored line scored under the three scoring formats, in id order
 *                (Standard, Half PPR, Full PPR): "same line, different value". Present exactly
 *                when {@code projection} is, and null otherwise. TE Premium, the fourth system
 *                preset, is not listed; the member's own ruleset, whichever it is, is
 *                {@code projection.points}, not an entry here
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
