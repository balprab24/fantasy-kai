package com.fantasykai.query;

import java.time.LocalDate;

/**
 * A player as the API lists them.
 *
 * <p>Always keyed on {@code id}: {@code full_name} is not unique -- 832 names in
 * the players table are shared, 24 of them between players who both have stat
 * lines. "Josh Allen" is a quarterback and a center. Name is for display.
 *
 * @param team      abbreviation, null for a free agent
 * @param espnId    digits only, or null -- see {@link PlayerQueryRepository#espnId}
 * @param birthDate null for the players nflverse has no birth date for
 * @param teamName  the current team's full name, null for a free agent
 * @param teamLogo  an https URL or null; V6's CHECK guarantees the scheme
 */
public record PlayerRow(long id, String gsisId, String name, String position,
        String team, String status, String espnId, LocalDate birthDate,
        String teamName, String teamLogo) {}
