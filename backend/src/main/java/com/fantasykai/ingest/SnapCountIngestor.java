package com.fantasykai.ingest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fills in snap share, which lives in its own nflverse release and joins on the
 * Pro Football Reference id rather than gsis -- so it needs the pfr id that
 * {@link PlayerIngestor} stashes in players.external_ids.
 */
@Component
public class SnapCountIngestor {

    static final String SOURCE = "nflverse.snap_counts";

    private static final String UPDATE = """
            UPDATE player_game_stats SET snap_pct = ?
             WHERE player_id = ? AND game_id = ?
            """;

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

        Map<String, Long> gameIds = new HashMap<>();
        jdbc.query("SELECT nflverse_game_id, id FROM games", rs -> {
            gameIds.put(rs.getString(1), rs.getLong(2));
        });

        List<Object[]> updates = new ArrayList<>();
        List<?> rows = client.read("snap_counts", "snap_counts_%d.csv".formatted(season), record -> {
            String pfrId = CsvValues.text(record, "pfr_player_id");
            String gameKey = CsvValues.text(record, "game_id");
            // offense_pct arrives as a 0..1 fraction; the column is a percentage.
            BigDecimal pct = CsvValues.fractionAsPercent(record, "offense_pct");
            if (pfrId == null || gameKey == null || pct == null) {
                return null;
            }
            Long playerId = byPfrId.get(pfrId);
            Long gameId = gameIds.get(gameKey);
            if (playerId == null || gameId == null) {
                return null;
            }
            updates.add(new Object[] {pct, playerId, gameId});
            return Boolean.TRUE;
        });

        int[] applied = jdbc.batchUpdate(UPDATE, updates);
        int changed = 0;
        for (int count : applied) {
            // A snap row with no matching stat line (offensive linemen, for instance)
            // updates nothing; that is expected, not an error.
            changed += Math.max(count, 0);
        }
        return new IngestResult(SOURCE, rows.size(), changed, rows.size() - changed);
    }
}
