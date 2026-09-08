package com.fantasykai.query;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The positions v1 scores.
 *
 * <p>§6 defers K and DST to v2, but ingestion stores every position on purpose
 * (§11) -- so the filter lives here, in the query, and nowhere near the ingest.
 * Of 112,319 stored stat rows only 36,567 are these four.
 */
public enum ScoringPosition {

    QB, RB, WR, TE;

    public static final List<String> ALL =
            Arrays.stream(values()).map(Enum::name).toList();

    /** @return the single requested position, or all four when unfiltered */
    public static List<String> resolve(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        String wanted = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .map(Enum::name)
                .filter(wanted::equals)
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new InvalidQueryParameterException(
                        "unknown position \"" + value + "\"; allowed: " + allowed()));
    }

    static String allowed() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
