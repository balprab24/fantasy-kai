package com.fantasykai.leagues;

import com.fantasykai.leagues.LeagueRepository.LeagueRow;
import com.fantasykai.leagues.LeagueRepository.RosterRow;
import com.fantasykai.leagues.LineupOptimizer.Candidate;
import com.fantasykai.leagues.LineupOptimizer.Lineup;
import com.fantasykai.leagues.LineupOptimizer.Spot;
import com.fantasykai.leagues.RestOfSeason.Outlook;
import com.fantasykai.leagues.RestOfSeason.Projection;
import com.fantasykai.scoring.ExplainedScore;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.ScoringProfiles;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * "Weak at QB, RB or WR", answered from the league itself (plan S2): every team's best lineup
 * for every remaining week, each starting slot averaged over those weeks, and the member's team
 * against the league median of the same slot.
 *
 * <p>Slots are named by strength within their kind -- WR1 is whichever of a team's starting
 * receivers projects highest that week -- so "your WR2 against the league's median WR2" compares
 * like with like whoever happens to be listed first.
 *
 * <p>Nothing is stored. Points travel unrounded until {@link #view}, which rounds each once.
 */
@Service
public class TeamNeeds {

    /** What a starting slot is called on screen, before its number. */
    private static final Map<RosterSlot, String> LABELS = Map.of(
            RosterSlot.QB, "QB", RosterSlot.RB, "RB", RosterSlot.WR, "WR", RosterSlot.TE, "TE",
            RosterSlot.FLEX_RWT, "Flex", RosterSlot.FLEX_WR, "W/R flex", RosterSlot.FLEX_WT, "W/T flex",
            RosterSlot.SUPERFLEX, "Superflex", RosterSlot.K, "K", RosterSlot.DST, "D/ST");

    /**
     * @param mine    the member's average expected points in this slot; null when not modelled
     * @param median  the league median of the same slot
     * @param diff    {@code mine - median}
     * @param starter who fills it most often over the remaining weeks; null if no one does
     */
    public record SlotNeed(String slot, String label, boolean modeled, Double mine, Double median,
            Double diff, Long starterPlayerId, String starterName) {}

    public record WeekPoints(int week, double points) {}

    /**
     * One player on the member's roster over the remaining weeks.
     *
     * @param status       PROJECTED, NO_PROJECTION or NOT_MODELED
     * @param availability what is known about the first remaining week
     * @param perWeek      the mean over the remaining weeks, byes and absences counted as 0
     * @param perGame      his projected line scored, when he plays
     */
    public record PlayerOutlookView(String externalPlayerId, Long playerId, String status,
            String availability, Double perWeek, Double perGame, List<WeekPoints> weeks,
            List<ExplainedScore.Part> parts, Double bonusPoints) {}

    /**
     * @param weeks        the regular-season weeks left before the league's playoffs
     * @param slots        empty until the member has picked a team
     * @param noProjection rostered skill players with no game to project from, league-wide
     */
    public record View(int season, List<Integer> weeks, String myTeamExternalId, List<SlotNeed> slots,
            List<PlayerOutlookView> players, int noProjection) {}

    private final LeagueRepository leagues;
    private final RestOfSeason restOfSeason;
    private final ScoringProfiles profiles;
    private final ObjectMapper json;

    public TeamNeeds(LeagueRepository leagues, RestOfSeason restOfSeason, ScoringProfiles profiles,
            ObjectMapper json) {
        this.leagues = leagues;
        this.restOfSeason = restOfSeason;
        this.profiles = profiles;
        this.json = json;
    }

    public View view(long userId, long leagueId) {
        LeagueRow league = leagues.find(leagueId, userId)
                .orElseThrow(() -> new LeagueNotFoundException("No league with id " + leagueId + "."));
        if (league.scoringProfileId() == null) {
            throw new LeagueNotScoredException(league.scoringNote() != null ? league.scoringNote()
                    : "This league has no ruleset to project with. Sync it to rebuild one.");
        }
        // Through the ownership filter, like every profile read.
        ResolvedRuleset rules = profiles.byId(league.scoringProfileId(), userId);
        List<RosterRow> roster = leagues.roster(leagueId, userId);
        Map<Long, String> positions = new HashMap<>();
        roster.stream().filter(r -> r.playerId() != null && r.position() != null)
                .forEach(r -> positions.put(r.playerId(), r.position()));
        Projection projection = restOfSeason.project(league.season(), league.playoffWeekStart(), positions, rules);
        Map<RosterSlot, Integer> slots = slots(league.rosterSlots());

        // team -> instance key -> points per week; and who filled it each week
        Map<String, Map<String, List<Double>>> points = new LinkedHashMap<>();
        Map<String, Map<String, Map<Long, Filled>>> fillers = new HashMap<>();
        Map<String, List<RosterRow>> byTeam = new LinkedHashMap<>();
        roster.forEach(r -> byTeam.computeIfAbsent(r.externalTeamId(), t -> new ArrayList<>()).add(r));
        for (var team : byTeam.entrySet()) {
            for (int week : projection.weeks()) {
                List<Candidate> candidates = new ArrayList<>();
                for (RosterRow spot : team.getValue()) {
                    Outlook outlook = spot.playerId() == null ? null : projection.players().get(spot.playerId());
                    if (outlook != null && !"IR".equals(spot.slot()) && !"TAXI".equals(spot.slot())) {
                        candidates.add(new Candidate(spot.playerId(), outlook.position(), outlook.week(week)));
                    }
                }
                Lineup lineup = LineupOptimizer.solve(slots, candidates);
                instances(lineup).forEach((key, spot) -> {
                    points.computeIfAbsent(team.getKey(), t -> new LinkedHashMap<>())
                            .computeIfAbsent(key, k -> new ArrayList<>()).add(spot.points());
                    if (spot.playerId() != null) {
                        fillers.computeIfAbsent(team.getKey(), t -> new HashMap<>())
                                .computeIfAbsent(key, k -> new HashMap<>())
                                .merge(spot.playerId(), new Filled(1, spot.points()), Filled::plus);
                    }
                });
            }
        }

        String mine = league.myTeamExternalId();
        List<SlotNeed> needs = new ArrayList<>();
        if (mine != null && !projection.weeks().isEmpty()) {
            Map<Long, String> names = new HashMap<>();
            roster.stream().filter(r -> r.playerId() != null).forEach(r -> names.put(r.playerId(), r.name()));
            for (String key : instanceKeys(slots)) {
                RosterSlot slot = RosterSlot.valueOf(key.substring(0, key.indexOf('#')));
                String label = label(slot, key, slots.get(slot));
                if (slot == RosterSlot.K || slot == RosterSlot.DST) {
                    needs.add(new SlotNeed(slot.name(), label, false, null, null, null, null, null));
                    continue;
                }
                List<Double> averages = new ArrayList<>();
                byTeam.keySet().forEach(team -> averages.add(average(points, team, key)));
                double myAverage = average(points, mine, key);
                double median = median(averages);
                // Most weeks in the slot, then most points there, then the lower id.
                Long starter = fillers.getOrDefault(mine, Map.of()).getOrDefault(key, Map.of()).entrySet().stream()
                        .max(Comparator.<Map.Entry<Long, Filled>>comparingInt(e -> e.getValue().weeks())
                                .thenComparingDouble(e -> e.getValue().points())
                                .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                        .map(Map.Entry::getKey).orElse(null);
                needs.add(new SlotNeed(slot.name(), label, true, round(myAverage), round(median),
                        round(myAverage - median), starter, starter == null ? null : names.get(starter)));
            }
        }

        if (mine != null && !projection.weeks().isEmpty()) {
            // Starting slots no RosterSlot covers (IDP): reported, never filled or guessed.
            unmodeledSlots(league.unmodeled()).forEach((name, count) -> {
                for (int i = 1; i <= count; i++) {
                    needs.add(new SlotNeed(name, count > 1 ? name + i : name, false, null, null, null, null, null));
                }
            });
        }

        List<PlayerOutlookView> players = new ArrayList<>();
        if (mine != null) {
            for (RosterRow spot : byTeam.getOrDefault(mine, List.of())) {
                Outlook outlook = spot.playerId() == null ? null : projection.players().get(spot.playerId());
                if (outlook == null) {
                    continue;
                }
                ExplainedScore perGame = outlook.perGame();
                players.add(new PlayerOutlookView(spot.externalPlayerId(), spot.playerId(),
                        outlook.status().name(), outlook.availability().name(),
                        outlook.status() == RestOfSeason.Status.PROJECTED ? round(outlook.mean()) : null,
                        perGame == null ? null : round(perGame.total()),
                        outlook.weekly().entrySet().stream().map(e -> new WeekPoints(e.getKey(), round(e.getValue()))).toList(),
                        perGame == null ? List.of() : perGame.parts(),
                        perGame == null ? null : perGame.bonusPoints()));
            }
        }
        int noProjection = (int) projection.players().values().stream()
                .filter(o -> o.status() == RestOfSeason.Status.NO_PROJECTION).count();
        return new View(league.season(), projection.weeks(), mine, needs, players, noProjection);
    }

    /** How often, and for how much, one player filled one slot instance. */
    private record Filled(int weeks, double points) {
        Filled plus(Filled other) {
            return new Filled(weeks + other.weeks, points + other.points);
        }
    }

    /** A lineup's modelled and unmodelled spots, keyed "WR#2": second-strongest WR that week. */
    static Map<String, Spot> instances(Lineup lineup) {
        Map<RosterSlot, List<Spot>> bySlot = new EnumMap<>(RosterSlot.class);
        lineup.spots().forEach(s -> bySlot.computeIfAbsent(s.slot(), k -> new ArrayList<>()).add(s));
        Map<String, Spot> keyed = new LinkedHashMap<>();
        bySlot.forEach((slot, spots) -> {
            spots.sort(Comparator.comparingDouble(Spot::points).reversed());
            for (int i = 0; i < spots.size(); i++) {
                keyed.put(slot.name() + "#" + (i + 1), spots.get(i));
            }
        });
        return keyed;
    }

    private static List<String> instanceKeys(Map<RosterSlot, Integer> slots) {
        List<String> keys = new ArrayList<>();
        for (RosterSlot slot : RosterSlot.values()) {
            int count = slot.starts() ? slots.getOrDefault(slot, 0) : 0;
            for (int i = 1; i <= count; i++) {
                keys.add(slot.name() + "#" + i);
            }
        }
        return keys;
    }

    private static String label(RosterSlot slot, String key, int count) {
        String base = LABELS.getOrDefault(slot, slot.name());
        if (count <= 1) {
            return base;
        }
        String n = key.substring(key.indexOf('#') + 1);
        return base.length() <= 2 ? base + n : base + " " + n;
    }

    private static double average(Map<String, Map<String, List<Double>>> points, String team, String key) {
        return points.getOrDefault(team, Map.of()).getOrDefault(key, List.of()).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
    }

    static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n == 0) {
            return 0;
        }
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    /** {@code leagues.unmodeled_scoring.slots}: lineup slots the league starts that we do not model. */
    private Map<String, Integer> unmodeledSlots(String text) {
        try {
            Map<String, Integer> slots = json.readTree(text).has("slots")
                    ? json.convertValue(json.readTree(text).get("slots"),
                            new TypeReference<TreeMap<String, Integer>>() {})
                    : Map.of();
            return slots == null ? Map.of() : slots;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unreadable leagues.unmodeled_scoring", e);
        }
    }

    private Map<RosterSlot, Integer> slots(String text) {
        try {
            Map<String, Integer> raw = json.readValue(text, new TypeReference<LinkedHashMap<String, Integer>>() {});
            Map<RosterSlot, Integer> slots = new EnumMap<>(RosterSlot.class);
            raw.forEach((name, count) -> slots.put(RosterSlot.valueOf(name), count));
            return slots;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unreadable leagues.roster_slots", e);
        }
    }

    /** Once, here, at the boundary. */
    private static double round(double points) {
        return ScoringEngine.roundForDisplay(points);
    }
}
