package com.fantasykai.leagues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fantasykai.leagues.LeagueProvider.Pairing;
import com.fantasykai.leagues.LeagueSnapshot.RosterEntry;
import com.fantasykai.leagues.LeagueSnapshot.Team;
import com.fantasykai.scoring.RulesetValidator;
import com.fantasykai.scoring.StatKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SleeperLeagueProviderTests {

    private final ObjectMapper json = new ObjectMapper();
    private final FixtureLeagueHttp http = new FixtureLeagueHttp(json);
    private int spent;
    private final LeagueLimits counting = new LeagueLimits(noRedis(), FixtureLeagueHttp.PROPS) {
        @Override
        public void sleeperCall() {
            spent++;
        }
    };
    private final SleeperLeagueProvider sleeper = new SleeperLeagueProvider(http, counting,
            new SleeperScoringMapper(new RulesetValidator()), FixtureLeagueHttp.PROPS);

    @Test
    void anAccountIsItsStableIdNotTheUsername() {
        LeagueProvider.Account account = sleeper.account(FixtureLeagueHttp.USERNAME);

        assertThat(account.externalUserId()).isEqualTo(FixtureLeagueHttp.OWNER);
        assertThat(account.displayName()).isEqualTo("fixture_owner");
    }

    /** Sleeper answers an unknown username with {@code 200 null}. */
    @Test
    void anUnknownUsernameIsNotFound() {
        assertThatThrownBy(() -> sleeper.account("nobody_here"))
                .isInstanceOf(LeagueNotFoundException.class).hasMessageContaining("nobody_here");
    }

    /** The shape is checked before a URL is built: nothing malformed reaches Sleeper. */
    @Test
    void aMalformedHandleNeverBecomesARequest() {
        for (String bad : List.of("../league/1", "a b", "x".repeat(41), "", "user%2f..", "name'--")) {
            assertThatThrownBy(() -> sleeper.account(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        for (String bad : List.of("12a", "1".repeat(21), "../1", "")) {
            assertThatThrownBy(() -> sleeper.fetch(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(http.requested).isEmpty();
        assertThat(spent).isZero();
    }

    @Test
    void leaguesAreListedForTheSeason() {
        var leagues = sleeper.leagues(sleeper.account(FixtureLeagueHttp.USERNAME), 2026);

        assertThat(leagues).extracting(LeagueProvider.LeagueSummary::externalLeagueId)
                .containsExactly(FixtureLeagueHttp.LEAGUE, "100000000000000002");
        assertThat(leagues.getFirst().numTeams()).isEqualTo(4);
    }

    /** Plan S1: league, users, rosters, state -- four calls, each one spent from the shared budget. */
    @Test
    void aFetchIsFourCallsAndSpendsFour() {
        sleeper.fetch(FixtureLeagueHttp.LEAGUE);

        assertThat(http.requested).hasSize(4);
        assertThat(spent).isEqualTo(4);
    }

    @Test
    void slotsAreCountedAndAnUnknownSlotIsReportedNotGuessed() {
        LeagueSnapshot league = sleeper.fetch(FixtureLeagueHttp.LEAGUE);

        assertThat(league.slots()).containsExactlyInAnyOrderEntriesOf(Map.of(
                RosterSlot.QB, 1, RosterSlot.RB, 2, RosterSlot.WR, 2, RosterSlot.TE, 1,
                RosterSlot.FLEX_RWT, 1, RosterSlot.FLEX_WT, 1, RosterSlot.K, 1, RosterSlot.DST, 1,
                RosterSlot.BENCH, 3, RosterSlot.IR, 1));
        assertThat(league.unmodeledSlots()).containsExactly(Map.entry("DL", 1));
    }

    /** starters[] follows roster_positions without the bench; "0" is an empty slot, not a player. */
    @Test
    void everyPlayerSitsInExactlyOneSpot() {
        LeagueSnapshot league = sleeper.fetch(FixtureLeagueHttp.LEAGUE);
        Map<String, String> slots = new java.util.HashMap<>();
        for (RosterEntry entry : league.roster()) {
            assertThat(slots.put(entry.externalPlayerId(), entry.slot())).as(entry.externalPlayerId()).isNull();
        }

        assertThat(slots).containsAllEntriesOf(Map.of(
                "1001", "QB", "1002", "RB", "1003", "RB", "1004", "WR", "1006", "TE",
                "1007", "FLEX_RWT", "1008", "FLEX_WT", "1009", "K", "CLE", "DST", "1010", "UNMODELED"));
        assertThat(slots).containsEntry("1012", "IR").containsEntry("1011", "BENCH")
                .containsEntry("9999", "BENCH").containsEntry("1013", "QB").doesNotContainKey("0");
    }

    @Test
    void teamsCarryOwnersAndNamesButNoPoints() {
        Map<String, Team> teams = new java.util.HashMap<>();
        sleeper.fetch(FixtureLeagueHttp.LEAGUE).teams().forEach(t -> teams.put(t.externalTeamId(), t));

        assertThat(teams.get("1").name()).isEqualTo("Fixture Owners");
        assertThat(teams.get("1").ownerIds()).containsExactly(FixtureLeagueHttp.OWNER);
        assertThat(teams.get("2").name()).as("no team name: the manager's").isEqualTo("second_manager");
        assertThat(teams.get("2").ownerIds()).as("co-owners count")
                .containsExactlyInAnyOrder("200000000000000002", "200000000000000003");
        assertThat(teams.get("3").name()).as("an orphaned team").isEqualTo("Team 3");
        assertThat(teams.get("4").name()).as("a blank team name").isEqualTo("fourth_manager");
        assertThat(teams.get("4").ties()).isEqualTo(1);
        assertThat(Team.class.getRecordComponents()).extracting(c -> c.getName())
                .noneMatch(name -> name.toLowerCase().contains("point") || name.contains("fpts"));
    }

    @Test
    void theLeaguesScoringArrivesTranslated() {
        LeagueSnapshot league = sleeper.fetch(FixtureLeagueHttp.LEAGUE);

        assertThat(league.scoring().ruleset().positionOverrides().get("TE")).containsEntry(StatKey.REC, 1.5);
        assertThat(league.type()).isEqualTo(LeagueSnapshot.LeagueType.REDRAFT);
        assertThat(league.playoffWeekStart()).isEqualTo(15);
        assertThat(league.remainingWeeks()).containsExactly(8, 9, 10, 11, 12, 13, 14);
    }

    @Test
    void remainingWeeksFollowTheSeasonsPhase() {
        JsonNode settings = json.createObjectNode().put("start_week", 1).put("playoff_week_start", 15);

        assertThat(SleeperLeagueProvider.remainingWeeks(2026, settings, state(2026, "regular", 8)))
                .containsExactly(8, 9, 10, 11, 12, 13, 14);
        assertThat(SleeperLeagueProvider.remainingWeeks(2026, settings, state(2026, "pre", 0)))
                .hasSize(14).startsWith(1);
        assertThat(SleeperLeagueProvider.remainingWeeks(2026, settings, state(2026, "post", 19))).isEmpty();
        assertThat(SleeperLeagueProvider.remainingWeeks(2025, settings, state(2026, "regular", 8))).isEmpty();
        assertThat(SleeperLeagueProvider.remainingWeeks(2026,
                json.createObjectNode().put("playoff_week_start", 0), state(2026, "regular", 17)))
                .as("no playoffs: the regular season runs to 18").containsExactly(17, 18);
    }

    /** A week Sleeper has not paired yet is absent, never an empty week that reads as a bye. */
    @Test
    void scheduleReadsPairingsAndLeavesUnpairedWeeksOut() {
        Map<Integer, List<Pairing>> schedule = sleeper.schedule(FixtureLeagueHttp.LEAGUE, List.of(8, 9, 10, 11));

        assertThat(schedule).containsOnlyKeys(8, 9);
        assertThat(schedule.get(8)).containsExactlyInAnyOrder(new Pairing("1", "2"), new Pairing("2", "1"),
                new Pairing("3", "4"), new Pairing("4", "3"));
        assertThat(schedule.get(9)).contains(new Pairing("1", "3"));
    }

    @Test
    void aMatchupWithMoreThanTwoSidesIsNotInventedIntoAGame() {
        http.answer("/v1/league/" + FixtureLeagueHttp.LEAGUE + "/matchups/8", json.valueToTree(List.of(
                Map.of("roster_id", 1, "matchup_id", 1), Map.of("roster_id", 2, "matchup_id", 1),
                Map.of("roster_id", 3, "matchup_id", 1))));

        assertThat(sleeper.schedule(FixtureLeagueHttp.LEAGUE, List.of(8))).isEmpty();
    }

    @Test
    void aMissingLeagueOrAnotherSportIsNotFound() {
        http.answer("/v1/league/" + FixtureLeagueHttp.LEAGUE, NullNode.getInstance());
        assertThatThrownBy(() -> sleeper.fetch(FixtureLeagueHttp.LEAGUE)).isInstanceOf(LeagueNotFoundException.class);

        http.answer("/v1/league/" + FixtureLeagueHttp.LEAGUE,
                json.createObjectNode().put("league_id", FixtureLeagueHttp.LEAGUE).put("sport", "nba"));
        assertThatThrownBy(() -> sleeper.fetch(FixtureLeagueHttp.LEAGUE)).isInstanceOf(LeagueNotFoundException.class);
    }

    @Test
    void aRosterThatIsNotAListIsUnreadableData() {
        http.answer("/v1/league/" + FixtureLeagueHttp.LEAGUE + "/rosters", json.createObjectNode());

        assertThatThrownBy(() -> sleeper.fetch(FixtureLeagueHttp.LEAGUE)).isInstanceOf(LeagueDataException.class);
    }

    private JsonNode state(int season, String phase, int week) {
        return json.createObjectNode().put("season", String.valueOf(season)).put("season_type", phase)
                .put("week", week);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<io.github.bucket4j.distributed.proxy.ProxyManager<byte[]>> noRedis() {
        return org.mockito.Mockito.mock(ObjectProvider.class);
    }
}
