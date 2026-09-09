package com.fantasykai.api;

import com.fantasykai.query.GamelogRow;
import com.fantasykai.query.PlayerQueryRepository;
import com.fantasykai.query.PlayerRow;
import com.fantasykai.query.PlayerSort;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.ScoringProfiles;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Player directory and game log. */
@Service
public class PlayerService {

    private final PlayerQueryRepository players;
    private final ScoringProfiles profiles;

    public PlayerService(PlayerQueryRepository players, ScoringProfiles profiles) {
        this.players = players;
        this.profiles = profiles;
    }

    public PageResponse<PlayerSummary> list(String position, String team, Integer season,
            String sort, boolean descending, int page, int size) {

        // Resolved to a whitelist constant before it can reach an ORDER BY.
        PlayerSort resolved = PlayerSort.from(sort);
        List<PlayerSummary> content = players
                .findPlayers(position, team, season, resolved, descending, page, size).stream()
                .map(row -> new PlayerSummary(row.id(), row.name(), row.position(),
                        row.team(), row.status()))
                .toList();

        return PageResponse.of(content, page, size,
                players.countPlayers(position, team, season));
    }

    public PlayerDetail byId(long id) {
        PlayerRow row = players.findById(id)
                .orElseThrow(() -> new PlayerNotFoundException(id));
        return new PlayerDetail(row.id(), row.gsisId(), row.name(), row.position(),
                row.team(), row.status());
    }

    /**
     * A player's weeks, each scored under the requested profile.
     *
     * <p>The season total is accumulated unrounded and rounded once, so it is the
     * rounded sum of the weeks rather than the sum of the rounded weeks -- two
     * numbers that drift apart by a cent per week and would make the game log
     * disagree with the ranking.
     */
    /** @param userId the authenticated caller, or {@code null}. See {@code RankingsService.rank}. */
    public GamelogResponse gamelog(long playerId, Integer season, long profileId, Long userId) {
        PlayerRow player = players.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException(playerId));
        ResolvedRuleset rules = profiles.byId(profileId, userId);

        List<GamelogRow> rows = players.findGamelog(playerId, season);
        List<GamelogWeek> weeks = new ArrayList<>(rows.size());
        double total = 0;

        for (GamelogRow row : rows) {
            // The stat line is stored without a position; scoring needs one so
            // that a TE-premium override actually applies to the player's catches.
            StatLine line = new StatLine(player.position(), row.line().values());
            double points = ScoringEngine.score(line, rules);
            total += points;
            weeks.add(new GamelogWeek(row.season(), row.week(), row.seasonType(),
                    row.opponent(), row.snapPct(), asJsonKeys(line),
                    ScoringEngine.roundForDisplay(points)));
        }

        return new GamelogResponse(player.id(), player.name(), player.position(), season,
                profileId, weeks.size(), ScoringEngine.roundForDisplay(total), weeks);
    }

    /** Renders stats under their ruleset names, in {@link StatKey} order. */
    private static Map<String, Double> asJsonKeys(StatLine line) {
        Map<String, Double> stats = new LinkedHashMap<>();
        for (StatKey stat : StatKey.values()) {
            stats.put(stat.json(), line.get(stat));
        }
        return stats;
    }
}
