package com.fantasykai.leagues;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One league as the platform reports it, already translated into our terms --
 * scoring as a {@link LeagueScoring}, lineup slots as {@link RosterSlot}s -- and
 * carrying no points. The platform's points-for is deliberately not read.
 *
 * @param slots          starting-slot counts; BENCH and IR included
 * @param unmodeledSlots slot names the league uses that no {@link RosterSlot} covers, with counts
 * @param remainingWeeks regular-season weeks not yet finished, the ones a schedule is for
 */
public record LeagueSnapshot(
        String externalLeagueId,
        String name,
        int season,
        int numTeams,
        LeagueType type,
        LeagueScoring scoring,
        Map<RosterSlot, Integer> slots,
        Map<String, Integer> unmodeledSlots,
        Integer playoffTeams,
        Integer playoffWeekStart,
        Integer tradeDeadline,
        List<Integer> remainingWeeks,
        List<Team> teams,
        List<RosterEntry> roster) {

    public LeagueSnapshot {
        slots = Map.copyOf(slots);
        unmodeledSlots = Map.copyOf(unmodeledSlots);
        remainingWeeks = List.copyOf(remainingWeeks);
        teams = List.copyOf(teams);
        roster = List.copyOf(roster);
    }

    public enum LeagueType { REDRAFT, KEEPER, DYNASTY, UNKNOWN }

    /** @param ownerIds platform account ids that manage this team, co-owners included */
    public record Team(String externalTeamId, String name, String managerName,
            Set<String> ownerIds, int wins, int losses, int ties) {

        public Team {
            ownerIds = Set.copyOf(ownerIds);
        }
    }

    /** @param slot a {@link RosterSlot} name for a starter, or BENCH, IR, TAXI or UNMODELED */
    public record RosterEntry(String externalTeamId, String externalPlayerId, String slot) {}
}
