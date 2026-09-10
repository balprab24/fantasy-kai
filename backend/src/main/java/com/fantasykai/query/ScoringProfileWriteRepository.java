package com.fantasykai.query;

import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The write half of {@code scoring_profiles} -- the first writer in this
 * package, which until Phase 5 held only reads.
 *
 * <p><strong>Every statement here carries {@code user_id = ?} in its WHERE
 * clause</strong>, including the update and the delete. Handoff §8 puts the
 * tenant filter in the query rather than in a service check, and a write is
 * exactly where that matters most: a service-layer "is this yours?" followed by
 * an unfiltered {@code UPDATE ... WHERE id = ?} is a race, and one missing
 * check is somebody editing another user's league. Here the filter is part of
 * the statement, so a mismatch updates zero rows and there is no window.
 *
 * <p>{@code is_preset} is never settable from a request. A user-authored
 * profile is always {@code FALSE}; presets come from {@code V3} and no code
 * path creates another.
 */
@Repository
public class ScoringProfileWriteRepository {

    private final JdbcTemplate jdbc;

    public ScoringProfileWriteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @throws DuplicateProfileNameException if the user already has one by that
     *     name, caught from V5's partial unique index rather than pre-checked --
     *     a check-then-insert races with itself.
     */
    public long create(long userId, String name, String rulesJson) {
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO scoring_profiles (user_id, name, rules, is_preset)
                    VALUES (?, ?, ?::jsonb, FALSE) RETURNING id
                    """, Long.class, userId, name, rulesJson);
            if (id == null) {
                throw new IllegalStateException("insert returned no id for profile " + name);
            }
            return id;
        } catch (DuplicateKeyException e) {
            throw new DuplicateProfileNameException(name);
        }
    }

    /**
     * @return false when the profile does not exist, is a preset, or belongs to
     *     someone else -- three cases the caller deliberately cannot tell apart,
     *     because distinguishing them would confirm that an id exists
     */
    public boolean update(long profileId, long userId, String name, String rulesJson) {
        try {
            return jdbc.update("""
                    UPDATE scoring_profiles SET name = ?, rules = ?::jsonb
                     WHERE id = ? AND user_id = ?
                    """, name, rulesJson, profileId, userId) == 1;
        } catch (DuplicateKeyException e) {
            throw new DuplicateProfileNameException(name);
        }
    }

    /** @return false in the same three indistinguishable cases as {@link #update} */
    public boolean delete(long profileId, long userId) {
        return jdbc.update("DELETE FROM scoring_profiles WHERE id = ? AND user_id = ?",
                profileId, userId) == 1;
    }

    /** The name of a profile the caller owns, for a response body after a write. */
    public Optional<String> findOwnName(long profileId, long userId) {
        return jdbc.query("SELECT name FROM scoring_profiles WHERE id = ? AND user_id = ?",
                        (rs, n) -> rs.getString("name"), profileId, userId)
                .stream().findFirst();
    }
}
