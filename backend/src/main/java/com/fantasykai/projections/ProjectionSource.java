package com.fantasykai.projections;

import java.util.regex.Pattern;

/**
 * Which method produced a projection, stated so that it stays true.
 *
 * <p>{@code id} is what Slice 3 persists beside every projected line. It names an algorithm and
 * a version, never a Java class, and it never changes meaning: a different weighting, window or
 * input is a new id ({@code -v2}), so a stored projection keeps saying exactly how it was made.
 *
 * @param id          stable and versioned, e.g. {@code weighted-average-v1}
 * @param label       what a member sees
 * @param description one plain sentence on the method -- no "AI", no "model" for a baseline
 */
public record ProjectionSource(String id, String label, String description) {

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+");

    public ProjectionSource {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("a source id is lowercase words and a version, "
                    + "like weighted-average-v1; got " + id);
        }
        if (label == null || label.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("a source needs a label and a description");
        }
    }
}
