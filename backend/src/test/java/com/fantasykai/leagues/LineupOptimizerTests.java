package com.fantasykai.leagues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.leagues.LineupOptimizer.Candidate;
import com.fantasykai.leagues.LineupOptimizer.Lineup;
import com.fantasykai.leagues.LineupOptimizer.Spot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LineupOptimizerTests {

    private static Candidate p(long id, String position, double points) {
        return new Candidate(id, position, points);
    }

    private static List<Long> starters(Lineup lineup, RosterSlot slot) {
        return lineup.spots().stream().filter(s -> s.slot() == slot).map(Spot::playerId).toList();
    }

    /**
     * Plan A8's reason for an exact solve. Filling W/R first takes the WR (10), leaving the W/T
     * flex a TE worth 1: 11. The best lineup puts the RB at W/R and the WR at W/T: 19.
     */
    @Test
    void overlappingFlexesBeatGreedy() {
        Lineup lineup = LineupOptimizer.solve(Map.of(RosterSlot.FLEX_WR, 1, RosterSlot.FLEX_WT, 1),
                List.of(p(1, "WR", 10), p(2, "RB", 9), p(3, "TE", 1)));

        assertThat(lineup.total()).isEqualTo(19.0);
        assertThat(starters(lineup, RosterSlot.FLEX_WR)).containsExactly(2L);
        assertThat(starters(lineup, RosterSlot.FLEX_WT)).containsExactly(1L);
    }

    @Test
    void superflexTakesTheSecondQuarterbackWhenHeIsWorthMore() {
        Lineup lineup = LineupOptimizer.solve(Map.of(RosterSlot.QB, 1, RosterSlot.WR, 1, RosterSlot.SUPERFLEX, 1),
                List.of(p(1, "QB", 20), p(2, "QB", 17), p(3, "WR", 12), p(4, "WR", 9)));

        assertThat(starters(lineup, RosterSlot.QB)).containsExactly(1L);
        assertThat(starters(lineup, RosterSlot.SUPERFLEX)).containsExactly(2L);
        assertThat(lineup.total()).isEqualTo(49.0);
    }

    /** A starter on bye projects 0 that week; the bench player takes his slot. */
    @Test
    void aByeForcesTheBenchIn() {
        Lineup lineup = LineupOptimizer.solve(Map.of(RosterSlot.RB, 1),
                List.of(p(1, "RB", 0.0), p(2, "RB", 6.5)));

        assertThat(starters(lineup, RosterSlot.RB)).containsExactly(2L);
    }

    /** A short roster leaves a slot empty at 0; it never moves a player out of position. */
    @Test
    void anEmptySlotScoresZeroAndNoPlayerIsOutOfPosition() {
        Lineup lineup = LineupOptimizer.solve(Map.of(RosterSlot.QB, 1, RosterSlot.TE, 1),
                List.of(p(1, "QB", 18), p(2, "WR", 25)));

        assertThat(starters(lineup, RosterSlot.TE)).containsExactly((Long) null);
        assertThat(lineup.total()).isEqualTo(18.0);
        assertThat(lineup.spots()).filteredOn(s -> s.playerId() != null && s.playerId() == 2L).isEmpty();
    }

    @Test
    void kickerAndDefenseAreListedUnmodelledAndUnfilled() {
        Lineup lineup = LineupOptimizer.solve(Map.of(RosterSlot.K, 1, RosterSlot.DST, 1, RosterSlot.BENCH, 6),
                List.of(p(1, "K", 9)));

        assertThat(lineup.spots()).hasSize(2).allSatisfy(s -> {
            assertThat(s.modeled()).isFalse();
            assertThat(s.playerId()).isNull();
        });
        assertThat(lineup.total()).isZero();
    }

    @Test
    void equalProjectionsResolveTheSameWayWhateverTheInputOrder() {
        List<Candidate> players = new ArrayList<>(List.of(p(7, "WR", 10), p(3, "WR", 10), p(5, "WR", 10)));
        Lineup first = LineupOptimizer.solve(Map.of(RosterSlot.WR, 2), players);
        Collections.reverse(players);
        Lineup second = LineupOptimizer.solve(Map.of(RosterSlot.WR, 2), players);

        assertThat(second.spots()).isEqualTo(first.spots());
    }

    /**
     * The solver against exhaustive search on 500 random rosters with every flex: exact, not
     * close. Any greedy or partial method fails this within a few seeds.
     */
    @Test
    void matchesExhaustiveSearchOnRandomRosters() {
        Random random = new Random(20261030L);
        String[] positions = {"QB", "RB", "WR", "TE"};
        for (int trial = 0; trial < 500; trial++) {
            Map<RosterSlot, Integer> slots = new EnumMap<>(RosterSlot.class);
            for (RosterSlot slot : List.of(RosterSlot.QB, RosterSlot.RB, RosterSlot.WR, RosterSlot.TE,
                    RosterSlot.FLEX_RWT, RosterSlot.FLEX_WR, RosterSlot.FLEX_WT, RosterSlot.SUPERFLEX)) {
                int count = random.nextInt(2);
                if (count > 0) {
                    slots.put(slot, count);
                }
            }
            List<Candidate> players = new ArrayList<>();
            int n = 1 + random.nextInt(7);
            for (int i = 0; i < n; i++) {
                players.add(p(i, positions[random.nextInt(4)], Math.round(random.nextDouble() * 250) / 10.0 - 1));
            }
            double exact = LineupOptimizer.solve(slots, players).total();
            assertThat(exact).as("trial %d: %s %s", trial, slots, players)
                    .isCloseTo(bruteForce(slots, players), within(1e-9));
        }
    }

    private static double bruteForce(Map<RosterSlot, Integer> slots, List<Candidate> players) {
        List<RosterSlot> instances = new ArrayList<>();
        slots.forEach((slot, count) -> {
            for (int i = 0; i < count; i++) {
                instances.add(slot);
            }
        });
        return best(instances, 0, players, new boolean[players.size()]);
    }

    private static double best(List<RosterSlot> instances, int at, List<Candidate> players, boolean[] used) {
        if (at == instances.size()) {
            return 0;
        }
        double best = best(instances, at + 1, players, used); // leave this slot empty
        for (int i = 0; i < players.size(); i++) {
            Candidate player = players.get(i);
            if (!used[i] && instances.get(at).eligible().contains(player.position())) {
                used[i] = true;
                best = Math.max(best, player.points() + best(instances, at + 1, players, used));
                used[i] = false;
            }
        }
        return best;
    }
}
