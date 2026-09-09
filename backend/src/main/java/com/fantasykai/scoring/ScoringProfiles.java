package com.fantasykai.scoring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Loads scoring profiles and hands back compiled rulesets.
 *
 * <p>Compilation is cached by profile id because it is pure -- the same row
 * always compiles to the same rates -- and because §9's whole diagnosis is that
 * the rankings endpoint is CPU-bound. Re-parsing JSON and rebuilding rate arrays
 * on every request would be work done per caller for a result that is identical
 * across callers.
 *
 * <p>This is not the §9 cache. That one keys on {@link ResolvedRuleset#hash()}
 * and stores computed rankings in Redis; this only avoids re-parsing the rules
 * themselves. Phase 11 adds the other.
 */
@Service
public class ScoringProfiles {

    /**
     * The tenant filter, and it lives here in the query rather than in a
     * service-layer check. Handoff §8.
     *
     * <p>A {@code null} {@code userId} binds to the second clause and matches
     * nothing, because {@code user_id = NULL} is never true in SQL -- so a
     * logged-out caller resolves system presets and only system presets. That
     * is what lets {@code GET /rankings} stay public without exposing anyone's
     * custom league: the chain gates the endpoint, this gates the rows.
     */
    private static final String BY_ID_VISIBLE_TO = """
            SELECT user_id, rules FROM scoring_profiles
             WHERE id = ? AND (user_id IS NULL OR user_id = ?)
            """;
    private static final String PRESET_BY_NAME =
            "SELECT rules FROM scoring_profiles WHERE name = ? AND is_preset = TRUE";

    private final JdbcTemplate jdbc;
    private final RulesetJson json;
    private final RulesetValidator validator;
    private final Map<Long, Cached> compiled = new ConcurrentHashMap<>();

    public ScoringProfiles(JdbcTemplate jdbc, RulesetJson json, RulesetValidator validator) {
        this.jdbc = jdbc;
        this.json = json;
        this.validator = validator;
    }

    /**
     * The caller's compiled ruleset for a profile they are allowed to see.
     *
     * <p><strong>The cache entry carries its owner, and that is load-bearing.</strong>
     * The query above is the authoritative filter, but it only runs on a miss --
     * so once one user has warmed profile 5, a plain
     * {@code computeIfAbsent(profileId, ...)} would hand their private ruleset
     * to the next caller without the filter ever executing again. Memoization
     * turns into an IDOR the moment the cached thing is not public. Re-checking
     * the owner on a hit costs one reference comparison and closes it.
     *
     * <p>A profile someone else owns is <strong>404, not 403</strong>: 403 would
     * confirm that the id exists, which is the fact being protected.
     *
     * @param userId the authenticated user, or {@code null} for an anonymous caller
     */
    public ResolvedRuleset byId(long profileId, Long userId) {
        Cached hit = compiled.computeIfAbsent(profileId, id -> load(id, userId));
        if (!hit.visibleTo(userId)) {
            throw new NoSuchProfileException("no scoring profile with id " + profileId);
        }
        return hit.ruleset();
    }

    private Cached load(long profileId, Long userId) {
        try {
            return jdbc.queryForObject(BY_ID_VISIBLE_TO,
                    (rs, n) -> {
                        // wasNull() reports on the LAST column read, and Java
                        // evaluates arguments left to right -- so reading rules
                        // first would make this ask "was `rules` null?", get
                        // false, and turn a preset's NULL owner into user 0.
                        // Every profile then belongs to a user who cannot exist
                        // and the presets 404 for everybody.
                        long owner = rs.getLong("user_id");
                        Long ownerId = rs.wasNull() ? null : owner;
                        return new Cached(compile(rs.getString("rules")), ownerId);
                    },
                    profileId, userId);
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchProfileException("no scoring profile with id " + profileId);
        }
    }

    /** Presets are seeded by V3 and named there; see {@code V3__seed_scoring_presets.sql}. */
    public ResolvedRuleset preset(String name) {
        try {
            return compile(jdbc.queryForObject(PRESET_BY_NAME, String.class, name));
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchProfileException("no preset named \"" + name + "\"");
        }
    }

    /**
     * Validate on the way in, not only on the way out. A stored profile is
     * re-checked on load so a row written before a rule tightened, or edited
     * outside the app, cannot quietly become the thing that scores a ranking.
     */
    public ResolvedRuleset compile(String rulesJson) {
        return ResolvedRuleset.compile(validator.validate(json.read(rulesJson)));
    }

    /**
     * Drops the compile cache for one profile. Every write path must call this
     * or the author keeps scoring against their pre-edit ruleset until restart.
     */
    public void evict(long profileId) {
        compiled.remove(profileId);
    }

    /**
     * @param ownerId {@code null} for a system preset, which is visible to everyone
     */
    private record Cached(ResolvedRuleset ruleset, Long ownerId) {

        boolean visibleTo(Long userId) {
            return ownerId == null || ownerId.equals(userId);
        }
    }
}
