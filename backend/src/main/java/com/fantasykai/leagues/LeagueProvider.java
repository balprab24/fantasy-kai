package com.fantasykai.leagues;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * One fantasy platform, read as the member (north-star §5).
 *
 * <p>Pure: a provider returns the platform's data and neither writes nor
 * scores. {@link LeagueService} decides what is stored. Credentials arrive
 * with ESPN private leagues (S4) and Yahoo (S5); Sleeper has none, so this
 * slice's interface carries none rather than an argument nothing reads.
 */
public interface LeagueProvider {

    /** The {@code platform} value stored on connections and leagues. */
    String platform();

    /** The platform account behind a handle the member typed. */
    Account account(String handle);

    /** The account's leagues for a season. */
    List<LeagueSummary> leagues(Account account, int season);

    /** Everything an import or a sync stores, in as few calls as the platform allows. */
    LeagueSnapshot fetch(String externalLeagueId);

    /**
     * Who plays whom, for the given weeks. A week the platform has not paired
     * yet is absent from the result, never an empty list that reads as "bye".
     */
    Map<Integer, List<Pairing>> schedule(String externalLeagueId, Collection<Integer> weeks);

    /** @param externalUserId the platform's stable id; a display name can change */
    record Account(String externalUserId, String displayName) {}

    record LeagueSummary(String externalLeagueId, String name, int season, int numTeams) {}

    record Pairing(String externalTeamId, String opponentExternalTeamId) {}
}
