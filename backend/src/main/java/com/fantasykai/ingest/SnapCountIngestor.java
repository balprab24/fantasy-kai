package com.fantasykai.ingest;

import com.fantasykai.query.ScoringPosition;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the snap file once and keeps two things from it.
 *
 * <ul>
 *   <li><strong>Snap share</strong>, written onto the stat row it belongs to. It joins on the
 *       Pro Football Reference id rather than gsis, so it needs the pfr id that
 *       {@link PlayerIngestor} stashes in players.external_ids.</li>
 *   <li><strong>Appearances</strong> (Phase 6, V7): every game a skill player took an
 *       offensive snap in, into {@code player_game_appearances}. A receiver who ran routes and
 *       caught nothing leaves no stat row, and the projection baseline would otherwise never
 *       see that game. Every appearance is stored, with or without a stat line; "played and
 *       recorded nothing" is decided when it is read.</li>
 * </ul>
 *
 * <p>One file, one {@code ingest_runs} row: since V7, {@code rows_upserted} counts snap shares
 * set <em>plus</em> appearances upserted, and the log line splits the two.
 *
 * <p>An appearance follows research v2's rule ({@code NflverseExtras.readSnaps},
 * {@code BacktestData.addAppearances}): the snap file calls him QB, RB, WR or TE;
 * {@code offense_snaps > 0}; his pfr id and the game both resolve; and his team is one side of
 * that game. Research also required the game to have been played, which a snap row implies.
 * Measured equal rather than assumed: the stored zero-stat appearances reproduce research's
 * per-season counts for 2020-25, and {@code backtest.sh parity} matches its histories game by
 * game.
 */
@Component
public class SnapCountIngestor {

    static final String SOURCE = "nflverse.snap_counts";

    private static final Logger log = LoggerFactory.getLogger(SnapCountIngestor.class);

    /** Every source column this ingestor reads. */
    static final Set<String> REQUIRED_COLUMNS =
            Set.of("pfr_player_id", "game_id", "offense_pct", "position", "team", "offense_snaps");

    private static final String UPDATE = """
            UPDATE player_game_stats SET snap_pct = ?
             WHERE player_id = ? AND game_id = ?
            """;

    private static final String UPSERT_APPEARANCE = """
            INSERT INTO player_game_appearances (player_id, game_id, team_id, offense_snaps)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (player_id, game_id) DO UPDATE
               SET team_id = EXCLUDED.team_id, offense_snaps = EXCLUDED.offense_snaps
            """;

    /** The two teams a game is between. */
    private record Matchup(long id, int home, int away) {

        boolean involves(int team) {
            return team == home || team == away;
        }
    }

    private final NflverseClient client;
    private final JdbcTemplate jdbc;

    public SnapCountIngestor(NflverseClient client, JdbcTemplate jdbc) {
        this.client = client;
        this.jdbc = jdbc;
    }

    public IngestResult ingest(int season) {
        // NB: `external_ids ? 'pfr'` would be the natural jsonb test, but JdbcTemplate
        // reads the ? as a bind placeholder. Compare the extracted value instead.
        Map<String, Long> byPfrId = new HashMap<>();
        jdbc.query("""
                SELECT external_ids ->> 'pfr' AS pfr_id, id FROM players
                 WHERE external_ids ->> 'pfr' IS NOT NULL
                """, rs -> {
            byPfrId.put(rs.getString("pfr_id"), rs.getLong("id"));
        });

        Map<String, Matchup> games = new HashMap<>();
        jdbc.query("SELECT nflverse_game_id, id, home_team_id, away_team_id FROM games", rs -> {
            games.put(rs.getString(1), new Matchup(rs.getLong(2), rs.getInt(3), rs.getInt(4)));
        });

        Map<String, Integer> teamIds = new HashMap<>();
        jdbc.query("SELECT abbr, id FROM teams", rs -> {
            teamIds.put(rs.getString(1), rs.getInt(2));
        });

        List<SnapRow> rows = client.read("snap_counts", "snap_counts_%d.csv".formatted(season),
                REQUIRED_COLUMNS, record -> {
            String pfrId = CsvValues.text(record, "pfr_player_id");
            String gameKey = CsvValues.text(record, "game_id");
            if (pfrId == null || gameKey == null) {
                return null;
            }
            Long playerId = byPfrId.get(pfrId);
            Matchup game = games.get(gameKey);
            if (playerId == null || game == null) {
                return null;
            }
            // offense_pct arrives as a 0..1 fraction; the column is a percentage.
            BigDecimal pct = CsvValues.fractionAsPercent(record, "offense_pct");
            Object[] update = pct == null ? null : new Object[] {pct, playerId, game.id()};
            // shortOrNull, not shortValue: a blank is "not recorded", never a zero -- though
            // either way it is not an appearance.
            Short snaps = CsvValues.shortOrNull(record, "offense_snaps");
            Integer team = teamIds.get(CsvValues.text(record, "team"));
            Object[] appearance = snaps != null && snaps > 0 && team != null && game.involves(team)
                    && ScoringPosition.ALL.contains(CsvValues.text(record, "position"))
                    ? new Object[] {playerId, game.id(), team, snaps}
                    : null;
            return new SnapRow(update, appearance);
        });

        List<Object[]> updates = rows.stream().map(SnapRow::update).filter(Objects::nonNull).toList();
        List<Object[]> appearances =
                rows.stream().map(SnapRow::appearance).filter(Objects::nonNull).toList();
        int[] applied = jdbc.batchUpdate(UPDATE, updates);
        jdbc.batchUpdate(UPSERT_APPEARANCE, appearances);

        // A snap row with no matching stat line (offensive linemen, for instance) updates
        // nothing; that is expected, not an error. A row is used when it set a snap share or
        // is an appearance -- it can be both, so the two counts do not add up to the rows read.
        int changed = 0;
        int used = 0;
        int next = 0;
        for (SnapRow row : rows) {
            boolean updated = row.update() != null && applied[next++] > 0;
            changed += updated ? 1 : 0;
            used += updated || row.appearance() != null ? 1 : 0;
        }
        log.info("{} {}: {} snap shares set, {} skill-player appearances upserted",
                SOURCE, season, changed, appearances.size());
        return new IngestResult(SOURCE, rows.size(), changed + appearances.size(), rows.size() - used);
    }

    /** What one snap row writes: a snap share, an appearance, both, or neither; null when absent. */
    private record SnapRow(Object[] update, Object[] appearance) {}
}
