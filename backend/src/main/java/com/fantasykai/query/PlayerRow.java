package com.fantasykai.query;

/**
 * A player as the API lists them.
 *
 * <p>Always keyed on {@code id}: {@code full_name} is not unique -- 832 names in
 * the players table are shared, 24 of them between players who both have stat
 * lines. "Josh Allen" is a quarterback and a center. Name is for display.
 *
 * @param team abbreviation, null for a free agent
 */
public record PlayerRow(long id, String gsisId, String name, String position,
        String team, String status) {}
