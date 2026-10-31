package com.fantasykai.leagues;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A starting slot in a fantasy lineup, and the positions that may fill it.
 *
 * <p>Deliberately separate from {@code ScoringPosition}, which is a query
 * filter whitelist (north-star §8): a FLEX is not a position anyone filters a
 * board by, and a filter enum that grew lineup semantics would leak them into
 * every query that takes one.
 *
 * <p>The three flex slots exist because they overlap without nesting -- W/R
 * and W/T share WR and neither contains the other -- which is why S2's
 * optimiser solves an assignment instead of filling slots greedily (plan A8).
 */
public enum RosterSlot {
    QB(Set.of("QB")),
    RB(Set.of("RB")),
    WR(Set.of("WR")),
    TE(Set.of("TE")),
    FLEX_RWT(Set.of("RB", "WR", "TE")),
    FLEX_WR(Set.of("WR", "RB")),
    FLEX_WT(Set.of("WR", "TE")),
    SUPERFLEX(Set.of("QB", "RB", "WR", "TE")),
    K(Set.of("K")),
    DST(Set.of("DST")),
    BENCH(Set.of()),
    IR(Set.of());

    /**
     * Sleeper's {@code roster_positions} names, measured on a captured league
     * (2026-10-28) and Sleeper's league settings. IDP slots (DL, LB, DB,
     * IDP_FLEX) are absent on purpose: an unknown name is reported to the
     * member as unmodelled, never guessed into a slot.
     */
    private static final Map<String, RosterSlot> SLEEPER = Map.ofEntries(
            Map.entry("QB", QB), Map.entry("RB", RB), Map.entry("WR", WR), Map.entry("TE", TE),
            Map.entry("FLEX", FLEX_RWT), Map.entry("WRRB_FLEX", FLEX_WR),
            Map.entry("REC_FLEX", FLEX_WT), Map.entry("SUPER_FLEX", SUPERFLEX),
            Map.entry("K", K), Map.entry("DEF", DST), Map.entry("BN", BENCH));

    private final Set<String> eligible;

    RosterSlot(Set<String> eligible) {
        this.eligible = eligible;
    }

    /** Positions that may start here; empty for BENCH and IR, which start no one. */
    public Set<String> eligible() {
        return eligible;
    }

    public boolean starts() {
        return !eligible.isEmpty();
    }

    public static Optional<RosterSlot> fromSleeper(String name) {
        return Optional.ofNullable(name == null ? null : SLEEPER.get(name));
    }
}
