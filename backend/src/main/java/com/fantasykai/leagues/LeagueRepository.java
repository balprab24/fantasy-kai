package com.fantasykai.leagues;

import com.fantasykai.leagues.LeagueSnapshot.RosterEntry;
import com.fantasykai.leagues.LeagueSnapshot.Team;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Every league table, read and written.
 *
 * <p><strong>Every statement carries {@code user_id = ?}</strong>, as
 * {@code ScoringProfileWriteRepository}'s do: the ownership filter is part of
 * the statement, so another member's id updates, deletes and reads zero rows
 * and there is no check-then-act window. The schema backs it -- each child
 * table reaches its league through {@code (league_id, user_id)}.
 */
@Repository
public class LeagueRepository {

    private final JdbcTemplate jdbc;

    public LeagueRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A stored league's own columns; JSON columns stay text for the service to read. */
    public record LeagueRow(long id, long connectionId, String platform, String externalLeagueId,
            int season, String name, int numTeams, String leagueType, Long scoringProfileId,
            String scoringNote, String rosterSlots, Integer playoffTeams, Integer playoffWeekStart,
            Integer tradeDeadline, String myTeamExternalId, String unmodeled, Instant syncedAt) {}

    public record TeamRow(String externalTeamId, String name, String managerName,
            int wins, int losses, int ties) {}

    /** A roster spot with what our player master knows about it; null fields when unmatched. */
    public record RosterRow(String externalTeamId, String externalPlayerId, String slot,
            Long playerId, String name, String position, String team, String espnId) {}

    public record MatchupRow(int week, String externalTeamId, String opponentExternalTeamId) {}

    /** The columns an import or a sync writes, apart from its keys. */
    public record LeagueFields(String name, int numTeams, String leagueType, Long scoringProfileId,
            String scoringNote, String rosterSlots, Integer playoffTeams, Integer playoffWeekStart,
            Integer tradeDeadline, String myTeamExternalId, String unmodeled, Instant syncedAt) {}

    private static final String LEAGUE_COLUMNS = """
            id, connection_id, platform, external_league_id, season, name, num_teams, league_type,
            scoring_profile_id, scoring_note, roster_slots::text AS roster_slots, playoff_teams,
            playoff_week_start, trade_deadline, my_team_external_id,
            unmodeled_scoring::text AS unmodeled_scoring, synced_at
            """;

    private static final RowMapper<LeagueRow> LEAGUE = (rs, n) -> new LeagueRow(
            rs.getLong("id"), rs.getLong("connection_id"), rs.getString("platform"),
            rs.getString("external_league_id"), rs.getInt("season"), rs.getString("name"),
            rs.getInt("num_teams"), rs.getString("league_type"),
            rs.getObject("scoring_profile_id", Long.class), rs.getString("scoring_note"),
            rs.getString("roster_slots"), rs.getObject("playoff_teams", Integer.class),
            rs.getObject("playoff_week_start", Integer.class),
            rs.getObject("trade_deadline", Integer.class), rs.getString("my_team_external_id"),
            rs.getString("unmodeled_scoring"), rs.getTimestamp("synced_at").toInstant());

    /** The member's connection to a platform account, created on first import. */
    public long upsertConnection(long userId, String platform, String externalUserId) {
        return jdbc.queryForObject("""
                INSERT INTO league_connections (user_id, platform, external_user_id)
                VALUES (?, ?, ?)
                ON CONFLICT ON CONSTRAINT uq_league_connections_account
                DO UPDATE SET status = 'ACTIVE', last_error = NULL
                RETURNING id
                """, Long.class, userId, platform, externalUserId);
    }

