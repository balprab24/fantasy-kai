package com.fantasykai.projection;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Experiment v2's research-only data: two nflverse files the pipeline already downloads or
 * could, read from a local cache that {@code scripts/backtest.sh} fills once.
 *
 * <ul>
 *   <li>{@code stats_player_week_<season>.csv} -- {@code receiving_air_yards},
 *       {@code passing_air_yards} and the week's {@code position}. {@code StatIngestor} reads
 *       this file and drops all three.</li>
 *   <li>{@code snap_counts_<season>.csv} -- appearances. A skill player with offensive snaps
 *       and no stat row played and recorded nothing; our ingest stores nothing for him.</li>
 * </ul>
 *
 * <p>Definitions were verified against the files before use (projection-accuracy.md, v2):
 * nflverse's {@code air_yards_share} is a player's receiving air yards over his team's
 * <em>passing</em> air yards, so that is the denominator built here. A missing file is an
 * error, never an empty season, and every file read is fingerprinted.
 */
final class NflverseExtras {

    static final Set<String> SKILL = Set.of("QB", "RB", "WR", "TE");

    /** One stat row's extras, keyed by gsis id and nflverse game id. */
    record StatExtra(double airYards, String position) {}

    /** An appearance from the snap file. */
    record Snap(String pfrId, String gameId, int season, int week, String team, String position,
            double offensePct) {}

    final Map<String, StatExtra> stats = new HashMap<>();
    final Map<String, Double> teamPassAirYards = new HashMap<>();
    final List<Snap> snaps = new ArrayList<>();
    final List<String> files = new ArrayList<>();
    int statRowsMissingAirYards;

    private NflverseExtras() {}

    static String statKey(String gsisId, String gameId) {
        return gsisId + "|" + gameId;
    }

    static String teamKey(String gameId, String teamAbbr) {
        return gameId + "|" + teamAbbr;
    }

    static NflverseExtras read(Path cache, int fromSeason, int toSeason) throws IOException {
        NflverseExtras extras = new NflverseExtras();
        for (int season = fromSeason; season <= toSeason; season++) {
            extras.readStats(cache.resolve("stats_player_week_" + season + ".csv"));
            extras.readSnaps(cache.resolve("snap_counts_" + season + ".csv"));
        }
        return extras;
    }

    void readStats(Path file) throws IOException {
        List<CSVRecord> rows = parse(file, Set.of("player_id", "game_id", "team", "season_type",
                "position", "receiving_air_yards", "passing_air_yards", "targets", "attempts"));
        for (CSVRecord r : rows) {
            if (!"REG".equals(r.get("season_type"))) {
                continue;
            }
            String gameId = r.get("game_id");
            double passing = number(r.get("passing_air_yards"));
            if (!Double.isNaN(passing)) {
                teamPassAirYards.merge(teamKey(gameId, r.get("team")), passing, Double::sum);
            } else if (number(r.get("attempts")) > 0) {
                statRowsMissingAirYards++;
            }
            String player = r.get("player_id");
            if (player.isBlank() || "NA".equals(player)) {
                continue; // the 131 rows our ingest also drops: no id to join on
            }
            double air = number(r.get("receiving_air_yards"));
            if (Double.isNaN(air) && number(r.get("targets")) > 0) {
                statRowsMissingAirYards++;
            }
            // No air yards on a row with no target is a zero, not an absence.
            stats.put(statKey(player, gameId),
                    new StatExtra(Double.isNaN(air) && !(number(r.get("targets")) > 0) ? 0 : air,
                            r.get("position")));
        }
    }

    void readSnaps(Path file) throws IOException {
        List<CSVRecord> rows = parse(file, Set.of("game_id", "game_type", "season", "week",
                "pfr_player_id", "position", "team", "offense_snaps", "offense_pct"));
        for (CSVRecord r : rows) {
            if (!"REG".equals(r.get("game_type")) || !SKILL.contains(r.get("position"))
                    || !(number(r.get("offense_snaps")) > 0)) {
                continue;
            }
            snaps.add(new Snap(r.get("pfr_player_id"), r.get("game_id"),
                    Integer.parseInt(r.get("season")), Integer.parseInt(r.get("week")),
                    r.get("team"), r.get("position"), number(r.get("offense_pct"))));
        }
    }

    private List<CSVRecord> parse(Path file, Set<String> required) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException(file + " is not in the cache; run scripts/backtest.sh, which fills it");
        }
        byte[] bytes = Files.readAllBytes(file);
        try (Reader reader = Files.newBufferedReader(file);
                CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                        .get().parse(reader)) {
            Set<String> header = parser.getHeaderMap().keySet();
            for (String column : required) {
                if (!header.contains(column)) {
                    throw new IOException(file + " has no column " + column);
                }
            }
            List<CSVRecord> rows = parser.getRecords();
            files.add(String.format(Locale.ROOT, "%s: %,d rows, sha256 %s", file.getFileName(),
                    rows.size(), sha256(bytes)));
            return rows;
        }
    }

    static double number(String value) {
        if (value == null || value.isBlank() || "NA".equals(value)) {
            return Double.NaN;
        }
        return Double.parseDouble(value);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
