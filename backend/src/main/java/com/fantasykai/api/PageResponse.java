package com.fantasykai.api;

import java.util.List;

/**
 * One page of results. §7: "cursor or offset pagination everywhere (never
 * unbounded lists)" -- this is the offset half, matching the {@code page=0&size=50}
 * shape every example in the doc uses.
 */
public record PageResponse<T>(List<T> content, int page, int size, long total, int totalPages) {

    /** Default page size, matching every {@code size=50} example in §7. */
    public static final int DEFAULT_SIZE = 50;

    /**
     * Hard ceiling on {@code size}. §7 forbids unbounded lists, and without a cap
     * a single caller could ask the rankings endpoint to serialize every skill
     * player in one response.
     */
    public static final int MAX_SIZE = 200;

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        return new PageResponse<>(content, page, size, total,
                size == 0 ? 0 : (int) ((total + size - 1) / size));
    }
}
