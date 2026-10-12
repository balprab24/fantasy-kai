package com.fantasykai.projections;

import java.util.List;
import java.util.Set;

/**
 * The one place a roster status or an injury designation is given a meaning.
 *
 * <p>Every source string the projection side reads is named here and nowhere else, as
 * {@code StatKey} does for stats: the ingestors store what nflverse ships, this classifies it,
 * and the job and the availability service ask this class rather than comparing strings.
 *
 * <p>The values and their meanings were measured, not read off the field names (2025 regular
 * season, rows with an id, against who recorded a stat line or an offensive snap that week):
 * a player marked RES, EXE, DEV, CUT or RET played 0 times; one marked injury-report Out played
 * 0 times in 1,342 rows. {@code status_description_abbr} only refines {@code status} (R01, R48,
 * P07...), so only {@code status} is read.
 *
 * <p><strong>Precedence</strong>, first match wins:
 * <ol>
 *   <li>roster RES or EXE &rarr; {@link AvailabilityStatus#RESERVE};</li>
 *   <li>roster DEV, CUT or RET &rarr; {@link AvailabilityStatus#NOT_ON_ROSTER};</li>
 *   <li>report Out &rarr; {@link AvailabilityStatus#OUT} -- whatever the roster says: ACT is
 *       membership of the 53, Out is this week's game, and the two do not conflict;</li>
 *   <li>report Doubtful, then Questionable;</li>
 *   <li>roster ACT or INA &rarr; {@link AvailabilityStatus#AVAILABLE};</li>
 *   <li>otherwise {@link AvailabilityStatus#UNKNOWN}.</li>
 * </ol>
 *
 * <p>INA is deliberately never a reason to rank anyone lower. It is the game-day inactive list,
 * written about 90 minutes before kickoff, so a morning pull cannot see it; reading it in a
 * backtest would be using the future. Any other roster value (TRD, TRC, ...) or report value
 * ('Note', or one nflverse adds later) means no information, never unavailability.
 */
public final class AvailabilityRules {

    /** On the 53-man roster. INA is a game-day list of players still on it. */
    private static final Set<String> ACTIVE = Set.of("ACT", "INA");

    /** Reserve lists and the exempt list: on the team, unable to play. */
    private static final Set<String> RESERVE = Set.of("RES", "EXE");

    /** Practice squad, released, retired: not on the 53. */
    private static final Set<String> OFF_ROSTER = Set.of("DEV", "CUT", "RET");

    static final String OUT = "Out";
    static final String DOUBTFUL = "Doubtful";
    static final String QUESTIONABLE = "Questionable";

    /**
     * The roster statuses that make a player a projection candidate: on the team that week,
     * available or not. A reserve player gets a projection -- his page can show it -- and is left
     * off the board.
     */
    public static final List<String> PROJECTED_ROSTER_STATUSES = List.of("ACT", "INA", "RES", "EXE");

    private AvailabilityRules() {}

    /**
     * @param rosterStatus the week's roster {@code status}, or null when there is no row
     * @param reportStatus the week's injury {@code report_status}, or null for none or blank
     */
    public static AvailabilityStatus classify(String rosterStatus, String reportStatus) {
        if (rosterStatus != null && RESERVE.contains(rosterStatus)) {
            return AvailabilityStatus.RESERVE;
        }
        if (rosterStatus != null && OFF_ROSTER.contains(rosterStatus)) {
            return AvailabilityStatus.NOT_ON_ROSTER;
        }
        if (OUT.equals(reportStatus)) {
            return AvailabilityStatus.OUT;
        }
        if (DOUBTFUL.equals(reportStatus)) {
            return AvailabilityStatus.DOUBTFUL;
        }
        if (QUESTIONABLE.equals(reportStatus)) {
            return AvailabilityStatus.QUESTIONABLE;
        }
        if (rosterStatus != null && ACTIVE.contains(rosterStatus)) {
            return AvailabilityStatus.AVAILABLE;
        }
        return AvailabilityStatus.UNKNOWN;
    }

    /** A roster status this class gives a meaning to; anything else is logged by the ingest. */
    public static boolean knownRosterStatus(String status) {
        return ACTIVE.contains(status) || RESERVE.contains(status) || OFF_ROSTER.contains(status);
    }

    /** A report status this class gives a meaning to; blank is not a value. */
    public static boolean knownReportStatus(String status) {
        return OUT.equals(status) || DOUBTFUL.equals(status) || QUESTIONABLE.equals(status);
    }

    /**
     * How restrictive a report status is, for the rare week a player is listed twice: 2024 had two
     * such player-weeks, and in the one inspected the later report was the more restrictive
     * (Questionable, then Out). The 2025-26 files carry no timestamp to order them, so the most
     * restrictive wins -- a deterministic rule that leans toward not ranking a player twice
     * reported, and never toward inventing a designation.
     */
    public static int restrictiveness(String reportStatus) {
        if (OUT.equals(reportStatus)) {
            return 3;
        }
        if (DOUBTFUL.equals(reportStatus)) {
            return 2;
        }
        if (QUESTIONABLE.equals(reportStatus)) {
            return 1;
        }
        return 0;
    }
}
