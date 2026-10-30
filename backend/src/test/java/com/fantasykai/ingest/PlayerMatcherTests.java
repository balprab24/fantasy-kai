package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlayerMatcherTests {
    private static final LocalDate DATE = LocalDate.of(1999, 9, 22);
    private static PlayerMatcher.Player player(long id, String name, String position, LocalDate date) {
        return new PlayerMatcher.Player(id, "gsis" + id, name, position, date,
                Map.of("espn", "espn" + id, "sleeper", "sleeper" + id));
    }
    private static PlayerMatcher.Identity identity(String external, String gsis, String espn,
                                                   String name, String position, LocalDate date) {
        return new PlayerMatcher.Identity("sleeper", external, gsis, espn, name, position, date);
    }
    @Test void idsPrecedeNamesAndEachFallbackWorks() {
        var matcher = new PlayerMatcher(List.of(player(1, "Josh Palmer", "WR", DATE)), Map.of());
        assertThat(matcher.match(identity("sleeper1", null, null, "wrong", "QB", null))).hasValue(1);
        assertThat(matcher.match(identity("new", " gsis1 ", null, "wrong", "QB", null))).hasValue(1);
        assertThat(matcher.match(identity("new", null, "espn1", "wrong", "QB", null))).hasValue(1);
        assertThat(matcher.match(identity("new", null, null, "Joshua Palmer Jr.", "WR", DATE))).hasValue(1);
    }
    @Test void normalizesAccentsPunctuationAndSuffixes() {
        assertThat(PlayerMatcher.normalize("José O'Neal III")).isEqualTo("jose oneal");
    }
    @Test void refusesAmbiguityButDateBreaksTie() {
        var matcher = new PlayerMatcher(List.of(player(1, "Josh Allen", "QB", DATE),
                player(2, "Josh Allen", "QB", DATE.plusYears(1))), Map.of());
        assertThat(matcher.match(identity("new", null, null, "Josh Allen", "QB", null))).isEmpty();
        assertThat(matcher.match(identity("new", null, null, "Josh Allen", "QB", DATE))).hasValue(1);
        assertThat(matcher.match(identity("new", null, null, "Josh Allen", "QB", DATE.plusYears(3)))).isEmpty();
    }
    @Test void crossPositionRequiresMatchingBirthDate() {
        var matcher = new PlayerMatcher(List.of(player(1, "Pat Ricard", "FB", DATE)), Map.of());
        assertThat(matcher.match(identity("new", null, null, "Pat Ricard", "RB", null))).isEmpty();
        assertThat(matcher.match(identity("new", null, null, "Pat Ricard", "RB", DATE))).hasValue(1);
    }
    @Test void overridesAreExplicitAndMustReferenceAnExistingPlayer() {
        var matcher = new PlayerMatcher(List.of(player(1, "Zonovan Knight", "RB", DATE)),
                Map.of("sleeper:new", 1L, "sleeper:bad", 99L));
        assertThat(matcher.match(identity("new", null, null, "Bam Knight", "RB", null))).hasValue(1);
        assertThat(matcher.match(identity("bad", null, null, "Bam Knight", "RB", null))).isEmpty();
        assertThat(matcher.match(identity("other", null, null, "Bam Knight", "RB", null))).isEmpty();
    }
    @Test void uniqueNameAndPositionDoNotRequireMatchingBirthDates() {
        var matcher = new PlayerMatcher(List.of(player(1, "Tyler Badie", "RB", DATE)), Map.of());
        assertThat(matcher.match(identity("new", null, null, "Tyler Badie", "RB", DATE.plusYears(1)))).hasValue(1);
    }

    @Test void duplicateStoredIdsAreRefusedRatherThanPickingTheFirst() {
        var matcher = new PlayerMatcher(List.of(
                new PlayerMatcher.Player(1, null, "One", "QB", null, Map.of("espn", "duplicate")),
                new PlayerMatcher.Player(2, null, "Two", "QB", null, Map.of("espn", "duplicate"))), Map.of());
        assertThat(matcher.match(identity("new", null, "duplicate", "One", "QB", null))).isEmpty();
    }

    @Test void namesAloneAndBlankIdsNeverMatch() {
        var matcher = new PlayerMatcher(List.of(player(1, "Josh Allen", "QB", DATE)), Map.of());
        assertThat(matcher.match(identity("", "", "", "Josh Allen", null, null))).isEmpty();
        assertThat(matcher.match(identity(null, null, null, null, null, null))).isEmpty();
    }
}
