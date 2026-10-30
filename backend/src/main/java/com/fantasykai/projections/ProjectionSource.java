package com.fantasykai.projections;

import java.util.regex.Pattern;

/**
 * Which method produced a projection, stated so that it stays true.
 *
 * <p>{@code id} is what Slice 3 persists beside every projected line. It names an algorithm and
 * a version, never a Java class, and it never changes meaning: a different weighting, window or
 * input is a new id ({@code -v2}), so a stored projection keeps saying exactly how it was made.
 *
 * <p>The id is checked here, where a source is defined, rather than when a row is written: an
 * id the database cannot hold fails the application at startup instead of failing every
 * night's insert. {@link #MAX_ID_LENGTH} is the width of
 * {@code player_week_projection.provider} (V7); {@code ProjectionSchemaTests} holds the two
 * together.
 *
 * @param id          stable and versioned, e.g. {@code weighted-average-v1}; at most
 *                    {@value #MAX_ID_LENGTH} characters
 * @param label       what a member sees
 * @param description one plain sentence on the method -- no "AI", no "model" for a baseline
 */
public record ProjectionSource(String id, String label, String description) {

    /** The longest id {@code player_week_projection.provider} holds. */
    public static final int MAX_ID_LENGTH = 40;

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+");

    public ProjectionSource {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("a source id is lowercase words and a version, "
                    + "like weighted-average-v1; got " + id);
        }
        if (id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("a source id is at most " + MAX_ID_LENGTH
                    + " characters, the width of the column it is stored in; got " + id.length()
                    + ": " + id);
        }
        if (label == null || label.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("a source needs a label and a description");
        }
    }
}
