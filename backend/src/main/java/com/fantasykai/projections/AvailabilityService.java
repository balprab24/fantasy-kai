package com.fantasykai.projections;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Who is on which roster, and what is known about each player's availability, for a week.
 *
 * <p>Two clocks, two rules (architecture section 6):
 *
 * <ul>
 *   <li><strong>Rosters</strong>: each team's roster as of its latest published week up to the
 *       target. nflverse publishes a week's rosters during that week (week 6 was absent on
 *       Monday 2026-10-12; week 5 was present on Friday 2026-10-09), and a team on its bye has
 *       no rows that week. So until a team's week is out, its previous week stands: a roster
 *       status lasts until a transaction changes it, and a reserve stint lasts weeks by rule. The
 *       week the row came from travels with it ({@link Availability#rosterWeek}).</li>
 *   <li><strong>Injury designations</strong>: exactly the target week's report, never another.
 *       A Questionable from week 5 says nothing about week 6.</li>
 * </ul>
 *
 * <p>A player traded mid-week is read from whichever team's snapshot is newest. One who is on
 * no published roster and no report is {@link AvailabilityStatus#UNKNOWN} -- ranked, because a
 * late or missing feed is not news about him.
 *
 * <p>Reads one week by its primary-key prefix: ~2,600 roster rows and ~360 injury rows.
 */
@Component
public class AvailabilityService {

    /** One player's roster row: his team and status as of that team's latest published week. */
    public record RosterEntry(long playerId, int teamId, int week, String status, String detail,
            String position, Instant ingestedAt) {}

    private static final String ROSTER_AS_OF = """
            WITH latest AS (
                SELECT team_id, max(week) AS week FROM roster_week
                 WHERE season = ? AND week <= ?
                 GROUP BY team_id)
            SELECT r.player_id, r.team_id, r.week, r.status, r.status_detail, r.ingested_at, p.position
              FROM roster_week r
              JOIN latest l ON l.team_id = r.team_id AND l.week = r.week
              JOIN players p ON p.id = r.player_id
             WHERE r.season = ?
            """;

    private static final String REPORT_FOR_WEEK = """
            SELECT player_id, report_status, ingested_at FROM injury_report
             WHERE season = ? AND week = ?
            """;

    private final JdbcTemplate jdbc;

    public AvailabilityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every player on a roster as of {@code target}, one entry each -- his newest. */
    public Map<Long, RosterEntry> roster(SeasonWeek target) {
        Map<Long, RosterEntry> roster = new HashMap<>();
        jdbc.query(ROSTER_AS_OF, rs -> {
            RosterEntry entry = new RosterEntry(rs.getLong("player_id"), rs.getInt("team_id"),
                    rs.getInt("week"), rs.getString("status"), rs.getString("status_detail"),
                    rs.getString("position"), rs.getTimestamp("ingested_at").toInstant());
            roster.merge(entry.playerId(), entry, (a, b) -> a.week() >= b.week() ? a : b);
        }, target.season(), target.week(), target.season());
        return roster;
    }

    /** Known availability for every player with a roster row or a designation for {@code target}. */
    public Map<Long, Availability> forWeek(SeasonWeek target) {
        Map<Long, RosterEntry> roster = roster(target);
        Map<Long, String> reports = new HashMap<>();
        Map<Long, Instant> reportedAt = new HashMap<>();
        jdbc.query(REPORT_FOR_WEEK, rs -> {
            long player = rs.getLong("player_id");
            reports.put(player, rs.getString("report_status"));
            Timestamp at = rs.getTimestamp("ingested_at");
            reportedAt.put(player, at.toInstant());
        }, target.season(), target.week());

        Map<Long, Availability> availability = new HashMap<>();
        for (long player : union(roster.keySet(), reportedAt.keySet())) {
            RosterEntry entry = roster.get(player);
            String report = reports.get(player);
            availability.put(player, new Availability(target,
                    AvailabilityRules.classify(entry == null ? null : entry.status(), report),
                    entry == null ? null : entry.status(), entry == null ? null : entry.detail(),
                    entry == null ? null : entry.week(), entry == null ? null : entry.teamId(),
                    report, entry == null ? null : entry.ingestedAt(), reportedAt.get(player)));
        }
        return availability;
    }

    /** One player's known availability for {@code target}; {@code UNKNOWN} when nothing is known. */
    public Availability of(long playerId, SeasonWeek target) {
        return forWeek(target).getOrDefault(playerId, Availability.unknown(target));
    }

    private static List<Long> union(Set<Long> a, Set<Long> b) {
        Set<Long> all = new TreeSet<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }
}
