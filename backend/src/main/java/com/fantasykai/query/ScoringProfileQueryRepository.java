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
 * <p>The tenant filter is {@code OR user_id = ?} bound to the JWT subject, in
 * this query rather than in a service-layer check (§8). A {@code null} binds to
 * a clause that is never true, so a logged-out caller sees the four presets and
 * nothing else -- the same shape as {@code ScoringProfiles.byId}.
 */
@Repository
public class ScoringProfileQueryRepository {

    private static final String VISIBLE_TO = """
            SELECT id, name, is_preset FROM scoring_profiles
             WHERE user_id IS NULL OR user_id = ?
             ORDER BY is_preset DESC, name
            """;

    private static final RowMapper<ProfileRow> ROW = (rs, n) ->
            new ProfileRow(rs.getLong("id"), rs.getString("name"), rs.getBoolean("is_preset"));

    private final JdbcTemplate jdbc;

    public ScoringProfileQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The presets, plus the caller's own.
     *
     * <p>Ordered presets first then by name, rather than by id: the four presets
     * are the stable part of a profile switcher and a user's own list grows
     * unpredictably, so id order would shuffle the familiar entries down the
     * list as they add profiles.
     *
     * @param userId the authenticated caller, or {@code null} for presets only
     */
    public List<ProfileRow> findVisibleTo(Long userId) {
        return jdbc.query(VISIBLE_TO, ROW, userId);
    }

    public record ProfileRow(long id, String name, boolean preset) {}
}
