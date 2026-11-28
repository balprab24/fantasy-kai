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
 *                     API serves it as {@code source.summary}, so a provider swap updates the copy
 * @param typicalError how far off the method has been, measured; null for a source that has no
 *                     published figure. Each of its figures says who it is about
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
     * How far off a method has been: one or more figures, each a headline with the measurements it
     * summarises and the population it is about. The member's popover shows them, so each is held
     * to what was measured: a headline is only constructible while every measurement is within
     * {@link ErrorFigure#HEADLINE_TOLERANCE} of it.
     *
     * <p>Why a list. The same method has a different typical miss for different groups, and the
     * honest copy gives more than one: {@link FigureKind#IF_HE_PLAYS} for the players who then
     * played, {@link FigureKind#BEFORE_KICKOFF} for the same players counting those who sat as
     * zeros. A third kind later is a list entry, not a new field.
     *
     * <p>What the check guards, and what it does not. It stops a headline and its measurements being
     * edited out of step -- the build fails, and so would the application -- and the committed
     * reports pin the measurements themselves ({@code ProjectionSourceTests}). It does not watch
     * new seasons: nothing feeds a result into this class, so a method that drifts is noticed by
     * whoever re-runs the backtest, and later by the live accuracy report (Slice 7), not here.
     *
     * @param metric  what was measured, e.g. {@code PPR MAE}: the mean absolute error of a
     *                player-week's projected points under <em>full PPR</em>. Another scoring
     *                has another error (this baseline's 2024 error is 4.579 under 0 PPR and
     *                5.003 under Half PPR), so copy that shows a number says which
     * @param figures at least one, at most one of each kind
     */
    public record TypicalError(String metric, List<ErrorFigure> figures) {

        public TypicalError {
            if (metric == null || metric.isBlank()) {
                throw new IllegalArgumentException("a typical error names its metric");
            }
            if (figures == null || figures.isEmpty()) {
                throw new IllegalArgumentException("a typical error carries at least one figure");
            }
            figures = List.copyOf(figures);
            if (figures.stream().map(ErrorFigure::kind).distinct().count() != figures.size()) {
                throw new IllegalArgumentException("a typical error has at most one figure of each kind");
            }
        }
    }

    /** What a figure is about; the popover's copy keys on it. */
    public enum FigureKind {
        /** The players the research followed, among those who then played. */
        IF_HE_PLAYS,
        /** The same players, chosen before kickoff, counting each who then did not play as a zero. */
        BEFORE_KICKOFF
    }

    /**
     * One headline error and what it rests on.
     *
     * @param kind       what the figure is about
     * @param value      the headline; finite and positive
     * @param population exactly who the measurements are over. A different group has a different
     *                   error: on this baseline the board-wide population reads about 4.5, so a
     *                   headline must never be shown without this
     * @param measured   every measurement the headline summarises; at least one
     */
    public record ErrorFigure(FigureKind kind, double value, String population, List<Measurement> measured) {

        /** The headline may be this far from any measurement it summarises, and no further. */
        public static final double HEADLINE_TOLERANCE = 0.2;

        public ErrorFigure {
            if (kind == null) {
                throw new IllegalArgumentException("a figure says what it is about");
            }
            if (population == null || population.isBlank()) {
                throw new IllegalArgumentException("a figure names its population");
            }
            if (!(value > 0) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("a headline error is a positive number; got " + value);
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
