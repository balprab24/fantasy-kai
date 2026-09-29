package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Age on 1 September of the season -- the one rule, pinned at its edges. */
class AgesTests {

    @Test
    void aBirthdayOnTheFirstOfSeptemberHasBeenReached() {
        assertThat(Ages.atSeasonStart(LocalDate.of(2000, 9, 1), 2025)).isEqualTo(25);
    }

    @Test
    void aBirthdayTheDayAfterHasNot() {
        assertThat(Ages.atSeasonStart(LocalDate.of(2000, 9, 2), 2025)).isEqualTo(24);
    }

    @Test
    void aLeapDayBirthdayIsNoSpecialCaseInSeptember() {
        assertThat(Ages.atSeasonStart(LocalDate.of(2000, 2, 29), 2025)).isEqualTo(25);
    }

    @Test
    void noBirthDateIsNoAgeRatherThanZero() {
        assertThat(Ages.atSeasonStart(null, 2025)).isNull();
    }
}
