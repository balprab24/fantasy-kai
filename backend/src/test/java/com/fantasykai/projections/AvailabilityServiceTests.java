package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Which week's facts answer for a week: rosters carry until a team's next week is published;
 * injury designations never carry. The real-row classification is in
 * {@code ingest.AvailabilityIngestTests}; these pin the clock.
 */
class AvailabilityServiceTests {

    private static final SeasonWeek WEEK_6 = new SeasonWeek(2026, 6);

    private final ProjectionDatabase db = ProjectionDatabase.get();
    private final AvailabilityService service = new AvailabilityService(db.jdbc);

    private int cin;
    private int nyg;
    private long chase;
    private long higgins;

    @BeforeEach
    void seed() {
        db.reset();
        cin = db.team("CIN");
        nyg = db.team("NYG");
        chase = db.player("00-0036900", "Ja'Marr Chase", "WR", cin);
        higgins = db.player("00-0036410", "Tee Higgins", "WR", cin);
    }

    /** Week 6's rosters are not out yet: week 5's stands, and says which week it is from. */
    @Test
    void untilATeamsWeekIsPublishedItsPreviousWeekStandsAndSaysSo() {
        db.roster(2026, 5, chase, cin, "ACT");
        db.roster(2026, 5, higgins, cin, "RES");

        Map<Long, Availability> week6 = service.forWeek(WEEK_6);

        assertThat(week6.get(chase).status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(week6.get(higgins).status()).as("a reserve stint lasts weeks by rule")
                .isEqualTo(AvailabilityStatus.RESERVE);
        assertThat(week6.get(chase).rosterWeek()).isEqualTo(5);
        assertThat(week6.get(chase).rosterIsCurrent()).isFalse();
    }

    /** Once a team's week is out it is the only one read for that team. */
    @Test
    void aPublishedWeekReplacesTheOneBefore() {
        db.roster(2026, 5, higgins, cin, "RES");
        db.roster(2026, 6, higgins, cin, "ACT"); // activated

        Availability a = service.of(higgins, WEEK_6);

        assertThat(a.status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(a.rosterIsCurrent()).isTrue();
    }

    /** Teams publish separately: each team is read at its own latest week. */
    @Test
    void eachTeamIsReadAtItsOwnLatestPublishedWeek() {
        long mccarthy = db.player("00-0039923", "J.J. McCarthy", "QB", nyg);
        db.roster(2026, 5, chase, cin, "ACT");
        db.roster(2026, 5, mccarthy, nyg, "ACT");
        db.roster(2026, 6, mccarthy, nyg, "ACT");

        Map<Long, Availability> week6 = service.forWeek(WEEK_6);

        assertThat(week6.get(chase).rosterWeek()).isEqualTo(5);
        assertThat(week6.get(mccarthy).rosterWeek()).isEqualTo(6);
    }

    /** A player gone from his team's newest roster is not on it, however recently he was. */
    @Test
    void aPlayerMissingFromHisTeamsNewestRosterIsNotOnIt() {
        db.roster(2026, 5, chase, cin, "ACT");
        db.roster(2026, 5, higgins, cin, "ACT");
        db.roster(2026, 6, chase, cin, "ACT"); // Higgins absent from week 6

        assertThat(service.roster(WEEK_6)).containsOnlyKeys(chase);
        assertThat(service.of(higgins, WEEK_6).status()).isEqualTo(AvailabilityStatus.UNKNOWN);
    }

    /** Traded: his newest row wins, the new team's, whichever team published first. */
    @Test
    void aTradedPlayerIsReadFromHisNewestRow() {
        db.roster(2026, 5, chase, cin, "ACT");
        db.roster(2026, 6, chase, nyg, "ACT");

        assertThat(service.of(chase, WEEK_6).teamId()).isEqualTo(nyg);
    }

    /** A designation belongs to its week: week 5's Questionable says nothing about week 6. */
    @Test
    void aDesignationNeverCarriesIntoTheNextWeek() {
        db.roster(2026, 5, chase, cin, "ACT");
        db.roster(2026, 6, chase, cin, "ACT");
        db.injury(2026, 5, chase, cin, "Questionable");

        assertThat(service.of(chase, new SeasonWeek(2026, 5)).status()).isEqualTo(AvailabilityStatus.QUESTIONABLE);
        assertThat(service.of(chase, WEEK_6).status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(service.of(chase, WEEK_6).reportStatus()).isNull();
    }

    /** A report with no roster row still counts; nothing at all is UNKNOWN, and ranked. */
    @Test
    void aReportWithoutARosterRowCountsAndNothingKnownIsRanked() {
        db.injury(2026, 6, chase, cin, "Out");

        assertThat(service.of(chase, WEEK_6).status()).isEqualTo(AvailabilityStatus.OUT);
        assertThat(service.of(chase, WEEK_6).rosterWeek()).isNull();
        assertThat(service.of(higgins, WEEK_6)).satisfies(a -> {
            assertThat(a.status()).isEqualTo(AvailabilityStatus.UNKNOWN);
            assertThat(a.ranked()).isTrue();
        });
    }

    /** An empty feed is not a verdict on anyone: no roster, no report, everyone UNKNOWN and ranked. */
    @Test
    void noFeedsAtAllMakesNobodyUnavailable() {
        assertThat(service.forWeek(WEEK_6)).isEmpty();
        assertThat(service.of(chase, WEEK_6).ranked()).isTrue();
    }

    /**
     * Only the season asked about: last season's roster says nothing about this one -- not even
     * a row for the same team in the same week number.
     */
    @Test
    void anotherSeasonsRosterIsNeverRead() {
        db.roster(2025, 18, chase, cin, "RES");
        assertThat(service.roster(WEEK_6)).isEmpty();

        db.roster(2026, 5, higgins, cin, "ACT");
        db.roster(2025, 5, chase, cin, "RES");

        assertThat(service.roster(WEEK_6)).containsOnlyKeys(higgins);
    }
}
