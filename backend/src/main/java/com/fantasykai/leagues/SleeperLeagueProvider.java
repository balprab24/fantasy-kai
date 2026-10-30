package com.fantasykai.leagues;

import com.fantasykai.leagues.LeagueSnapshot.LeagueType;
import com.fantasykai.leagues.LeagueSnapshot.RosterEntry;
import com.fantasykai.leagues.LeagueSnapshot.Team;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Sleeper, read through its public, read-only API (plan A1). No secret exists
 * to hold: a member types a username, and the stable {@code user_id} it
 * resolves to is what gets stored.
 *
 * <p>An import or sync is four calls -- league, users, rosters, the NFL state
 * -- plus one per schedule week still missing. Each call spends one unit of the
 * shared budget in {@link LeagueLimits}.
 *
 * <p>Sleeper is free for non-commercial use only, which this project is.
 */
@Component
public class SleeperLeagueProvider implements LeagueProvider {

    public static final String PLATFORM = "sleeper";

    /** Plan S1: the only shapes a user-supplied part of a Sleeper URL may take. */
    static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,40}");
    static final Pattern LEAGUE_ID = Pattern.compile("\\d{1,20}");

    /** The regular season's last week when a league has no playoffs. */
    private static final int LAST_REGULAR_WEEK = 18;

    private final LeagueHttp http;
    private final LeagueLimits limits;
    private final SleeperScoringMapper scoring;
    private final String baseUrl;

    public SleeperLeagueProvider(LeagueHttp http, LeagueLimits limits, SleeperScoringMapper scoring,
            LeagueProperties props) {
        this.http = http;
        this.limits = limits;
        this.scoring = scoring;
        this.baseUrl = props.sleeperBaseUrl();
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public Account account(String username) {
        require(USERNAME, username, "username");
        JsonNode user = get("/v1/user/" + username);
        String id = text(user, "user_id");
        if (id == null) {
            throw new LeagueNotFoundException("No Sleeper user is named " + username + ".");
        }
        return new Account(id, firstNonBlank(text(user, "display_name"), username));
    }

    @Override
    public List<LeagueSummary> leagues(Account account, int season) {
        JsonNode leagues = get("/v1/user/%s/leagues/nfl/%d"
                .formatted(require(LEAGUE_ID, account.externalUserId(), "user id"), season));
        List<LeagueSummary> result = new ArrayList<>();
        for (JsonNode league : array(leagues, "leagues")) {
            String id = text(league, "league_id");
            if (id != null && LEAGUE_ID.matcher(id).matches()) {
                result.add(new LeagueSummary(id, firstNonBlank(text(league, "name"), "Sleeper league"),
                        season, numTeams(league)));
            }
        }
        return result;
    }

    @Override
    public LeagueSnapshot fetch(String leagueId) {
        require(LEAGUE_ID, leagueId, "league id");
        JsonNode league = get("/v1/league/" + leagueId);
        if (league.isNull() || !"nfl".equals(text(league, "sport"))) {
            throw new LeagueNotFoundException("No Sleeper NFL league has id " + leagueId + ".");
        }
        JsonNode users = get("/v1/league/" + leagueId + "/users");
        JsonNode rosters = get("/v1/league/" + leagueId + "/rosters");
        JsonNode state = get("/v1/state/nfl");

        JsonNode settings = league.path("settings");
        int season = integer(text(league, "season"), "season");

        Map<RosterSlot, Integer> slots = new EnumMap<>(RosterSlot.class);
        Map<String, Integer> unmodeledSlots = new TreeMap<>();
        List<String> starting = new ArrayList<>();
        for (JsonNode position : array(league.path("roster_positions"), "roster_positions")) {
            String name = position.asText();
            RosterSlot slot = RosterSlot.fromSleeper(name).orElse(null);
            if (slot == null) {
                unmodeledSlots.merge(name, 1, Integer::sum);
            } else {
                slots.merge(slot, 1, Integer::sum);
            }
            if (slot != RosterSlot.BENCH) {
                // Sleeper's starters[] follows roster_positions with the bench left out.
                starting.add(slot == null ? "UNMODELED" : slot.name());
            }
        }
        int reserve = settings.path("reserve_slots").asInt(0);
        if (reserve > 0) {
            slots.put(RosterSlot.IR, reserve);
        }

        Map<String, JsonNode> usersById = new HashMap<>();
        for (JsonNode user : array(users, "users")) {
            String id = text(user, "user_id");
            if (id != null) {
                usersById.put(id, user);
            }
        }

        List<Team> teams = new ArrayList<>();
        Map<String, RosterEntry> roster = new LinkedHashMap<>();
        for (JsonNode team : array(rosters, "rosters")) {
            String teamId = text(team, "roster_id");
            if (teamId == null) {
                throw new LeagueDataException("A Sleeper roster has no roster_id.");
            }
            Set<String> owners = new HashSet<>();
            String owner = text(team, "owner_id");
            if (owner != null) {
                owners.add(owner);
            }
            for (JsonNode coOwner : team.path("co_owners")) {
                owners.add(coOwner.asText());
            }
            JsonNode user = owner == null ? null : usersById.get(owner);
            String manager = user == null ? null : text(user, "display_name");
            String name = firstNonBlank(user == null ? null : text(user.path("metadata"), "team_name"),
                    manager, "Team " + teamId);
            JsonNode record = team.path("settings");
            teams.add(new Team(teamId, truncate(name, 128), manager == null ? null : truncate(manager, 128),
                    owners, record.path("wins").asInt(0), record.path("losses").asInt(0),
                    record.path("ties").asInt(0)));

            // Precedence: a starter is a starter even though players[] lists him too.
            JsonNode starters = team.path("starters");
            for (int i = 0; i < starters.size(); i++) {
                String player = starters.get(i).asText();
                if (!"0".equals(player) && !player.isBlank()) {
                    String slot = i < starting.size() ? starting.get(i) : "UNMODELED";
                    roster.putIfAbsent(player, new RosterEntry(teamId, player, slot));
                }
            }
            addAll(roster, teamId, team.path("reserve"), "IR");
            addAll(roster, teamId, team.path("taxi"), "TAXI");
            addAll(roster, teamId, team.path("players"), "BENCH");
        }

        return new LeagueSnapshot(leagueId, truncate(firstNonBlank(text(league, "name"), "Sleeper league"), 128),
                season, numTeams(league), type(settings.path("type").asInt(-1)),
                scoring.map(league.path("scoring_settings")), slots, unmodeledSlots,
                optional(settings, "playoff_teams"), optional(settings, "playoff_week_start"),
                optional(settings, "trade_deadline"), remainingWeeks(season, settings, state),
                teams, new ArrayList<>(roster.values()));
    }

    @Override
    public Map<Integer, List<Pairing>> schedule(String leagueId, Collection<Integer> weeks) {
        require(LEAGUE_ID, leagueId, "league id");
        Map<Integer, List<Pairing>> schedule = new TreeMap<>();
        for (int week : weeks) {
            Map<String, List<String>> byMatchup = new HashMap<>();
            for (JsonNode side : array(get("/v1/league/%s/matchups/%d".formatted(leagueId, week)), "matchups")) {
                String matchup = text(side, "matchup_id");
                String team = text(side, "roster_id");
                if (matchup != null && team != null) {
                    byMatchup.computeIfAbsent(matchup, ignored -> new ArrayList<>()).add(team);
                }
            }
            List<Pairing> pairings = new ArrayList<>();
            for (List<String> pair : byMatchup.values()) {
                // Two sides make a game. Anything else (a median or guillotine
                // format) is not a head-to-head pairing and is not invented into one.
                if (pair.size() == 2) {
                    pairings.add(new Pairing(pair.get(0), pair.get(1)));
                    pairings.add(new Pairing(pair.get(1), pair.get(0)));
                }
            }
            if (!pairings.isEmpty()) {
                schedule.put(week, pairings);
            }
        }
        return schedule;
    }

    /**
     * Regular-season weeks still to play: from the current week during the
     * season, all of them before it, none after it. Sleeper's
     * {@code playoff_week_start} ends the regular season; 0 means no playoffs.
     */
    static List<Integer> remainingWeeks(int season, JsonNode settings, JsonNode state) {
        int start = Math.max(1, settings.path("start_week").asInt(1));
        int playoffs = settings.path("playoff_week_start").asInt(0);
        int last = playoffs > 1 ? Math.min(playoffs - 1, LAST_REGULAR_WEEK) : LAST_REGULAR_WEEK;
        int stateSeason = state.path("season").asInt(0);
        String phase = state.path("season_type").asText("");

        int from;
        if (season < stateSeason || season == stateSeason && "post".equals(phase)) {
            return List.of();
        } else if (season == stateSeason && "regular".equals(phase)) {
            from = Math.max(start, state.path("week").asInt(start));
        } else {
            from = start;
        }
        List<Integer> weeks = new ArrayList<>();
        for (int week = from; week <= last; week++) {
            weeks.add(week);
        }
        return weeks;
    }

    /** {@code settings.type}: 0 is redraft (measured); 1 keeper and 2 dynasty are unverified. */
    private static LeagueType type(int code) {
        return switch (code) {
            case 0 -> LeagueType.REDRAFT;
            case 1 -> LeagueType.KEEPER;
            case 2 -> LeagueType.DYNASTY;
            default -> LeagueType.UNKNOWN;
        };
    }

    private JsonNode get(String path) {
        limits.sleeperCall();
        return http.get(URI.create(baseUrl + path));
    }

    private static void addAll(Map<String, RosterEntry> roster, String teamId, JsonNode players, String slot) {
        for (JsonNode player : players) {
            String id = player.asText();
            if (!id.isBlank() && !"0".equals(id)) {
                roster.putIfAbsent(id, new RosterEntry(teamId, id, slot));
            }
        }
    }

    private static int numTeams(JsonNode league) {
        int teams = league.path("settings").path("num_teams").asInt(0);
        return teams > 0 ? teams : league.path("total_rosters").asInt(0);
    }

    private static Integer optional(JsonNode settings, String key) {
        JsonNode value = settings.get(key);
        return value == null || !value.canConvertToInt() ? null : value.asInt();
    }

    private static Iterable<JsonNode> array(JsonNode node, String what) {
        if (node.isNull() || node.isMissingNode()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new LeagueDataException("Sleeper " + what + " is not a list.");
        }
        return node;
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }

    private static int integer(String value, String what) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new LeagueDataException("Sleeper " + what + " is not a number: " + value);
        }
    }

    private static String require(Pattern pattern, String value, String what) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("not a valid Sleeper " + what);
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
