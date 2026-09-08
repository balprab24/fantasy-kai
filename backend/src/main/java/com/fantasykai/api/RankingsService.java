package com.fantasykai.api;

import com.fantasykai.query.PlayerQueryRepository;
import com.fantasykai.query.RankingScope;
import com.fantasykai.query.ScorableRow;
import com.fantasykai.query.ScoringPosition;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.ScoringEngine;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Service;

/**
 * Ranks players by points scored under a caller-chosen ruleset.
 *
 * <p><strong>This is the endpoint §9 is about.</strong> It reads every scorable
 * player-week for the season, scores each one in Java, aggregates, sorts and
 * only then takes a page -- so the work is proportional to the season's row
 * count and independent of {@code size}, and it repeats in full for every
 * concurrent caller. That is the compute-bound baseline; Phase 6's cache is what
 * removes it, keyed on {@link ResolvedRuleset#hash()} so two users with
 * identical league settings share one entry.
 *
 * <p>Scoring is per game and then summed, never a single score over summed
 * stats. Threshold bonuses make the evaluator non-linear -- a "100+ rush yards"
 * bonus belongs to a game, not to a season -- and §6 requires the rounding to
 * happen once, here at the boundary, so a season total is the rounded sum of
 * weeks rather than the sum of rounded weeks.
 */
@Service
public class RankingsService {

    private final PlayerQueryRepository players;
    private final com.fantasykai.scoring.ScoringProfiles profiles;

    public RankingsService(PlayerQueryRepository players,
            com.fantasykai.scoring.ScoringProfiles profiles) {
        this.players = players;
        this.profiles = profiles;
    }

    public PageResponse<RankingRow> rank(long profileId, String position, int season,
            RankingScope scope, int page, int size) {

        // Resolved before any query: an unknown profile is a 404, not an empty ranking.
        ResolvedRuleset rules = profiles.byId(profileId);
        List<String> positions = ScoringPosition.resolve(position);

        Integer weekFloor = null;
        if (scope == RankingScope.LAST4) {
            Optional<Integer> latest = players.latestRegularWeek(season);
            if (latest.isEmpty()) {
                return PageResponse.of(List.of(), page, size, 0);
            }
            weekFloor = Math.max(1, latest.get() - RankingScope.LAST_N_WEEKS + 1);
        }

        Map<Long, Tally> byPlayer = new HashMap<>();
        for (ScorableRow row : players.findScorableRows(season, positions, weekFloor)) {
            byPlayer.computeIfAbsent(row.playerId(), id -> new Tally(row))
                    .add(ScoringEngine.score(row.line(), rules));
        }

        ToDoubleFunction<Tally> metric =
                scope == RankingScope.PER_GAME ? Tally::perGame : Tally::points;
        // Descending by the scope's metric, then by id so a page boundary that
        // falls inside a tie is stable across requests.
        List<Tally> ranked = byPlayer.values().stream()
                .sorted(Comparator.comparingDouble(metric).reversed()
                        .thenComparingLong(Tally::playerId))
                .toList();

        int from = Math.min((int) Math.min((long) page * size, Integer.MAX_VALUE), ranked.size());
        int to = Math.min(from + size, ranked.size());

        List<RankingRow> content = new java.util.ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            Tally tally = ranked.get(i);
            content.add(new RankingRow(i + 1, tally.playerId, tally.name, tally.position,
                    tally.team, tally.games,
                    ScoringEngine.roundForDisplay(tally.points()),
                    ScoringEngine.roundForDisplay(tally.perGame())));
        }
        return PageResponse.of(content, page, size, ranked.size());
    }

    /** Running unrounded total for one player. Rounding happens at the boundary, above. */
    private static final class Tally {

        private final long playerId;
        private final String name;
        private final String position;
        private final String team;
        private double points;
        private int games;

        private Tally(ScorableRow row) {
            this.playerId = row.playerId();
            this.name = row.name();
            this.position = row.position();
            this.team = row.team();
        }

        private void add(double weekPoints) {
            points += weekPoints;
            games++;
        }

        private long playerId() {
            return playerId;
        }

        private double points() {
            return points;
        }

        private double perGame() {
            return games == 0 ? 0 : points / games;
        }
    }
}
