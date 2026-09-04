package com.fantasykai.scoring;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every stat a v1 ruleset is allowed to score.
 *
 * <p>This enum does three jobs at once, which is the point. It is the
 * allowlist a ruleset is validated against (§8: an unvalidated JSONB column is
 * an injection surface); its {@link #index()} is the position of the stat in
 * the {@code double[]} the evaluator dot-products, so scoring a row never does
 * a hash lookup per stat; and its {@link #json()} name is also the
 * {@code player_game_stats} column name, so the query in Phase 3 can be
 * generated from this list rather than hand-maintained beside it.
 *
 * <p>The set matches the {@code base} block in §6. Kicking distance buckets and
 * defensive stats are stored (§5) but not scorable until the v2 ruleset grows a
 * shape for them -- adding one here is a single line, which is the point of
 * keeping the allowlist in one place.
 */
public enum StatKey {
    PASS_YD("pass_yd"),
    PASS_TD("pass_td"),
    PASS_INT("pass_int"),
    PASS_2PT("pass_2pt"),

    RUSH_YD("rush_yd"),
    RUSH_TD("rush_td"),
    RUSH_2PT("rush_2pt"),

    REC("rec"),
    REC_YD("rec_yd"),
    REC_TD("rec_td"),
    REC_2PT("rec_2pt"),

    FUM_LOST("fum_lost"),
    RET_TD("ret_td");

    /** Width of the rate and stat arrays. */
    public static final int COUNT = values().length;

    private static final Map<String, StatKey> BY_JSON = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(StatKey::json, Function.identity()));

    private final String json;

    StatKey(String json) {
        this.json = json;
    }

    /** The key as it appears in a ruleset, and as the stat column is named. */
    public String json() {
        return json;
    }

    /** Index into a rate or stat array. */
    public int index() {
        return ordinal();
    }

    /** Empty for anything not on the allowlist -- the validator turns that into a rejection. */
    public static Optional<StatKey> fromJson(String name) {
        return Optional.ofNullable(BY_JSON.get(name));
    }
}
