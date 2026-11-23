package com.fantasykai.projections;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
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
 * @param id           stable and versioned, e.g. {@code weighted-average-v1}; at most
 *                     {@value #MAX_ID_LENGTH} characters
 * @param label        what a member sees
 * @param summary      one plain sentence on the method -- no "AI", no "model" for a baseline. The
 *                     API serves it as the method's description, so a provider swap updates the
 *                     copy
 * @param typicalError how far off the method has been, measured; null for a source that has no
 *                     published figure
 */
public record ProjectionSource(String id, String label, String summary, TypicalError typicalError) {

    /** The longest id {@code player_week_projection.provider} holds. */
    public static final int MAX_ID_LENGTH = 40;

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+");

    /** A source with no published error figure. */
    public ProjectionSource(String id, String label, String summary) {
        this(id, label, summary, null);
    }

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
        if (label == null || label.isBlank() || summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("a source needs a label and a summary");
        }
    }

    /**
     * How far off a method has been, as a headline and the measurements it summarises. The number
     * is printed beside the label ("about 5.5 points per player-week"), so it is held to what was
     * measured: a headline is only constructible while every measurement is within
     * {@link #HEADLINE_TOLERANCE} of it. When a new season drifts past that, the application
     * refuses to start with the copy rather than keep saying a number it no longer supports.
     *
     * @param metric     what was measured, e.g. {@code PPR MAE}: the mean absolute error of a
     *                   player-week's projected points under full PPR
     * @param value      the headline, to the half point
     * @param population who it was measured over
     * @param measured   every measurement the headline summarises; at least one
     */
    public record TypicalError(String metric, double value, String population, List<Measurement> measured) {

        /** The headline may be this far from any measurement it summarises, and no further. */
        public static final double HEADLINE_TOLERANCE = 0.2;

        public TypicalError {
            if (metric == null || metric.isBlank() || population == null || population.isBlank()) {
                throw new IllegalArgumentException("a typical error names its metric and its population");
            }
            if (measured == null || measured.isEmpty()) {
                throw new IllegalArgumentException("a typical error rests on at least one measurement");
            }
            measured = List.copyOf(measured);
            for (Measurement measurement : measured) {
                if (Math.abs(value - measurement.mae()) > HEADLINE_TOLERANCE + 1e-9) {
                    throw new IllegalArgumentException("a headline of " + value + " is more than "
                            + HEADLINE_TOLERANCE + " from the measured " + measurement.mae() + " ("
                            + measurement.label() + "); the copy would overstate what was measured");
                }
            }
        }
    }

    /**
     * One measurement, from a committed report.
     *
     * @param season the season measured
     * @param weeks  which weeks of it, e.g. {@code 1-4}; null for the whole season
     * @param mae    the mean absolute error, in points
     * @param n      how many player-weeks it was measured over; null when the report does not say
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Measurement(int season, String weeks, double mae, Integer n) {

        public Measurement {
            if (!(mae > 0) || Double.isInfinite(mae)) {
                throw new IllegalArgumentException("a measured error is a positive number; got " + mae);
            }
        }

        String label() {
            return season + (weeks == null ? "" : " weeks " + weeks);
        }
    }
}
