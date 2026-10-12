package com.fantasykai.projections;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Which week is "next week".
 *
 * <p>The smallest regular-season week of the season that still has a game in it which is not
 * finished. A game is finished when its stat rows are stored -- not when it has a score, which
 * can lag the stats -- so the board rolls forward only once the whole of last week is in:
 * normally the Tuesday 06:00 ET pull, after Monday night. Until then the week shows with every
 * game kicked off and locked.
 *
 * <p>Two different conditions, handled two different ways:
 *
 * <ul>
 *   <li><strong>A game no longer on the schedule</strong> -- moved to another week or cancelled,
 *       which nflverse expresses by deleting the row -- is {@code games.delisted_at}, set by the
 *       ingest ({@code GameIngestor}). It is not part of any week: it never holds one, is never a
 *       target game, and is never projected. Immediately, with no waiting.</li>
 *   <li><strong>A game still on the schedule whose stats have not arrived</strong> stops holding
 *       the week {@value #UNREPORTED_HOURS} hours after kickoff. That is a late feed, not a
 *       schedule change: the game stays listed, and when its stats land the next run regenerates
 *       every open row from them. Without it, one Monday night game whose stats were still missing
 *       at Thursday 06:00 would leave Thursday's game never projected -- the last run before it
 *       would still be on the old week. Forty-eight hours puts Monday night's kickoff behind that
 *       run. It also bounds the days a cancelled game stays listed before nflverse deletes it
 *       (2022's BUF-CIN: three), but delisting, not this, is what removes it.</li>
 * </ul>
 *
 * <p>Postseason games never count: weeks 19-22 are playoffs, and 2020's wild card is week 18.
 * Empty means there is no week to project: after week 18, before the schedule is published,
 * or out of season.
 *
 * <p>{@code player_game_stats} has no index past its primary key, by design (section 9), so the
 * "has stat rows" test is one sequential scan of it -- once a day, inside the job. A request
 * path must never call this; Slice 5 reads the week the job wrote.
 */
@Component
public class TargetWeekResolver {

    static final int UNREPORTED_HOURS = 48;

    static final Duration UNREPORTED_AFTER = Duration.ofHours(UNREPORTED_HOURS);

    private static final String NEXT_WEEK = """
            SELECT min(g.week) FROM games g
             WHERE g.season = ? AND g.season_type = 'REG' AND g.delisted_at IS NULL
               AND NOT EXISTS (SELECT 1 FROM player_game_stats s WHERE s.game_id = g.id)
               AND (g.kickoff_at IS NULL OR g.kickoff_at > ?)
            """;

    private static final String GAMES = """
            SELECT id, home_team_id, away_team_id, kickoff_at FROM games
             WHERE season = ? AND week = ? AND season_type = 'REG' AND delisted_at IS NULL
             ORDER BY id
            """;

    private final JdbcTemplate jdbc;

    public TargetWeekResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The week to project in {@code season} as of {@code now}, with its games; empty if none. */
    public Optional<TargetWeek> resolve(int season, Instant now) {
        Integer week = jdbc.queryForObject(NEXT_WEEK, Integer.class, season, utc(now.minus(UNREPORTED_AFTER)));
        if (week == null) {
            return Optional.empty();
        }
        List<TargetWeek.Game> games = jdbc.query(GAMES, (rs, n) -> {
            OffsetDateTime kickoff = rs.getObject("kickoff_at", OffsetDateTime.class);
            return new TargetWeek.Game(rs.getLong("id"), rs.getInt("home_team_id"),
                    rs.getInt("away_team_id"), kickoff == null ? null : kickoff.toInstant());
        }, season, week);
        return Optional.of(new TargetWeek(new SeasonWeek(season, week), games));
    }

    /** pgjdbc binds an OffsetDateTime as timestamptz exactly; no JVM time zone on the way. */
    static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
