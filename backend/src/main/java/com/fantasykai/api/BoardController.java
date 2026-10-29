package com.fantasykai.api;

import com.fantasykai.query.PlayerQueryRepository;
import com.fantasykai.query.RankingScope;
import com.fantasykai.query.ScorableRow;
import com.fantasykai.query.ScoringPosition;
import com.fantasykai.query.Usage;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.ScoringProfiles;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// No @Validated: see RankingsController.

/**
 * The member board: {@code /rankings}' rows plus what the board shows beside
 * them -- position ranks, recent form, the last four weeks, volume.
 *
 * <p>Every rank here is counted over the <strong>whole</strong> eligible
 * population before a page is cut, never from the rows a browser happens to
 * hold: a WR on page two is still WR75, not WR1 of its page.
 *
 * <p>The cost is stated, not hidden. Each request ranks the whole season (as
 * {@code /rankings} already does at any {@code size}) and then scores the
 * season once more for the weekly figures, so a 200-row page costs two
 * season scans and one usage scan. {@code /players/{id}/performance} pays the
 * same to return one row. Fine at this traffic; the caching that should
 * replace it is the Phase 11 perf pass.
 */
@RestController
@RequestMapping("/api/v1")
public class BoardController {

    /** A player qualifies for a per-game rank at half the leader's games -- {@code QUALIFYING_SHARE} in {@code lib/board.ts}. */
    private static final double QUALIFYING_SHARE = 0.5;

    private final RankingsService rankings;
    private final PlayerQueryRepository players;
    private final ScoringProfiles profiles;

    public BoardController(RankingsService rankings, PlayerQueryRepository players,
            ScoringProfiles profiles) {
        this.rankings = rankings;
        this.players = players;
        this.profiles = profiles;
    }

    /** One league week of a player's recent form; {@code points} null is no recorded game, not zero. */
    public record Week(int week, Double points) {}

    /**
     * A board row. The ranking's own fields are unwrapped, so to a client this
     * is a {@code RankingRow} with more fields, not a wrapper around one.
     *
     * @param posRank     place among players at the position, over the whole population
     * @param ppgPosRank  place by points per game among qualified players at the
     *                    position; null when the player is not qualified
     * @param recentPpg   points per game over the last four league weeks; null with no game in them
     * @param form        {@code recentPpg} minus the season's per-game; null unless the
     *                    window has two games and the season has more than the window
     * @param recentWeeks the last four league weeks, oldest first
     * @param dataThrough the latest regular-season week with a recorded game; 0 before week 1
     */
    public record Row(@JsonUnwrapped RankingRow ranking, Integer posRank, Integer ppgPosRank,
            Double recentPpg, Double form, List<Week> recentWeeks, int dataThrough, Usage usage) {}

