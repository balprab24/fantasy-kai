package com.fantasykai.query;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Lists scoring profiles for the picker.
 *
 * <p>Separate from {@link com.fantasykai.scoring.ScoringProfiles}, which exists
 * to compile a single profile into an evaluator. This one only ever reads
 * metadata -- it never touches the {@code rules} column, so an unparseable row
 * cannot take the list down.
 *
 * <p>Phase 3 serves presets only. Phase 5 adds {@code OR user_id = ?} bound to
 * the JWT subject, in this query rather than in a service-layer check (§8).
 */
@Repository
public class ScoringProfileQueryRepository {

    private static final String PRESETS = """
            SELECT id, name, is_preset FROM scoring_profiles
            WHERE user_id IS NULL
            ORDER BY id
            """;

    private static final RowMapper<ProfileRow> ROW = (rs, n) ->
            new ProfileRow(rs.getLong("id"), rs.getString("name"), rs.getBoolean("is_preset"));

    private final JdbcTemplate jdbc;

    public ScoringProfileQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProfileRow> findPresets() {
        return jdbc.query(PRESETS, ROW);
    }

    public record ProfileRow(long id, String name, boolean preset) {}
}
