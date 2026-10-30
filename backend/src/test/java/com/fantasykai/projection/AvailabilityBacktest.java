package com.fantasykai.projection;

import com.fantasykai.projections.AvailabilityRules;
import com.fantasykai.projections.AvailabilityStatus;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * {@code scripts/backtest.sh availability}: the approved V1 availability rule, run historically
 * with the production classifier and nothing tuned.
 *
 * <p>The population is the research's own: 2025's P0, rebuilt from stored rows exactly as Slice 1
 * built it -- every player who appeared in his team's previous game, ranked by trailing
 * opportunity, a player who then did not play counting as absent. The run first checks it
 * reproduces the 406 absences the research reported, then classifies every case with
 * {@link AvailabilityRules#classify} from nflverse's 2025 weekly roster and injury report.
 *
 * <p>Read the result with its one leak in mind. The roster file is each week's FINAL snapshot:
 * a player activated from injured reserve, or a practice-squad player elevated, after a Saturday
 * 06:00 pull shows ACT here and RES or DEV there. So this cannot show production's exposure to
 * those moves; it bounds it instead, by counting players who played that week after being RES the
 * week before, or as elevated practice-squad players (ACT with a P detail). The injury report has
 * no such leak: it is pregame by nature. INA, the game-day inactive list, is never read.
 */
final class AvailabilityBacktest {

    private static final int SEASON = 2025;

    /** P0 absences the research reported for 2025 (projection-accuracy.md). */
    private static final int REPORTED_ABSENT = 406;

    private AvailabilityBacktest() {}

    private record Roster(String status, String detail) {}

    static void run(Connection connection, Path cache) throws Exception {
        BacktestData data = BacktestData.load(connection);
        Population.Season population = Population.build(data, SEASON);
        List<Case> p0 = population.where(c -> c.inP0);
        long absent = p0.stream().filter(c -> c.actual == null).count();

        Map<Long, String> gsis = new HashMap<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, gsis_id FROM players WHERE gsis_id IS NOT NULL")) {
            while (rs.next()) {
                gsis.put(rs.getLong(1), rs.getString(2));
            }
        }
        Map<String, Roster> roster = new HashMap<>();
        for (CSVRecord r : read(cache.resolve("roster_weekly_" + SEASON + ".csv"))) {
            if ("REG".equals(r.get("game_type")) && !r.get("gsis_id").isBlank()) {
                roster.put(r.get("gsis_id") + ":" + r.get("week"), new Roster(r.get("status"),
                        r.get("status_description_abbr")));
            }
        }
        Map<String, String> report = new HashMap<>();
        for (CSVRecord r : read(cache.resolve("injuries_" + SEASON + ".csv"))) {
            if ("REG".equals(r.get("game_type")) && !r.get("report_status").isBlank()) {
                report.merge(r.get("gsis_id") + ":" + r.get("week"), r.get("report_status"),
                        (a, b) -> AvailabilityRules.restrictiveness(a) >= AvailabilityRules.restrictiveness(b) ? a : b);
            }
        }

        Map<AvailabilityStatus, int[]> byStatus = new EnumMap<>(AvailabilityStatus.class);
        int[] notProjected = new int[2];
        int activatedAndPlayed = 0;
        int elevatedAndPlayed = 0;
        int inaAbsent = 0;
        for (Case c : p0) {
            String id = gsis.get(c.playerId);
            Roster r = id == null ? null : roster.get(id + ":" + c.week);
            String designation = id == null ? null : report.get(id + ":" + c.week);
            AvailabilityStatus status = AvailabilityRules.classify(r == null ? null : r.status(), designation);
            int played = c.actual == null ? 0 : 1;
            byStatus.computeIfAbsent(status, s -> new int[2])[played]++;
            // Production projects only a rostered player; the final snapshot decides that here.
            if (r == null || !AvailabilityRules.PROJECTED_ROSTER_STATUSES.contains(r.status())) {
                notProjected[played]++;
            }
            if (played == 1 && r != null && "ACT".equals(r.status())) {
                Roster before = id == null ? null : roster.get(id + ":" + (c.week - 1));
                if (before != null && "RES".equals(before.status())) {
                    activatedAndPlayed++;
                }
                if (r.detail() != null && r.detail().startsWith("P")) {
                    elevatedAndPlayed++;
                }
            }
            if (played == 0 && r != null && "INA".equals(r.status()) && designation == null) {
                inaAbsent++;
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# Availability, V1 rule, 2025 P0 (production `AvailabilityRules`, nothing tuned)\n\n");
        md.append(String.format(Locale.ROOT, "P0: %,d player-weeks, %,d absent (research reported %d: %s).%n%n",
                p0.size(), absent, REPORTED_ABSENT, absent == REPORTED_ABSENT ? "reproduced" : "NOT reproduced"));
        md.append("| V1 classification | Ranked | Absent | Played |\n|---|---|---:|---:|\n");
        for (AvailabilityStatus s : AvailabilityStatus.values()) {
            int[] n = byStatus.getOrDefault(s, new int[2]);
            md.append(String.format(Locale.ROOT, "| %s | %s | %,d | %,d |%n", s, s.ranked() ? "yes" : "**no**", n[0], n[1]));
        }
        int suppressedAbsent = count(byStatus, AvailabilityStatus.OUT, 0) + count(byStatus, AvailabilityStatus.RESERVE, 0);
        int suppressedPlayed = count(byStatus, AvailabilityStatus.OUT, 1) + count(byStatus, AvailabilityStatus.RESERVE, 1);
        md.append(String.format(Locale.ROOT, "%nOut or reserve -- projected, not ranked: **%,d absent correctly "
                + "left off; %,d who played wrongly left off.**%n", suppressedAbsent, suppressedPlayed));
        md.append(String.format(Locale.ROOT, "Not projected at all (practice squad, released, retired, or on no "
                + "week roster): %,d absent, %,d who played.%n", notProjected[0], notProjected[1]));
        md.append(String.format(Locale.ROOT, "Absent and game-day inactive with no designation (unknowable at "
                + "06:00, INA never read): %,d.%n", inaAbsent));
        md.append(String.format(Locale.ROOT, "%nThe final-snapshot leak, bounded: %,d played after being RES the "
                + "week before (activated during the week); %,d played as elevated practice-squad players. "
                + "A Saturday 06:00 pull may still have shown them RES or DEV.%n", activatedAndPlayed,
                elevatedAndPlayed));
        Files.createDirectories(Backtest.OUT);
        Files.writeString(Backtest.OUT.resolve("availability-results.md"), md.toString());
        System.out.println(md);
        if (absent != REPORTED_ABSENT) {
            throw new IllegalStateException("P0 absences " + absent + " do not reproduce the research's "
                    + REPORTED_ABSENT + "; the comparison would not be like for like");
        }
    }

    private static int count(Map<AvailabilityStatus, int[]> byStatus, AvailabilityStatus s, int played) {
        return byStatus.getOrDefault(s, new int[2])[played];
    }

    private static List<CSVRecord> read(Path file) throws Exception {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(file + " is not in the cache; run scripts/backtest.sh, which fills it");
        }
        try (Reader reader = Files.newBufferedReader(file);
                CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
                        .parse(reader)) {
            return parser.getRecords();
        }
    }
}
