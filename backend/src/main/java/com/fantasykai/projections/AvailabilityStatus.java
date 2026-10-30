package com.fantasykai.projections;

/**
 * What is known, before kickoff, about whether a player is available for a week's game.
 *
 * <p>Known information only: an official designation or a roster list, never a prediction.
 * There are no probabilities here and nothing says how likely a Questionable player is to play.
 * The projection itself never depends on this -- a raw line is made from history alone and kept
 * whatever happens; this decides only whether the board ranks it (architecture section 6).
 */
public enum AvailabilityStatus {

    /** On the active roster with no game designation. */
    AVAILABLE(true),
    /** Officially Questionable: ranked, and shown as such. */
    QUESTIONABLE(true),
    /** Officially Doubtful: ranked, and shown as such. */
    DOUBTFUL(true),
    /** Officially Out for the week's game: projected, not ranked. */
    OUT(false),
    /** On a reserve list (injured reserve, PUP, NFI, suspension) or the exempt list: cannot play. */
    RESERVE(false),
    /** Not on the active roster that week: practice squad, released, retired. */
    NOT_ON_ROSTER(false),
    /**
     * Nothing known: no roster row and no designation. A feed that is missing, late or failed is
     * not news about a player, so this is ranked -- it never makes anyone disappear.
     */
    UNKNOWN(true);

    private final boolean ranked;

    AvailabilityStatus(boolean ranked) {
        this.ranked = ranked;
    }

    /** Whether the projection board ranks a player in this state. */
    public boolean ranked() {
        return ranked;
    }
}
