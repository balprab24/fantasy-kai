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
 * <p>One escape: a game still without stat rows {@value #UNREPORTED_HOURS} hours after kickoff
 * stops holding the week. Without it, one Monday night game whose stats are late past Thursday
 * 06:00 would mean Thursday night's game is never projected at all -- the last run before it
 * would still be on the old week. Forty-eight hours puts Monday night's kickoff behind
 * Thursday's 06:00 run.
 *
 * <p>It also stops a cancelled game freezing the rest of the season. nflverse drops one from its
 * schedule (2022 week 17 holds 15 games; BUF-CIN is not among them, measured 2026-10-12), but
 * {@code GameIngestor} only upserts: a game ingested before it was cancelled stays in
 * {@code games} with no stats, for good. The local database never saw that happen only because
 * 2022 was backfilled years later.
 *
 * <p>A game rolled past this way is finished as far as the week is concerned; when its stats do
 * land, the next run regenerates every open row from them.
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
             WHERE g.season = ? AND g.season_type = 'REG'
               AND NOT EXISTS (SELECT 1 FROM player_game_stats s WHERE s.game_id = g.id)
               AND (g.kickoff_at IS NULL OR g.kickoff_at > ?)
            """;

    private static final String GAMES = """
            SELECT id, home_team_id, away_team_id, kickoff_at FROM games
             WHERE season = ? AND week = ? AND season_type = 'REG'
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