    @GetMapping("/rankings/board")
    public PageResponse<Row> board(
            @RequestParam long profileId,
            @RequestParam @Min(2020) @Max(2100) int season,
            @RequestParam(required = false) String position,
            @RequestParam(defaultValue = "season") String scope,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.MAX_SIZE)
            @Min(1) @Max(PageResponse.MAX_SIZE) int size,
            @AuthenticationPrincipal Long userId) {
        List<Row> all = rows(profileId, userId, position, season, RankingScope.from(scope));
        int start = (int) Math.min((long) page * size, all.size());
        int end = Math.min(start + size, all.size());
        return PageResponse.of(all.subList(start, end), page, size, all.size());
    }

    /** One player's board row, for the player page opened from that board. */
    @GetMapping("/players/{id}/performance")
    public Row performance(
            @PathVariable long id,
            @RequestParam long profileId,
            @RequestParam @Min(2020) @Max(2100) int season,
            @RequestParam(defaultValue = "season") String scope,
            @AuthenticationPrincipal Long userId) {
        return rows(profileId, userId, null, season, RankingScope.from(scope)).stream()
                .filter(row -> row.ranking().playerId() == id)
                .findFirst()
                .orElseThrow(() -> new PlayerNotFoundException(id));
    }

    List<Row> rows(long profileId, Long userId, String position, int season, RankingScope scope) {
        // Resolved first, through the ownership filter: someone else's profile is a 404 here too.
        ResolvedRuleset rules = profiles.byId(profileId, userId);
        List<RankingRow> ranking = rankings
                .rank(profileId, userId, position, season, scope, 0, Integer.MAX_VALUE)
                .content();
        int latest = players.latestRegularWeek(season).orElse(0);
        int firstRecent = Math.max(1, latest - RankingScope.LAST_N_WEEKS + 1);

        // Every game scored once, then summed by week order (PointsTally), as the ranking does.
        Map<Long, PointsTally> wholeSeason = new HashMap<>();
        Map<Long, PointsTally> recent = new HashMap<>();
        Map<Long, Map<Integer, Double>> weekly = new HashMap<>();
        for (ScorableRow row : players.findScorableRows(season, ScoringPosition.resolve(position), null)) {
            double points = ScoringEngine.score(row.line(), rules);
            wholeSeason.computeIfAbsent(row.playerId(), PointsTally::new).add(row.week(), points);
            if (row.week() >= firstRecent) {
                recent.computeIfAbsent(row.playerId(), PointsTally::new).add(row.week(), points);
                weekly.computeIfAbsent(row.playerId(), id -> new HashMap<>())
                        .put(row.week(), ScoringEngine.roundForDisplay(points));
            }
        }

        Map<Long, Integer> ppgRanks = perGameRanks(ranking);
        Map<Long, Usage> usage = players.boardUsage(season, scope == RankingScope.LAST4 ? firstRecent : 1);
        Map<String, Integer> seen = new HashMap<>();
        List<Row> result = new ArrayList<>(ranking.size());
        for (RankingRow row : ranking) {
            long id = row.playerId();
            int posRank = seen.merge(row.position(), 1, Integer::sum);

            List<Week> weeks = new ArrayList<>();
            Map<Integer, Double> played = weekly.getOrDefault(id, Map.of());
            for (int week = firstRecent; week <= latest; week++) {
                weeks.add(new Week(week, played.get(week)));
            }

            PointsTally window = recent.get(id);
            PointsTally whole = wholeSeason.get(id);
            Double recentPpg = window == null ? null : ScoringEngine.roundForDisplay(window.perGame());
            Double form = window == null || window.games() < 2 || whole.games() <= window.games()
                    ? null
                    : ScoringEngine.roundForDisplay(window.perGame() - whole.perGame());

            result.add(new Row(row, posRank, ppgRanks.get(id), recentPpg, form, weeks, latest,
                    usage.getOrDefault(id, Usage.NONE)));
        }
        return result;
    }

    /**
     * Rank by points per game within each position, qualified players only.
     * Ties share a rank (1, 1, 3), matching {@code rankPerGame} in
     * {@code lib/board.ts}, so equal figures always get the same colour.
     */
    static Map<Long, Integer> perGameRanks(List<RankingRow> ranking) {
        int most = ranking.stream().mapToInt(RankingRow::gamesPlayed).max().orElse(0);
        int floor = Math.max(1, (int) Math.ceil(most * QUALIFYING_SHARE));

        Map<String, List<RankingRow>> byPosition = new HashMap<>();
        for (RankingRow row : ranking) {
            if (row.gamesPlayed() >= floor) {
                byPosition.computeIfAbsent(row.position(), p -> new ArrayList<>()).add(row);
            }
        }

        Map<Long, Integer> ranks = new HashMap<>();
        for (List<RankingRow> rows : byPosition.values()) {
            rows.sort(Comparator.comparingDouble(RankingRow::pointsPerGame).reversed());
            for (int i = 0; i < rows.size(); i++) {
                RankingRow row = rows.get(i);
                boolean tied = i > 0 && row.pointsPerGame() == rows.get(i - 1).pointsPerGame();
                ranks.put(row.playerId(), tied ? ranks.get(rows.get(i - 1).playerId()) : i + 1);
            }
        }
        return ranks;
    }
}
