package com.fantasykai.projection;

import com.fantasykai.query.StatColumns;
import com.fantasykai.scoring.StatKey;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Everything the backtest reads, loaded once from the local database: regular-season skill
 * rows, every team's per-game volume, and the schedule with its lines.
 *
 * <p>Only data the pipeline already ingests -- the model that wins has to be buildable in
 * production without a new source. The load is fingerprinted (row counts and per-quantity
 * sums by season, plus a sha256 over the rows) and the sums are checked against a direct
 * SQL {@code SUM}, so a report says exactly which data produced it.
 */
record BacktestData(Map<Long, Timeline<PlayerGame>> players, Map<Integer, Timeline<TeamGame>> teams,
        Map<Integer, Timeline<DefenseGame>> defenses, Timeline<LeagueWeek> league,
        Map<Long, Game> games, Map<Integer, List<Game>> gamesByWeek, List<String> fingerprint) {

    private static final String SKILL_FILTER = """
            JOIN players p ON p.id = s.player_id
            JOIN games g ON g.id = s.game_id
            WHERE g.season_type = 'REG' AND p.position IN ('QB', 'RB', 'WR', 'TE')
            """;

    List<Game> gamesIn(int season, int week) {
        return gamesByWeek.getOrDefault(Dated.key(season, week), List.of());
    }

    /** v1's dataset: the database and nothing else. */
    static BacktestData load(Connection connection) throws SQLException {
        return load(connection, null);
    }

    /**
     * The database, and -- when {@code extras} is given -- Experiment v2's corrections from
     * nflverse: air yards, the week's position, active zero-stat appearances, and a game
     * counted as played once its stat rows exist. Without extras every row is exactly v1's,
     * so v1's committed reports still reproduce.
     */
    static BacktestData load(Connection connection, NflverseExtras extras) throws SQLException {
        Map<Integer, String> abbrById = new HashMap<>();
        Map<String, Integer> idByAbbr = new HashMap<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, abbr FROM teams")) {
            while (rs.next()) {
                abbrById.put(rs.getInt("id"), rs.getString("abbr"));
                idByAbbr.put(rs.getString("abbr"), rs.getInt("id"));
            }
        }

        record Scheduled(Game game, boolean scored) {}
        Map<Long, Scheduled> scheduled = new TreeMap<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT id, nflverse_game_id, season, week, home_team_id, away_team_id,
                               spread_line, total_line, home_score
                          FROM games WHERE season_type = 'REG'
                        """)) {
            while (rs.next()) {
                // wasNull() describes the column read last, so each flag is taken at once.
                double spread = rs.getDouble("spread_line");
                if (rs.wasNull()) {
                    spread = Double.NaN;
                }
                double total = rs.getDouble("total_line");
                if (rs.wasNull()) {
                    total = Double.NaN;
                }
                rs.getInt("home_score");
                boolean scored = !rs.wasNull();
                Game game = new Game(rs.getLong("id"), rs.getString("nflverse_game_id"),
                        rs.getInt("season"), rs.getInt("week"), rs.getInt("home_team_id"),
                        rs.getInt("away_team_id"), spread, total, scored);
                scheduled.put(game.id(), new Scheduled(game, scored));
            }
        }

        Map<String, TeamGame> teamGames = new HashMap<>();
        Map<Integer, int[]> missingTeamAir = new TreeMap<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT s.game_id, s.team_id, g.season, g.week,
                               SUM(s.pass_att) AS pass_att, SUM(s.rush_att) AS rush_att,
                               SUM(s.targets) AS targets
                          FROM player_game_stats s JOIN games g ON g.id = s.game_id
                         WHERE g.season_type = 'REG'
                         GROUP BY s.game_id, s.team_id, g.season, g.week
                        """)) {
            while (rs.next()) {
                long gameId = rs.getLong("game_id");
                int teamId = rs.getInt("team_id");
                int season = rs.getInt("season");
                double passAir = Double.NaN;
                if (extras != null) {
                    Double air = extras.teamPassAirYards.get(NflverseExtras.teamKey(
                            scheduled.get(gameId).game().nflverseId(), abbrById.get(teamId)));
                    if (air != null) {
                        passAir = air;
                    } else {
                        missingTeamAir.computeIfAbsent(season, k -> new int[1])[0]++;
                    }
                }
                TeamGame team = new TeamGame(teamId, gameId, season, rs.getInt("week"),
                        rs.getInt("pass_att"), rs.getInt("rush_att"), rs.getInt("targets"), passAir);
                teamGames.put(gameId + ":" + teamId, team);
            }
        }

        // A game is over once it has a score -- or, in v2, stat rows: nflverse publishes them
        // only after a game, and the schedule's score can lag a day behind.
        Map<Long, Game> games = new TreeMap<>();
        Map<String, Game> byNflverseId = new HashMap<>();
        Set<Long> withRows = new HashSet<>();
        teamGames.values().forEach(t -> withRows.add(t.gameId()));
        List<String> playedByRows = new ArrayList<>();
        for (Scheduled s : scheduled.values()) {
            Game g = s.game();
            boolean played = s.scored() || (extras != null && withRows.contains(g.id()));
            if (played && !s.scored()) {
                playedByRows.add(g.nflverseId());
            }
            Game game = new Game(g.id(), g.nflverseId(), g.season(), g.week(), g.homeTeamId(),
                    g.awayTeamId(), g.spread(), g.total(), played);
            games.put(game.id(), game);
            byNflverseId.put(game.nflverseId(), game);
        }

        Map<Long, List<PlayerGame>> byPlayer = new TreeMap<>();
        Map<Integer, int[]> joined = new TreeMap<>(); // season -> {matched, unmatched, position changed}
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT s.player_id, p.position, p.gsis_id, g.nflverse_game_id, s.game_id,
                               s.season, s.week, s.team_id, s.snap_pct, s.pass_att, s.rush_att,
                               s.targets, s.punt_ret, s.kick_ret, %s
                          FROM player_game_stats s
                        %s""".formatted(StatColumns.SELECT_LIST, SKILL_FILTER))) {
            while (rs.next()) {
                double snap = rs.getDouble("snap_pct");
                if (rs.wasNull()) {
                    snap = Double.NaN;
                }
                double[] line = new double[Quantity.COUNT];
                System.arraycopy(StatColumns.readValues(rs), 0, line, 0, StatKey.COUNT);
                line[Quantity.PASS_ATT] = rs.getInt("pass_att");
                line[Quantity.RUSH_ATT] = rs.getInt("rush_att");
                line[Quantity.TARGETS] = rs.getInt("targets");
                long gameId = rs.getLong("game_id");
                int teamId = rs.getInt("team_id");
                int season = rs.getInt("season");
                String position = rs.getString("position");
                double airYards = Double.NaN;
                if (extras != null) {
                    int[] counts = joined.computeIfAbsent(season, k -> new int[3]);
                    NflverseExtras.StatExtra extra = extras.stats.get(NflverseExtras.statKey(
                            rs.getString("gsis_id"), rs.getString("nflverse_game_id")));
                    if (extra == null) {
                        counts[1]++;
                    } else {
                        counts[0]++;
                        airYards = extra.airYards();
                        if (NflverseExtras.SKILL.contains(extra.position())
                                && !extra.position().equals(position)) {
                            position = extra.position();
                            counts[2]++;
                        }
                    }
                }
                PlayerGame game = new PlayerGame(rs.getLong("player_id"), position,
                        gameId, season, rs.getInt("week"), teamId, snap, line,
                        rs.getInt("punt_ret") + rs.getInt("kick_ret"),
                        teamGames.get(gameId + ":" + teamId), airYards, false);
                byPlayer.computeIfAbsent(game.playerId(), id -> new ArrayList<>()).add(game);
            }
        }

        Map<Long, Timeline<PlayerGame>> storedOnly = new TreeMap<>();
        byPlayer.forEach((id, rows) -> storedOnly.put(id, new Timeline<>(rows)));
        List<String> fingerprint = new ArrayList<>(fingerprint(connection, storedOnly));

        if (extras != null) {
            fingerprint.addAll(addAppearances(connection, extras, byPlayer, games, byNflverseId,
                    idByAbbr, teamGames, joined, missingTeamAir, playedByRows));
        }

        Map<Long, Timeline<PlayerGame>> players = new TreeMap<>();
        byPlayer.forEach((id, rows) -> players.put(id, new Timeline<>(rows)));

        Map<Integer, List<TeamGame>> byTeam = new TreeMap<>();
        teamGames.values().forEach(t -> byTeam.computeIfAbsent(t.teamId(), id -> new ArrayList<>()).add(t));
        Map<Integer, Timeline<TeamGame>> teams = new TreeMap<>();
        byTeam.forEach((id, rows) -> teams.put(id, new Timeline<>(rows)));

        // What each defense allowed: every skill row, credited to the other side of its game.
        Map<String, DefenseGame> allowed = new HashMap<>();
        for (TeamGame team : teamGames.values()) {
            Game game = games.get(team.gameId());
            int defense = game.homeTeamId() == team.teamId() ? game.awayTeamId() : game.homeTeamId();
            allowed.put(team.gameId() + ":" + defense, new DefenseGame(defense, team.gameId(),
                    team.season(), team.week(), new double[Priors.POSITIONS.size()][Quantity.COUNT]));
        }
        for (List<PlayerGame> rows : byPlayer.values()) {
            for (PlayerGame row : rows) {
                Game game = games.get(row.gameId());
                int defense = game.homeTeamId() == row.teamId() ? game.awayTeamId() : game.homeTeamId();
                double[] sum = allowed.get(row.gameId() + ":" + defense).allowed()
                        [Priors.POSITIONS.indexOf(row.position())];
                for (int q = 0; q < Quantity.COUNT; q++) {
                    sum[q] += row.line()[q];
                }
            }
        }
        Map<Integer, List<DefenseGame>> byDefense = new TreeMap<>();
        allowed.values().forEach(d -> byDefense.computeIfAbsent(d.teamId(), id -> new ArrayList<>()).add(d));
        Map<Integer, Timeline<DefenseGame>> defenses = new TreeMap<>();
        byDefense.forEach((id, rows) -> defenses.put(id, new Timeline<>(rows)));

        Map<Integer, List<Game>> gamesByWeek = new TreeMap<>();
        games.values().stream().sorted(Comparator.comparingLong(Game::id))
                .forEach(g -> gamesByWeek.computeIfAbsent(g.key(), k -> new ArrayList<>()).add(g));

        return new BacktestData(players, teams, defenses, league(byPlayer, teamGames), games,
                gamesByWeek, List.copyOf(fingerprint));
    }

    /** The league's per-week totals over every row, and the team-games that produced them. */
    static Timeline<LeagueWeek> league(Map<Long, List<PlayerGame>> byPlayer,
            Map<String, TeamGame> teamGames) {
        Map<Integer, double[]> totals = new TreeMap<>();
        Map<Integer, Integer> teamCounts = new TreeMap<>();
        for (TeamGame t : teamGames.values()) {
            teamCounts.merge(t.key(), 1, Integer::sum);
        }
        List<PlayerGame> rows = new ArrayList<>();
        byPlayer.values().forEach(rows::addAll);
        rows.sort(Comparator.comparingInt(PlayerGame::key).thenComparingLong(PlayerGame::playerId)
                .thenComparingLong(PlayerGame::gameId));
        for (PlayerGame row : rows) {
            double[] sum = totals.computeIfAbsent(row.key(), k -> new double[Quantity.COUNT]);
            for (int q = 0; q < Quantity.COUNT; q++) {
                sum[q] += row.line()[q];
            }
        }
        List<LeagueWeek> weeks = new ArrayList<>();
        teamCounts.forEach((key, count) -> weeks.add(new LeagueWeek(key / 100, key % 100, count,
                totals.getOrDefault(key, new double[Quantity.COUNT]))));
        return new Timeline<>(weeks);
    }

    /**
     * Adds every active, zero-stat appearance: a skill player with offensive snaps in a
     * finished game and no stored row. Each is counted, and so is every snap row that could
     * not be placed -- an unknown pfr id, a player who is not a skill player in our table, a
     * team that is not a side of the game.
     */
    private static List<String> addAppearances(Connection connection, NflverseExtras extras,
            Map<Long, List<PlayerGame>> byPlayer, Map<Long, Game> games,
            Map<String, Game> byNflverseId, Map<String, Integer> idByAbbr,
            Map<String, TeamGame> teamGames, Map<Integer, int[]> joined,
            Map<Integer, int[]> missingTeamAir, List<String> playedByRows) throws SQLException {
        Map<String, Long> byPfr = new HashMap<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT external_ids ->> 'pfr' AS pfr, id FROM players
                         WHERE external_ids ->> 'pfr' IS NOT NULL
                           AND position IN ('QB', 'RB', 'WR', 'TE')
                        """)) {
            while (rs.next()) {
                byPfr.put(rs.getString("pfr"), rs.getLong("id"));
            }
        }
        Set<String> stored = new HashSet<>();
        byPlayer.values().forEach(rows -> rows.forEach(r -> stored.add(r.playerId() + ":" + r.gameId())));

        Map<Integer, int[]> counts = new TreeMap<>(); // added, unknown pfr, team mismatch, unfinished
        for (NflverseExtras.Snap snap : extras.snaps) {
            int[] c = counts.computeIfAbsent(snap.season(), k -> new int[4]);
            Game game = byNflverseId.get(snap.gameId());
            if (game == null || !game.played()) {
                c[3]++;
                continue;
            }
            Long player = byPfr.get(snap.pfrId());
            if (player == null) {
                c[1]++;
                continue;
            }
            Integer team = idByAbbr.get(snap.team());
            TeamGame teamGame = team == null ? null : teamGames.get(game.id() + ":" + team);
            if (team == null || !game.involves(team) || teamGame == null) {
                c[2]++;
                continue;
            }
            if (!stored.add(player + ":" + game.id())) {
                continue; // he has a stat row, or this snap row repeats one already placed
            }
            byPlayer.computeIfAbsent(player, id -> new ArrayList<>()).add(new PlayerGame(player,
                    snap.position(), game.id(), game.season(), game.week(), team,
                    100 * snap.offensePct(), new double[Quantity.COUNT], 0, teamGame, 0, true));
            c[0]++;
        }

        List<String> lines = new ArrayList<>();
        lines.add("v2 dataset -- nflverse files read:");
        extras.files.forEach(f -> lines.add("  " + f));
        for (Map.Entry<Integer, int[]> entry : joined.entrySet()) {
            int[] j = entry.getValue();
            int[] a = counts.getOrDefault(entry.getKey(), new int[4]);
            lines.add(String.format(Locale.ROOT,
                    "%d: v2 joins -- %,d stored rows matched nflverse, %,d not (air yards NaN); "
                            + "%,d took the week's position; %,d zero-stat appearances added; "
                            + "snap rows not placed: %,d unknown pfr id, %,d team not a side, "
                            + "%,d unfinished game; %,d team-games without passing air yards",
                    entry.getKey(), j[0], j[1], j[2], a[0], a[1], a[2], a[3],
                    missingTeamAir.getOrDefault(entry.getKey(), new int[1])[0]));
        }
        lines.add("games counted as played by their stat rows (score not yet stored): "
                + (playedByRows.isEmpty() ? "none" : String.join(", ", playedByRows)));
        lines.add("nflverse rows with a target or attempt but no air yards: "
                + extras.statRowsMissingAirYards);
        return lines;
    }

    /**
     * Rows and per-quantity sums by season, checked against SQL, plus a sha256 over every
     * loaded row in a fixed order. Two runs on the same data print the same fingerprint.
     */
    private static List<String> fingerprint(Connection connection,
            Map<Long, Timeline<PlayerGame>> players) throws SQLException {
        Map<Integer, double[]> loaded = new TreeMap<>();
        List<PlayerGame> rows = new ArrayList<>();
        players.values().forEach(t -> rows.addAll(t.all()));
        rows.sort(Comparator.comparingInt(PlayerGame::key).thenComparingLong(PlayerGame::playerId));
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        ByteBuffer buffer = ByteBuffer.allocate(8 * (4 + Quantity.COUNT));
        for (PlayerGame row : rows) {
            double[] sums = loaded.computeIfAbsent(row.season(), s -> new double[Quantity.COUNT + 1]);
            sums[Quantity.COUNT]++;
            buffer.clear();
            buffer.putLong(row.playerId()).putLong(row.gameId()).putLong(row.teamId())
                    .putDouble(row.snapPct());
            for (int q = 0; q < Quantity.COUNT; q++) {
                sums[q] += row.line()[q];
                buffer.putDouble(row.line()[q]);
            }
            sha.update(buffer.array());
        }

        // Generated from the enum and three fixed usage names -- compile-time constants, never
        // a request value, which is what StatColumns does for the same reason (section 8).
        String sums = IntStream.range(0, Quantity.COUNT)
                .mapToObj(q -> "SUM(s." + Quantity.name(q) + ")")
                .collect(Collectors.joining(", "));
        List<String> lines = new ArrayList<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT s.season, COUNT(*), " + sums
                        + " FROM player_game_stats s " + SKILL_FILTER + " GROUP BY s.season")) {
            Map<Integer, double[]> fromSql = new TreeMap<>();
            while (rs.next()) {
                double[] values = new double[Quantity.COUNT + 1];
                values[Quantity.COUNT] = rs.getLong(2);
                for (int q = 0; q < Quantity.COUNT; q++) {
                    values[q] = rs.getLong(3 + q);
                }
                fromSql.put(rs.getInt(1), values);
            }
            if (!fromSql.keySet().equals(loaded.keySet())) {
                throw new IllegalStateException("seasons loaded " + loaded.keySet()
                        + " but SQL has " + fromSql.keySet());
            }
            for (Map.Entry<Integer, double[]> entry : loaded.entrySet()) {
                double[] expected = fromSql.get(entry.getKey());
                for (int i = 0; i <= Quantity.COUNT; i++) {
                    if (expected[i] != entry.getValue()[i]) {
                        throw new IllegalStateException("season " + entry.getKey() + " "
                                + (i == Quantity.COUNT ? "rows" : Quantity.name(i)) + ": loaded "
                                + entry.getValue()[i] + ", SQL says " + expected[i]);
                    }
                }
                double[] v = entry.getValue();
                lines.add(String.format(Locale.ROOT,
                        "%d: %,d rows · pass_yd %,.0f · rush_yd %,.0f · rec %,.0f · rec_yd %,.0f · targets %,.0f",
                        entry.getKey(), (long) v[Quantity.COUNT], v[Quantity.of(StatKey.PASS_YD)],
                        v[Quantity.of(StatKey.RUSH_YD)], v[Quantity.of(StatKey.REC)],
                        v[Quantity.of(StatKey.REC_YD)], v[Quantity.TARGETS]));
            }
        }
        lines.add("sha256 over " + String.format(Locale.ROOT, "%,d", rows.size()) + " rows: "
                + HexFormat.of().formatHex(sha.digest()));
        lines.add("per-season sums match a direct SQL SUM: yes (checked at load)");
        return List.copyOf(lines);
    }
}
