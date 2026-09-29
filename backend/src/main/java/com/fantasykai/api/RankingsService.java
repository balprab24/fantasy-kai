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
import org.springframework.stereotype.Service;

/**
 * Ranks players by points scored under a caller-chosen ruleset.
 *
 * <p><strong>This is the endpoint §9 is about.</strong> It reads every scorable
 * player-week for the season, scores each one in Java, aggregates, sorts and
 * only then takes a page -- so the work is proportional to the season's row
 * count and independent of {@code size}, and it repeats in full for every
 * concurrent caller. That is the compute-bound baseline; Phase 11's cache is what
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

    /**
     * @param userId the authenticated caller, or {@code null} when logged out.
     *     Carried down to the profile lookup rather than checked here, so the
     *     tenant filter stays in the query (§8). An anonymous caller resolves
     *     system presets and nothing else.
     */
    public PageResponse<RankingRow> rank(long profileId, Long userId, String position, int season,
            RankingScope scope, int page, int size) {

        // Resolved before any query: an unknown profile is a 404, not an empty
        // ranking -- and so is one belonging to somebody else.
        ResolvedRuleset rules = profiles.byId(profileId, userId);
        List<String> positions = ScoringPosition.resolve(position);

        Integer weekFloor = null;
        if (scope == RankingScope.LAST4) {
            Optional<Integer> latest = players.latestRegularWeek(season);
            if (latest.isEmpty()) {
                return PageResponse.of(List.of(), page, size, 0);
            }
            weekFloor = Math.max(1, latest.get() - RankingScope.LAST_N_WEEKS + 1);
        }

        Map<Long, Entry> byPlayer = new HashMap<>();
        for (ScorableRow row : players.findScorableRows(season, positions, weekFloor)) {
            byPlayer.computeIfAbsent(row.playerId(), id -> new Entry(row, new PointsTally(id)))
                    .tally().add(row.week(), ScoringEngine.score(row.line(), rules));
        }

        // Descending by the scope's metric, then by id so a page boundary that
        // falls inside a tie is stable across requests. The same comparator
        // orders a career's positional rank, which is what makes the two agree.
        Comparator<PointsTally> order =
                scope == RankingScope.PER_GAME ? PointsTally.BY_PER_GAME : PointsTally.BY_POINTS;
        List<Entry> ranked = byPlayer.values().stream()
                .sorted(Comparator.comparing(Entry::tally, order))
                .toList();

        int from = Math.min((int) Math.min((long) page * size, Integer.MAX_VALUE), ranked.size());
        int to = Math.min(from + size, ranked.size());
        List<Entry> slice = ranked.subList(from, to);

        // Identity for display, fetched for this page only -- after the sort, so
        // the SQL the §9 baseline measures is unchanged. Not free: one more round
        // trip per request (a PK lookup, ~0.15 ms), and PointsTally keeps a
        // record per row and sorts each player's weeks. Phase 11 compares with
        // both in.
        Map<Long, String> espnIds = players.findEspnIds(
                slice.stream().map(entry -> entry.tally().playerId()).toList());

        List<RankingRow> content = new java.util.ArrayList<>(slice.size());
        for (int i = 0; i < slice.size(); i++) {
            ScorableRow who = slice.get(i).who();
            PointsTally tally = slice.get(i).tally();
            content.add(new RankingRow(from + i + 1, tally.playerId(), who.name(), who.position(),
                    who.team(), tally.games(),
                    ScoringEngine.roundForDisplay(tally.points()),
                    ScoringEngine.roundForDisplay(tally.perGame()),
                    espnIds.get(tally.playerId())));
        }
        return PageResponse.of(content, page, size, ranked.size());
    }

    /**
     * A player's identity, taken from the first row seen -- name, position and
     * current team are the same on every row -- and his running tally.
     */
    private record Entry(ScorableRow who, PointsTally tally) {}
}
