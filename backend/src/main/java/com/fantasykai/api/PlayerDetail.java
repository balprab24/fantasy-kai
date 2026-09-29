package com.fantasykai.api;

import java.time.LocalDate;

/**
 * {@code /players/{id}}. Adds the nflverse canonical id and display identity to
 * the summary fields.
 *
 * <p>No age: that is arithmetic on {@code birthDate} and today, and a field that
 * changes on a birthday with no change to the data does not belong in a response
 * a cache might one day hold. A season's age is on {@link CareerSeason}, where it
 * is a pure function of the two.
 *
 * @param espnId    digits only, or null -- the only input a headshot needs
 * @param birthDate null for 265 of 25,066 players (2026-09-28): the source's 32
 *                  blanks, and players {@code StatIngestor} created from a stat
 *                  line before the player file named them
 * @param teamName  the current team's full name; null for a free agent
 * @param teamLogo  https URL or null
 */
public record PlayerDetail(long id, String gsisId, String name, String position,
        String team, String status, String espnId, LocalDate birthDate,
        String teamName, String teamLogo) {}
