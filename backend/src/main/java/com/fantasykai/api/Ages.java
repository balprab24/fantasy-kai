package com.fantasykai.api;

import java.time.LocalDate;
import java.time.Period;

/** How old a player was in a season. One rule, written down once. */
final class Ages {

    private Ages() {}

    /**
     * Whole years on 1 September of the season -- the age a player carried into
     * it, which is how a fantasy manager reads "a 24-year-old running back". A
     * birthday falling on 1 September counts as reached.
     */
    static Integer atSeasonStart(LocalDate birthDate, int season) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.of(season, 9, 1)).getYears();
    }
}
