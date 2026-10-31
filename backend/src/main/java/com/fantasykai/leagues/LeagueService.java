package com.fantasykai.leagues;

import com.fantasykai.leagues.LeagueDtos.DiscoveredLeague;
import com.fantasykai.leagues.LeagueDtos.Discovery;
import com.fantasykai.leagues.LeagueDtos.LeagueSummaryView;
import com.fantasykai.leagues.LeagueDtos.LeagueView;
import com.fantasykai.leagues.LeagueDtos.RosterView;
import com.fantasykai.leagues.LeagueDtos.TeamView;
import com.fantasykai.leagues.LeagueDtos.WeekView;
import com.fantasykai.leagues.LeagueProvider.Account;
import com.fantasykai.leagues.LeagueProvider.LeagueSummary;
import com.fantasykai.leagues.LeagueProvider.Pairing;
import com.fantasykai.leagues.LeagueRepository.LeagueFields;
import com.fantasykai.leagues.LeagueRepository.LeagueRow;
import com.fantasykai.leagues.LeagueRepository.RosterRow;
import com.fantasykai.leagues.LeagueRepository.TeamRow;
import com.fantasykai.leagues.LeagueSnapshot.RosterEntry;
import com.fantasykai.leagues.LeagueSnapshot.Team;
import com.fantasykai.query.ScoringProfileWriteRepository;
import com.fantasykai.scoring.RulesetJson;
import com.fantasykai.scoring.ScoringProfiles;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Import, sync, read and delete a member's leagues (plan S1).
 *
 * <p>The shape of every write: <strong>network first, outside any
 * transaction; then one transaction</strong> that replaces the snapshot,
 * writes the scoring profile and adds the schedule. A platform that times out
 * halfway leaves the previous snapshot exactly as it was, and no database
 * connection is held open across a ten-second HTTP call.
 *
 * <p>Ownership is in every statement ({@link LeagueRepository}), and it is
 * checked <em>before</em> a sync claims its cooldown: otherwise another
 * member could spend a league's sync slot just by knowing its id.
 */
@Service
public class LeagueService {

    /** Sleeper names a team defense by its team: {@code "CLE"}. */
    private static final Pattern TEAM_DEFENSE = Pattern.compile("[A-Z]{2,3}");

    private static final String PROFILE_SUFFIX = " (Sleeper)";
    private static final int PROFILE_NAME_MAX = 64;

