package com.fantasykai.query;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * How a ranking aggregates a player's weeks: §7's {@code scope=season|per_game|last4}.
 *
 * <p>All three sum <em>per-game</em> scores rather than scoring a summed stat
 * line. That is not an implementation detail -- threshold bonuses make
 * {@link com.fantasykai.scoring.ScoringEngine} non-linear, so a "100+ rush yards"
 * bonus is earned once per qualifying game and scoring a season total would pay
 * it at most once per season.
 */
public enum RankingScope {

    SEASON("season"),
    PER_GAME("per_game"),
    LAST4("last4");

    /** Weeks in the {@code last4} window. */
    public static final int LAST_N_WEEKS = 4;

    private final String param;

    RankingScope(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    public static RankingScope from(String value) {
        if (value == null || value.isBlank()) {
            return SEASON;
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(scope -> scope.param.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new InvalidQueryParameterException(
                        "unknown scope \"" + value + "\"; allowed: " + allowed()));
    }

    static String allowed() {
        return Arrays.stream(values()).map(RankingScope::param).collect(Collectors.joining(", "));
    }
}