    /**
     * @throws LeagueConflictException when the member already imported it for
     *     this season -- caught from the unique constraint, not pre-checked
     */
    public long insertLeague(long userId, long connectionId, String platform, String externalLeagueId,
            int season, LeagueFields f) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO leagues (user_id, connection_id, platform, external_league_id, season,
                        name, num_teams, league_type, scoring_profile_id, scoring_note, roster_slots,
                        playoff_teams, playoff_week_start, trade_deadline, my_team_external_id,
                        unmodeled_scoring, synced_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?)
                    RETURNING id
                    """, Long.class, userId, connectionId, platform, externalLeagueId, season,
                    f.name(), f.numTeams(), f.leagueType(), f.scoringProfileId(), f.scoringNote(),
                    f.rosterSlots(), f.playoffTeams(), f.playoffWeekStart(), f.tradeDeadline(),
                    f.myTeamExternalId(), f.unmodeled(), Timestamp.from(f.syncedAt()));
        } catch (DuplicateKeyException e) {
            throw new LeagueConflictException("You already imported this league for " + season + ".");
        }
    }

    public boolean updateLeague(long leagueId, long userId, LeagueFields f) {
        return jdbc.update("""
                UPDATE leagues SET name = ?, num_teams = ?, league_type = ?, scoring_profile_id = ?,
                       scoring_note = ?, roster_slots = ?::jsonb, playoff_teams = ?,
                       playoff_week_start = ?, trade_deadline = ?, my_team_external_id = ?,
                       unmodeled_scoring = ?::jsonb, synced_at = ?
                 WHERE id = ? AND user_id = ?
                """, f.name(), f.numTeams(), f.leagueType(), f.scoringProfileId(), f.scoringNote(),
                f.rosterSlots(), f.playoffTeams(), f.playoffWeekStart(), f.tradeDeadline(),
                f.myTeamExternalId(), f.unmodeled(), Timestamp.from(f.syncedAt()), leagueId, userId) == 1;
    }

    /** Teams and rosters as one snapshot: the old one is gone only if the new one is written. */
    public void replaceSnapshot(long leagueId, long userId, List<Team> teams,
            List<RosterEntry> roster, Map<String, Long> playerIds) {
        jdbc.update("DELETE FROM league_rosters WHERE league_id = ? AND user_id = ?", leagueId, userId);
        jdbc.update("DELETE FROM league_teams WHERE league_id = ? AND user_id = ?", leagueId, userId);
        jdbc.batchUpdate("""
                INSERT INTO league_teams (league_id, user_id, external_team_id, name, manager_name,
                    wins, losses, ties)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, teams.stream().map(t -> new Object[] {leagueId, userId, t.externalTeamId(),
                t.name(), t.managerName(), t.wins(), t.losses(), t.ties()}).toList());
        jdbc.batchUpdate("""
                INSERT INTO league_rosters (league_id, user_id, external_team_id, external_player_id,
                    player_id, slot)
                VALUES (?, ?, ?, ?, ?, ?)
                """, roster.stream().map(r -> new Object[] {leagueId, userId, r.externalTeamId(),
                r.externalPlayerId(), playerIds.get(r.externalPlayerId()), r.slot()}).toList());
    }

    /** Pairings are fixed once published, so a stored week is kept and only gaps are filled. */
    public void addMatchups(long leagueId, long userId, Map<Integer, List<LeagueProvider.Pairing>> schedule) {
        List<Object[]> rows = new ArrayList<>();
        schedule.forEach((week, pairings) -> pairings.forEach(p -> rows.add(new Object[] {
                leagueId, userId, week, p.externalTeamId(), p.opponentExternalTeamId()})));
        jdbc.batchUpdate("""
                INSERT INTO league_matchups (league_id, user_id, week, external_team_id,
                    opponent_external_team_id)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, rows);
    }

    public Set<Integer> storedWeeks(long leagueId, long userId) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT week FROM league_matchups WHERE league_id = ? AND user_id = ?",
                Integer.class, leagueId, userId));
    }

    public Optional<LeagueRow> find(long leagueId, long userId) {
        return jdbc.query("SELECT " + LEAGUE_COLUMNS + " FROM leagues WHERE id = ? AND user_id = ?",
                LEAGUE, leagueId, userId).stream().findFirst();
    }

    public List<LeagueRow> findAll(long userId) {
        return jdbc.query("SELECT " + LEAGUE_COLUMNS
                + " FROM leagues WHERE user_id = ? ORDER BY season DESC, name, id", LEAGUE, userId);
    }

    /** league id by external id, for marking what discovery found as already imported. */
    public Map<String, Long> importedIds(long userId, String platform, int season) {
        Map<String, Long> ids = new HashMap<>();
        jdbc.query("""
                SELECT external_league_id, id FROM leagues
                 WHERE user_id = ? AND platform = ? AND season = ?
                """, rs -> {
            ids.put(rs.getString("external_league_id"), rs.getLong("id"));
        }, userId, platform, season);
        return ids;
    }

    public List<TeamRow> teams(long leagueId, long userId) {
        return jdbc.query("""
                SELECT external_team_id, name, manager_name, wins, losses, ties
                  FROM league_teams WHERE league_id = ? AND user_id = ?
                 ORDER BY wins DESC, ties DESC, losses, name
                """, (rs, n) -> new TeamRow(rs.getString("external_team_id"), rs.getString("name"),
                rs.getString("manager_name"), rs.getInt("wins"), rs.getInt("losses"),
                rs.getInt("ties")), leagueId, userId);
    }

    public List<RosterRow> roster(long leagueId, long userId) {
        return jdbc.query("""
                SELECT r.external_team_id, r.external_player_id, r.slot, p.id AS player_id,
                       p.full_name, p.position, t.abbr, p.external_ids->>'espn' AS espn_id
                  FROM league_rosters r
                  LEFT JOIN players p ON p.id = r.player_id
                  LEFT JOIN teams t ON t.id = p.team_id
                 WHERE r.league_id = ? AND r.user_id = ?
                 ORDER BY r.external_team_id, r.external_player_id
                """, (rs, n) -> new RosterRow(rs.getString("external_team_id"),
                rs.getString("external_player_id"), rs.getString("slot"),
                rs.getObject("player_id", Long.class), rs.getString("full_name"),
                rs.getString("position"), rs.getString("abbr"), rs.getString("espn_id")),
                leagueId, userId);
    }

    public List<MatchupRow> matchups(long leagueId, long userId) {
        return jdbc.query("""
                SELECT week, external_team_id, opponent_external_team_id FROM league_matchups
                 WHERE league_id = ? AND user_id = ? ORDER BY week, external_team_id
                """, (rs, n) -> new MatchupRow(rs.getInt("week"), rs.getString("external_team_id"),
                rs.getString("opponent_external_team_id")), leagueId, userId);
    }

    /** @return false when the league is not the member's or has no such team */
    public boolean setMyTeam(long leagueId, long userId, String externalTeamId) {
        return jdbc.update("""
                UPDATE leagues SET my_team_external_id = ?
                 WHERE id = ? AND user_id = ?
                   AND EXISTS (SELECT 1 FROM league_teams
                                WHERE league_id = ? AND user_id = ? AND external_team_id = ?)
                """, externalTeamId, leagueId, userId, leagueId, userId, externalTeamId) == 1;
    }

    /** @param scoringProfileId the deleted league's profile, for the caller to remove; may be null */
    public record Deleted(Long scoringProfileId) {}

    /**
     * Deletes the league (its teams, rosters and schedule cascade) and, when it
     * was the account's last league, the connection too.
     *
     * @return empty when nothing was deleted -- not found and not yours are the same answer
     */
    public Optional<Deleted> delete(long leagueId, long userId) {
        List<Object[]> deleted = jdbc.query("""
                DELETE FROM leagues WHERE id = ? AND user_id = ?
                RETURNING connection_id, scoring_profile_id
                """, (rs, n) -> new Object[] {rs.getLong("connection_id"),
                rs.getObject("scoring_profile_id", Long.class)}, leagueId, userId);
        if (deleted.isEmpty()) {
            return Optional.empty();
        }
        jdbc.update("""
                DELETE FROM league_connections c WHERE c.id = ? AND c.user_id = ?
                   AND NOT EXISTS (SELECT 1 FROM leagues l WHERE l.connection_id = c.id)
                """, deleted.getFirst()[0], userId);
        return Optional.of(new Deleted((Long) deleted.getFirst()[1]));
    }

    /**
     * Our player id for each Sleeper id that resolves unambiguously: a reviewed
     * override first (S0's order), then the id the daily crosswalk stored --
     * refused when two players carry it, which the crosswalk's write guard can
     * leave behind (PR #75 review).
     */
    public Map<String, Long> resolveSleeperIds(Collection<String> sleeperIds) {
        Map<String, Long> resolved = new HashMap<>();
        if (sleeperIds.isEmpty()) {
            return resolved;
        }
        Object[] ids = sleeperIds.toArray();
        jdbc.query("""
                SELECT external_ids->>'sleeper' AS sleeper_id, min(id) AS player_id
                  FROM players WHERE external_ids->>'sleeper' = ANY (?)
                 GROUP BY 1 HAVING count(*) = 1
                """, ps -> ps.setArray(1, textArray(ps.getConnection(), ids)),
                rs -> {
                    resolved.put(rs.getString("sleeper_id"), rs.getLong("player_id"));
                });
        jdbc.query("""
                SELECT external_id, player_id FROM player_id_overrides
                 WHERE platform = 'sleeper' AND external_id = ANY (?)
                """, ps -> ps.setArray(1, textArray(ps.getConnection(), ids)),
                rs -> {
                    resolved.put(rs.getString("external_id"), rs.getLong("player_id"));
                });
        return resolved;
    }

    private static Array textArray(java.sql.Connection connection, Object[] values)
            throws java.sql.SQLException {
        return connection.createArrayOf("text", values);
    }
}
