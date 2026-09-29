package com.fantasykai.api;

import com.fantasykai.query.GamelogRow;
import com.fantasykai.query.PlayerQueryRepository;
import com.fantasykai.query.PlayerRow;
import com.fantasykai.query.PlayerSort;
import com.fantasykai.query.PositionWeekRow;
import com.fantasykai.query.ScoringPosition;
import com.fantasykai.query.Usage;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.ScoringProfiles;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/** Player directory, game log and career. */
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
                row.team(), row.status(), row.espnId(), row.birthDate(),
                row.teamName(), row.teamLogo());
    }

    /**
     * A player's weeks, each scored under the requested profile.
     *
     * <p>The season total is accumulated unrounded and rounded once, so it is the
     * rounded sum of the weeks rather than the sum of the rounded weeks -- two
     * numbers that drift apart by a cent per week and would make the game log
     * disagree with the ranking.
     *
     * <p>Every week recorded, playoffs included, and so is the total: a log is a
     * record of what happened. The regular-season numbers a board shows are the
     * {@link #career} endpoint's.
     *
     * @param userId the authenticated caller, or {@code null}. See {@code RankingsService.rank}.
     */
    public GamelogResponse gamelog(long playerId, Integer season, long profileId, Long userId) {
        PlayerRow player = players.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException(playerId));
        ResolvedRuleset rules = profiles.byId(profileId, userId);

        List<GamelogRow> rows = players.findGamelog(playerId, season);
        List<GamelogWeek> weeks = new ArrayList<>(rows.size());
        double total = 0;

        for (GamelogRow row : rows) {
            StatLine line = positioned(row, player);
            double points = ScoringEngine.score(line, rules);
            total += points;
            weeks.add(new GamelogWeek(row.season(), row.week(), row.seasonType(), row.team(),
                    row.opponent(), row.home(), row.snapPct(), row.usage(), asJsonKeys(line),
                    ScoringEngine.roundForDisplay(points)));
        }

        return new GamelogResponse(player.id(), player.name(), player.position(), season,
                profileId, weeks.size(), ScoringEngine.roundForDisplay(total), weeks);
    }

    /**
     * Every regular season a player has a stat line in, scored under one profile,
     * with where he stood at his position -- for the season and for each week.
     *
     * <p>Two reads. The player's own rows are a primary-key lookup. The ranks need
     * every player at his position in those seasons, which is one full scan of
     * {@code player_game_stats} -- the same cost class as a ranking request, and
     * paid once per (player, profile): a chart, a log and a season switch all read
     * from this one answer. A position v1 does not rank (K, OL, defence) skips
     * the scan and gets no ranks rather than a 400.
     *
     * <p>Season ranks use {@link PointsTally#BY_POINTS}, the ranking's own order,
     * over tallies summed in week order -- so a season's {@code posRank} is the
     * positional rank the board derives for him, not merely close to it.
     */
    public CareerResponse career(long playerId, long profileId, Long userId) {
        PlayerRow player = players.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException(playerId));
        // Before any stats query: another user's profile is a 404 either way, but
        // it should cost nothing to find that out.
        ResolvedRuleset rules = profiles.byId(profileId, userId);

        Map<Integer, List<GamelogRow>> bySeason = new TreeMap<>(Comparator.reverseOrder());
        Set<Integer> played = new TreeSet<>(Comparator.reverseOrder());
        for (GamelogRow row : players.findGamelog(playerId, null)) {
            played.add(row.season());
            if ("REG".equals(row.seasonType())) {
                bySeason.computeIfAbsent(row.season(), s -> new ArrayList<>()).add(row);
            }
        }

        PositionRanks ranks = ScoringPosition.ALL.contains(player.position())
                ? rankAtPosition(player.position(), bySeason.keySet(), rules)
                : PositionRanks.NONE;

        List<CareerSeason> seasons = new ArrayList<>(bySeason.size());
        bySeason.forEach((season, rows) -> {
            PointsTally tally = new PointsTally(playerId);
            double[] stats = new double[StatKey.COUNT];
            Usage usage = Usage.NONE;
            Set<String> teams = new LinkedHashSet<>();
            List<CareerWeek> weeks = new ArrayList<>(rows.size());

            for (GamelogRow row : rows) {
                StatLine line = positioned(row, player);
                double points = ScoringEngine.score(line, rules);
                tally.add(row.week(), points);
                for (int i = 0; i < StatKey.COUNT; i++) {
                    stats[i] += line.values()[i];
                }
                usage = usage.plus(row.usage());
                teams.add(row.team());
                weeks.add(new CareerWeek(row.week(), row.opponent(), row.home(),
                        ScoringEngine.roundForDisplay(points),
                        ranks.week(season, row.week(), points)));
            }

            seasons.add(new CareerSeason(season, List.copyOf(teams),
                    Ages.atSeasonStart(player.birthDate(), season), tally.games(),
                    ScoringEngine.roundForDisplay(tally.points()),
                    ScoringEngine.roundForDisplay(tally.perGame()),
                    ranks.season(season, playerId),
                    asJsonKeys(new StatLine(player.position(), stats)), usage, weeks));
        });

        return new CareerResponse(playerId, profileId, seasons, List.copyOf(played));
    }

    /** Scores every week at the position once, and keeps what a rank needs. */
    private PositionRanks rankAtPosition(String position, Set<Integer> seasons,
            ResolvedRuleset rules) {

        Map<Integer, Map<Long, PointsTally>> tallies = new HashMap<>();
        Map<Long, List<Double>> weekScores = new HashMap<>();

        for (PositionWeekRow row : players.findPositionWeeks(position, seasons)) {
            // Every row here is at this position; the stored line carries none,
            // and a TE-premium override only applies if the line says TE.
            double points = ScoringEngine.score(
                    new StatLine(position, row.line().values()), rules);
            tallies.computeIfAbsent(row.season(), s -> new HashMap<>())
                    .computeIfAbsent(row.playerId(), PointsTally::new)
                    .add(row.week(), points);
            weekScores.computeIfAbsent(weekKey(row.season(), row.week()), k -> new ArrayList<>())
                    .add(ScoringEngine.roundForDisplay(points));
        }

        Map<Integer, Map<Long, Integer>> seasonRanks = new HashMap<>();
        tallies.forEach((season, byPlayer) -> {
            List<PointsTally> order = byPlayer.values().stream()
                    .sorted(PointsTally.BY_POINTS)
                    .toList();
            Map<Long, Integer> places = new HashMap<>();
            for (int i = 0; i < order.size(); i++) {
                places.put(order.get(i).playerId(), i + 1);
            }
            seasonRanks.put(season, places);
        });
        return new PositionRanks(seasonRanks, weekScores);
    }

    /**
     * Where a player stood at his position.
     *
     * <p>Two tie rules, deliberately different. A season place is one per player
     * with the id tiebreak, because it must equal the board's. A week has no
     * board to agree with, so players level on points share a place -- "1 + how
     * many scored more", compared on the points the API returns (to the cent),
     * so a last-bit difference never splits two equal weeks. The page shows one
     * decimal: 12.34 and 12.30 both read "12.3" and still rank apart.
     */
    private record PositionRanks(Map<Integer, Map<Long, Integer>> seasons,
            Map<Long, List<Double>> weeks) {

        static final PositionRanks NONE = new PositionRanks(Map.of(), Map.of());

        Integer season(int season, long playerId) {
            return seasons.getOrDefault(season, Map.of()).get(playerId);
        }

        Integer week(int season, int week, double points) {
            List<Double> scores = weeks.get(weekKey(season, week));
            if (scores == null) {
                return null;
            }
            double shown = ScoringEngine.roundForDisplay(points);
            int ahead = 0;
            for (double other : scores) {
                if (other > shown) {
                    ahead++;
                }
            }
            return ahead + 1;
        }
    }

    private static long weekKey(int season, int week) {
        return season * 100L + week;
    }

    /**
     * The stat line is stored without a position; scoring needs one so that a
     * TE-premium override actually applies to the player's catches.
     */
    private static StatLine positioned(GamelogRow row, PlayerRow player) {
        return new StatLine(player.position(), row.line().values());
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
