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
 * themselves. Phase 6 adds the other.
 */
@Service
public class ScoringProfiles {

    private static final String BY_ID =
            "SELECT rules FROM scoring_profiles WHERE id = ?";
    private static final String PRESET_BY_NAME =
            "SELECT rules FROM scoring_profiles WHERE name = ? AND is_preset = TRUE";

    private final JdbcTemplate jdbc;
    private final RulesetJson json;
    private final RulesetValidator validator;
    private final Map<Long, ResolvedRuleset> compiled = new ConcurrentHashMap<>();

    public ScoringProfiles(JdbcTemplate jdbc, RulesetJson json, RulesetValidator validator) {
        this.jdbc = jdbc;
        this.json = json;
        this.validator = validator;
    }

    public ResolvedRuleset byId(long profileId) {
        return compiled.computeIfAbsent(profileId, id -> {
            try {
                return compile(jdbc.queryForObject(BY_ID, String.class, id));
            } catch (EmptyResultDataAccessException e) {
                throw new InvalidRulesetException("no scoring profile with id " + id);
            }
        });
    }

    /** Presets are seeded by V3 and named there; see {@code V3__seed_scoring_presets.sql}. */
    public ResolvedRuleset preset(String name) {
        try {
            return compile(jdbc.queryForObject(PRESET_BY_NAME, String.class, name));
        } catch (EmptyResultDataAccessException e) {
            throw new InvalidRulesetException("no preset named \"" + name + "\"");
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

    /** Drops the compile cache; call after a profile is written. */
    public void evict(long profileId) {
        compiled.remove(profileId);
    }
}
