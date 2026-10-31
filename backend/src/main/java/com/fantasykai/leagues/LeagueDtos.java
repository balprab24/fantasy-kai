package com.fantasykai.leagues;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response bodies for {@link LeagueController}. */
public final class LeagueDtos {

    private LeagueDtos() {}

    /** The same shapes {@link SleeperLeagueProvider} enforces, so a bad one is a 400 before any call. */
    static final String USERNAME = "[A-Za-z0-9_]{1,40}";
    static final String LEAGUE_ID = "\\d{1,20}";

    public record DiscoverRequest(
            @NotBlank @Pattern(regexp = USERNAME) String username,
            @Min(2020) @Max(2100) int season) {}

    /** @param importedLeagueId the member's own copy, when they already imported it */
    public record DiscoveredLeague(String externalLeagueId, String name, int season, int numTeams,
            Long importedLeagueId) {}

    public record Discovery(String externalUserId, String displayName, List<DiscoveredLeague> leagues) {}

    public record ImportRequest(
            @NotBlank @Pattern(regexp = "sleeper") String platform,
            @NotBlank @Pattern(regexp = USERNAME) String username,
            @NotBlank @Pattern(regexp = LEAGUE_ID) String externalLeagueId,
            @Min(2020) @Max(2100) int season) {}

    public record MyTeamRequest(@NotBlank @Size(max = 64) String externalTeamId) {}

    public record LeagueSummaryView(long id, String platform, String externalLeagueId, int season,
            String name, int numTeams, String myTeamName, Instant syncedAt) {}

    /** @param kind PLAYER, TEAM_DEFENSE (Sleeper names a defense by team) or UNMATCHED */
    public record RosterView(String externalPlayerId, String slot, String kind, Long playerId,
            String name, String position, String team, String espnId) {}

    public record TeamView(String externalTeamId, String name, String managerName, int wins,
            int losses, int ties, boolean mine, List<RosterView> roster) {}

    /** @param opponentName null when the opponent is not a team in the stored snapshot */
    public record WeekView(int week, String opponentExternalTeamId, String opponentName) {}

    /**
     * @param rosterSlots    starting and reserve slot counts, in lineup order
     * @param unmodeled      what the league scores or starts that is not modelled -- never dropped
     * @param schedule       the member's team's stored remaining schedule, by week
     * @param unmatched      roster spots whose player is not in our player master
     */
    public record LeagueView(long id, String platform, String externalLeagueId, int season,
            String name, int numTeams, String leagueType, Long scoringProfileId, String scoringNote,
            List<String> approximations, Map<String, Integer> rosterSlots,
            List<Unmodeled.Item> unmodeled, Integer playoffTeams, Integer playoffWeekStart,
            Integer tradeDeadline, String myTeamExternalId, Instant syncedAt, List<TeamView> teams,
            List<WeekView> schedule, int unmatched) {}
}