    private final Map<String, LeagueProvider> providers;
    private final LeagueRepository leagues;
    private final LeagueLimits limits;
    private final ScoringProfileWriteRepository profiles;
    private final ScoringProfiles compiled;
    private final RulesetJson rulesetJson;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public LeagueService(List<LeagueProvider> providers, LeagueRepository leagues, LeagueLimits limits,
            ScoringProfileWriteRepository profiles, ScoringProfiles compiled, RulesetJson rulesetJson,
            ObjectMapper json, PlatformTransactionManager transactions) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(LeagueProvider::platform, p -> p));
        this.leagues = leagues;
        this.limits = limits;
        this.profiles = profiles;
        this.compiled = compiled;
        this.rulesetJson = rulesetJson;
        this.json = json;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = Clock.systemUTC();
    }

    /** A Sleeper account's leagues for a season. Reads the platform, writes nothing. */
    public Discovery discover(long userId, String username, int season) {
        LeagueProvider sleeper = provider(SleeperLeagueProvider.PLATFORM);
        Account account = sleeper.account(username);
        Map<String, Long> imported = leagues.importedIds(userId, sleeper.platform(), season);
        List<DiscoveredLeague> found = sleeper.leagues(account, season).stream()
                .map(l -> new DiscoveredLeague(l.externalLeagueId(), l.name(), l.season(),
                        l.numTeams(), imported.get(l.externalLeagueId())))
                .toList();
        return new Discovery(account.externalUserId(), account.displayName(), found);
    }

    /**
     * Imports a league the account belongs to. Membership is checked against
     * the account's own league list: the API is public, so without it any
     * league id would import under any username.
     */
    public LeagueView importLeague(long userId, String platform, String username,
            String externalLeagueId, int season) {
        LeagueProvider provider = provider(platform);
        Account account = provider.account(username);
        boolean member = provider.leagues(account, season).stream()
                .map(LeagueSummary::externalLeagueId).anyMatch(externalLeagueId::equals);
        if (!member) {
            throw new LeagueNotFoundException(
                    "That league is not one of " + username + "'s " + season + " leagues.");
        }
        LeagueSnapshot snapshot = provider.fetch(externalLeagueId);
        if (snapshot.season() != season) {
            throw new LeagueNotFoundException("That league is not a " + season + " league.");
        }
        Map<Integer, List<Pairing>> schedule = provider.schedule(externalLeagueId, snapshot.remainingWeeks());
        Map<String, Long> playerIds = leagues.resolveSleeperIds(playerIds(snapshot));
        String myTeam = snapshot.teams().stream()
                .filter(team -> team.ownerIds().contains(account.externalUserId()))
                .map(Team::externalTeamId).findFirst().orElse(null);

        long leagueId = transaction.execute(status -> {
            long connection = leagues.upsertConnection(userId, provider.platform(), account.externalUserId());
            Long profile = writeProfile(userId, null, snapshot);
            long id = leagues.insertLeague(userId, connection, provider.platform(), externalLeagueId,
                    season, fields(snapshot, profile, myTeam));
            leagues.replaceSnapshot(id, userId, snapshot.teams(), snapshot.roster(), playerIds);
            leagues.addMatchups(id, userId, schedule);
            return id;
        });
        return view(userId, leagueId);
    }

    public LeagueView sync(long userId, long leagueId) {
        LeagueRow row = leagues.find(leagueId, userId).orElseThrow(() -> notFound(leagueId));
        limits.sync(leagueId);
        LeagueProvider provider = provider(row.platform());
        LeagueSnapshot snapshot = provider.fetch(row.externalLeagueId());
        Set<Integer> stored = leagues.storedWeeks(leagueId, userId);
        List<Integer> missing = snapshot.remainingWeeks().stream().filter(w -> !stored.contains(w)).toList();
        Map<Integer, List<Pairing>> schedule = missing.isEmpty()
                ? Map.of() : provider.schedule(row.externalLeagueId(), missing);
        Map<String, Long> playerIds = leagues.resolveSleeperIds(playerIds(snapshot));
        // A team that left the league takes the member's choice with it.
        String myTeam = snapshot.teams().stream().map(Team::externalTeamId)
                .anyMatch(id -> id.equals(row.myTeamExternalId())) ? row.myTeamExternalId() : null;

        transaction.executeWithoutResult(status -> {
            Long profile = writeProfile(userId, row.scoringProfileId(), snapshot);
            if (!leagues.updateLeague(leagueId, userId, fields(snapshot, profile, myTeam))) {
                throw notFound(leagueId); // deleted while the platform was answering
            }
            leagues.replaceSnapshot(leagueId, userId, snapshot.teams(), snapshot.roster(), playerIds);
            leagues.addMatchups(leagueId, userId, schedule);
        });
        return view(userId, leagueId);
    }

    public List<LeagueSummaryView> list(long userId) {
        List<LeagueSummaryView> result = new ArrayList<>();
        for (LeagueRow row : leagues.findAll(userId)) {
            String myTeam = row.myTeamExternalId() == null ? null : leagues.teams(row.id(), userId).stream()
                    .filter(t -> t.externalTeamId().equals(row.myTeamExternalId()))
                    .map(TeamRow::name).findFirst().orElse(null);
            result.add(new LeagueSummaryView(row.id(), row.platform(), row.externalLeagueId(),
                    row.season(), row.name(), row.numTeams(), myTeam, row.syncedAt()));
        }
        return result;
    }

    public LeagueView view(long userId, long leagueId) {
        LeagueRow row = leagues.find(leagueId, userId).orElseThrow(() -> notFound(leagueId));
        Stored stored = read(row.unmodeled());

        Map<String, List<RosterView>> rosters = new HashMap<>();
        int unmatched = 0;
        for (RosterRow spot : leagues.roster(leagueId, userId)) {
            RosterView view = rosterView(spot);
            if ("UNMATCHED".equals(view.kind())) {
                unmatched++;
            }
            rosters.computeIfAbsent(spot.externalTeamId(), ignored -> new ArrayList<>()).add(view);
        }
        List<TeamView> teams = new ArrayList<>();
        Map<String, String> names = new HashMap<>();
        for (TeamRow team : leagues.teams(leagueId, userId)) {
            names.put(team.externalTeamId(), team.name());
            List<RosterView> roster = rosters.getOrDefault(team.externalTeamId(), List.of()).stream()
                    .sorted((a, b) -> Integer.compare(slotOrder(a.slot()), slotOrder(b.slot())))
                    .toList();
            teams.add(new TeamView(team.externalTeamId(), team.name(), team.managerName(),
                    team.wins(), team.losses(), team.ties(),
                    team.externalTeamId().equals(row.myTeamExternalId()), roster));
        }
        List<WeekView> schedule = row.myTeamExternalId() == null ? List.of()
                : leagues.matchups(leagueId, userId).stream()
                        .filter(m -> m.externalTeamId().equals(row.myTeamExternalId()))
                        .map(m -> new WeekView(m.week(), m.opponentExternalTeamId(),
                                names.get(m.opponentExternalTeamId())))
                        .toList();

        return new LeagueView(row.id(), row.platform(), row.externalLeagueId(), row.season(), row.name(),
                row.numTeams(), row.leagueType(), row.scoringProfileId(), row.scoringNote(),
                stored.approximations(), readSlots(row.rosterSlots()),
                Unmodeled.describe(stored.scoring(), stored.slots()), row.playoffTeams(),
                row.playoffWeekStart(), row.tradeDeadline(), row.myTeamExternalId(), row.syncedAt(),
                teams, schedule, unmatched);
    }

    public LeagueView setMyTeam(long userId, long leagueId, String externalTeamId) {
        if (!leagues.setMyTeam(leagueId, userId, externalTeamId)) {
            // Not yours and no such team are deliberately the same answer.
            throw new LeagueNotFoundException("No such team in league " + leagueId + ".");
        }
        return view(userId, leagueId);
    }

    /** Removes the league and the scoring profile its import created. */
    public void delete(long userId, long leagueId) {
        Long profile = transaction.execute(status -> {
            LeagueRepository.Deleted deleted = leagues.delete(leagueId, userId)
                    .orElseThrow(() -> notFound(leagueId));
            if (deleted.scoringProfileId() != null) {
                profiles.delete(deleted.scoringProfileId(), userId);
            }
            return deleted.scoringProfileId();
        });
        if (profile != null) {
            compiled.evict(profile);
        }
    }

    /**
     * The league's profile: updated in place on a sync, created on import or
     * when the member deleted it. Null when the league's scoring does not
     * validate -- the reason is kept in {@code scoring_note}.
     */
    private Long writeProfile(long userId, Long existing, LeagueSnapshot snapshot) {
        if (snapshot.scoring().ruleset() == null) {
            return existing;
        }
        String rules = rulesetJson.write(snapshot.scoring().ruleset());
        if (existing != null) {
            String name = profiles.findOwnName(existing, userId).orElse(null);
            if (name != null && profiles.update(existing, userId, name, rules)) {
                compiled.evict(existing);
                return existing;
            }
        }
        return profiles.create(userId, freeProfileName(userId, snapshot.name()), rules);
    }

    /**
     * {@code "<league> (Sleeper)"}, numbered on a clash, within V1's 64
     * characters. Picked before the insert rather than retried after a
     * unique violation, which in Postgres aborts the surrounding transaction.
     */
    private String freeProfileName(long userId, String leagueName) {
        Set<String> taken = new HashSet<>(profiles.namesOwnedBy(userId));
        for (int n = 1; ; n++) {
            String suffix = n == 1 ? PROFILE_SUFFIX : PROFILE_SUFFIX + " " + n;
            String base = leagueName.strip();
            if (base.length() + suffix.length() > PROFILE_NAME_MAX) {
                base = base.substring(0, PROFILE_NAME_MAX - suffix.length()).strip();
            }
            if (!taken.contains(base + suffix)) {
                return base + suffix;
            }
        }
    }

    private LeagueFields fields(LeagueSnapshot snapshot, Long profile, String myTeam) {
        Map<String, Integer> slots = new LinkedHashMap<>();
        snapshot.slots().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> slots.put(e.getKey().name(), e.getValue()));
        Stored stored = new Stored(new TreeMap<>(snapshot.scoring().unmodeled()),
                new TreeMap<>(snapshot.unmodeledSlots()), snapshot.scoring().approximations());
        return new LeagueFields(snapshot.name(), snapshot.numTeams(), snapshot.type().name(), profile,
                snapshot.scoring().error() == null ? null
                        : "This league's scoring could not become a profile: " + snapshot.scoring().error(),
                write(slots), snapshot.playoffTeams(), snapshot.playoffWeekStart(),
                snapshot.tradeDeadline(), myTeam, write(stored), Instant.now(clock));
    }

    /** {@code leagues.unmodeled_scoring}: everything the member is told we do not model. */
    record Stored(Map<String, Double> scoring, Map<String, Integer> slots, List<String> approximations) {}

    private Stored read(String text) {
        try {
            Stored stored = json.readValue(text, Stored.class);
            return new Stored(stored.scoring() == null ? Map.of() : stored.scoring(),
                    stored.slots() == null ? Map.of() : stored.slots(),
                    stored.approximations() == null ? List.of() : stored.approximations());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unreadable leagues.unmodeled_scoring", e);
        }
    }

    private Map<String, Integer> readSlots(String text) {
        try {
            Map<String, Integer> slots = json.readValue(text, new TypeReference<LinkedHashMap<String, Integer>>() {});
            Map<String, Integer> ordered = new LinkedHashMap<>();
            slots.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(slotOrder(a.getKey()), slotOrder(b.getKey())))
                    .forEach(e -> ordered.put(e.getKey(), e.getValue()));
            return ordered;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unreadable leagues.roster_slots", e);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise league data", e);
        }
    }

    private static RosterView rosterView(RosterRow spot) {
        if (spot.playerId() != null) {
            return new RosterView(spot.externalPlayerId(), spot.slot(), "PLAYER", spot.playerId(),
                    spot.name(), spot.position(), spot.team(), spot.espnId());
        }
        if (TEAM_DEFENSE.matcher(spot.externalPlayerId()).matches()) {
            return new RosterView(spot.externalPlayerId(), spot.slot(), "TEAM_DEFENSE", null,
                    spot.externalPlayerId() + " D/ST", "DST", spot.externalPlayerId(), null);
        }
        return new RosterView(spot.externalPlayerId(), spot.slot(), "UNMATCHED", null, null, null,
                null, null);
    }

    /** Lineup order: RosterSlot's own order, then TAXI and anything unmodelled. */
    private static int slotOrder(String slot) {
        try {
            return RosterSlot.valueOf(slot).ordinal();
        } catch (IllegalArgumentException e) {
            return RosterSlot.values().length + ("TAXI".equals(slot) ? 0 : 1);
        }
    }

    private static Set<String> playerIds(LeagueSnapshot snapshot) {
        return snapshot.roster().stream().map(RosterEntry::externalPlayerId).collect(Collectors.toSet());
    }

    private LeagueProvider provider(String platform) {
        LeagueProvider provider = providers.get(platform);
        if (provider == null) {
            throw new IllegalArgumentException("not a supported league platform: " + platform);
        }
        return provider;
    }

    private static LeagueNotFoundException notFound(long leagueId) {
        return new LeagueNotFoundException("No league with id " + leagueId + ".");
    }
}
