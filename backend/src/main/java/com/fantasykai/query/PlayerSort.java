package com.fantasykai.query;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The columns {@code /players} may be ordered by, and the whole of §8's
 * "whitelist sortable columns by name".
 *
 * <p>A sort parameter is the one place a read API is tempted to concatenate,
 * because {@code ORDER BY} cannot take a bind parameter. Resolving the request
 * string to an enum constant first means the string that reaches the statement
 * is always one of these four literals -- {@code ?sort=name; DROP TABLE} does
 * not fail to match a pattern, it fails to be an enum constant.
 */
public enum PlayerSort {

    NAME("name", "p.full_name"),
    POSITION("position", "p.position"),
    TEAM("team", "t.abbr"),
    ID("id", "p.id");

    private final String param;
    private final String column;

    PlayerSort(String param, String column) {
        this.param = param;
        this.column = column;
    }

    public String param() {
        return param;
    }

    /** A compile-time constant. Never derived from a request. */
    public String column() {
        return column;
    }

    public static PlayerSort from(String value) {
        if (value == null || value.isBlank()) {
            return NAME;
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(sort -> sort.param.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new InvalidQueryParameterException(
                        "unknown sort \"" + value + "\"; allowed: " + allowed()));
    }

    static String allowed() {
        return Arrays.stream(values()).map(PlayerSort::param).collect(Collectors.joining(", "));
    }
}
