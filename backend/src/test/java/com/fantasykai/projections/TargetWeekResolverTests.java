package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * "Next week" through a season: before it, mid-week, a Monday night still missing its stats,
 * the rollover, a game that never reports, and after week 18. Every week here has a Thursday,
 * a Sunday and a Monday game, timed in Eastern as nflverse publishes them.
 */
class TargetWeekResolverTests {

    private static final ZoneId EASTERN = ZoneId.of("America/New_York");

    private final ProjectionDatabase db = ProjectionDatabase.get();
    private final TargetWeekResolver resolver = new TargetWeekResolver(db.jdbc);

    /** "week:day" -> game id, for the 2026 schedule seeded by {@link #schedule}. */
    private final Map<String, Long> games = new HashMap<>();
    private final Map<Long, Integer> homeTeam = new HashMap<>();
    private long scorer;
    private int[] teams;

    @BeforeEach
    void seed() {
        db.reset();
        games.clear();
        homeTeam.clear();
        teams = new int[] {db.team("CIN"), db.team("BAL"), db.team("KC"), db.team("DET"),
                db.team("BUF"), db.team("TB")};
        scorer = db.player("Scorer", "WR", teams[0]);
    }

    @Test
    void beforeTheSeasonTheNextWeekIsWeekOne() {
        schedule(2026, 1, 6);

        Instant august = et(LocalDate.of(2026, 8, 20), 6, 0);

        TargetWeek target = resolver.resolve(2026, august).orElseThrow();

        assertThat(target.week()).isEqualTo(new SeasonWeek(2026, 1));
        assertThat(target.games()).hasSize(3).noneMatch(g -> g.kickedOffBy(august));
    }

    @Test
    void aSeasonWithNoScheduleYetHasNoWeekToProject() {
        schedule(2026, 1, 6);

        assertThat(resolver.resolve(2027, Instant.parse("2027-05-01T10:00:00Z"))).isEmpty();
    }

    /** Saturday of week 5: Thursday is in, Sunday and Monday are still to come. */
    @Test
    void midWeekTheWeekBeingPlayedIsStillTheTarget() {
        schedule(2026, 1, 6);
        complete(1, 4);
        played(5, DayOfWeek.THURSDAY);
        Instant saturday = day(5, DayOfWeek.SATURDAY, 6, 0);

        TargetWeek target = resolver.resolve(2026, saturday).orElseThrow();

        assertThat(target.week()).isEqualTo(new SeasonWeek(2026, 5));
        assertThat(target.games()).filteredOn(g -> g.kickedOffBy(saturday)).hasSize(1);
    }

    /**
     * Tuesday 06:00: Monday night has kicked off but its stats are not in. The week stays, with
     * every game kicked off -- so every row is locked -- rather than rolling forward on two
     * thirds of the evidence.
     */
    @Test
    void untilMondayNightsStatsLandTheWeekStaysWithEveryGameKickedOff() {
        schedule(2026, 1, 6);
        complete(1, 4);
        played(5, DayOfWeek.THURSDAY);
        played(5, DayOfWeek.SUNDAY);
        Instant tuesday = day(5, DayOfWeek.TUESDAY, 6, 0);

        TargetWeek target = resolver.resolve(2026, tuesday).orElseThrow();

        assertThat(target.week()).isEqualTo(new SeasonWeek(2026, 5));
        assertThat(target.games()).allMatch(g -> g.kickedOffBy(tuesday));
    }

    @Test
    void whenMondayNightsStatsLandTheWeekRollsForward() {
        schedule(2026, 1, 6);
        complete(1, 5);

        assertThat(resolver.resolve(2026, day(5, DayOfWeek.TUESDAY, 6, 0)).orElseThrow().week())
                .isEqualTo(new SeasonWeek(2026, 6));
    }

    /**
     * A game with no stats 48 hours after kickoff stops holding the week -- so a late Monday
     * night feed cannot leave Thursday night unprojected. The boundary, to the second.
     */
    @Test
    void aGameStillUnreportedFortyEightHoursAfterKickoffNoLongerHoldsTheWeek() {
        schedule(2026, 1, 6);
        complete(1, 4);
        played(5, DayOfWeek.THURSDAY);
        played(5, DayOfWeek.SUNDAY);
        Instant mondayKickoff = kickoff(5, DayOfWeek.MONDAY);

        assertThat(resolver.resolve(2026, mondayKickoff.plusSeconds(48 * 3600 - 1)).orElseThrow().week())
                .isEqualTo(new SeasonWeek(2026, 5));
        assertThat(resolver.resolve(2026, mondayKickoff.plusSeconds(48 * 3600)).orElseThrow().week())
                .isEqualTo(new SeasonWeek(2026, 6));
        assertThat(resolver.resolve(2026, day(6, DayOfWeek.THURSDAY, 6, 0)).orElseThrow().week())
                .as("Thursday 06:00 ET, the last run before Thursday night")
                .isEqualTo(new SeasonWeek(2026, 6));
    }

    /**
     * Off the schedule -- nflverse deleted the row, so the ingest delisted it -- is not part of
     * the week at all: it stops holding it at once, an hour after kickoff, not after 48.
     */
    @Test
    void aGameOffTheScheduleStopsHoldingTheWeekAtOnce() {
        schedule(2026, 1, 6);
        complete(1, 4);
        played(5, DayOfWeek.THURSDAY);
        played(5, DayOfWeek.SUNDAY);
        db.delist(games.get("5:" + DayOfWeek.MONDAY));

        assertThat(resolver.resolve(2026, kickoff(5, DayOfWeek.MONDAY).plusSeconds(3600)).orElseThrow().week())
                .isEqualTo(new SeasonWeek(2026, 6));
    }

    /** ...and it is never one of the week's games, so nothing is projected for it. */
    @Test
    void aGameOffTheScheduleIsNotATargetGame() {
        schedule(2026, 1, 6);
        complete(1, 4);
        long sunday = games.get("5:" + DayOfWeek.SUNDAY);
        db.delist(sunday);

        // Wednesday 06:00 before week 5's Thursday game: the week a run would be projecting.
        TargetWeek target = resolver.resolve(2026, day(4, DayOfWeek.WEDNESDAY, 6, 0)).orElseThrow();

        assertThat(target.week()).isEqualTo(new SeasonWeek(2026, 5));
        assertThat(target.games()).hasSize(2).extracting(TargetWeek.Game::id).doesNotContain(sunday);
    }

    /**
     * A late feed is not a cancellation. A listed game with no stats is still a game of its week
     * -- a target game, holding the week -- until 48 hours after kickoff; then it stops holding
     * the week but stays listed, and its stats still count when they land.
     */
    @Test
    void lateStatsAreNotMistakenForACancellation() {
        schedule(2026, 1, 6);
        complete(1, 4);
        played(5, DayOfWeek.THURSDAY);
        played(5, DayOfWeek.SUNDAY);
        long monday = games.get("5:" + DayOfWeek.MONDAY);
        Instant kickoff = kickoff(5, DayOfWeek.MONDAY);

        TargetWeek thirtyHoursLate = resolver.resolve(2026, kickoff.plusSeconds(30 * 3600)).orElseThrow();
        assertThat(thirtyHoursLate.week()).isEqualTo(new SeasonWeek(2026, 5));
        assertThat(thirtyHoursLate.games()).extracting(TargetWeek.Game::id).contains(monday);

        assertThat(resolver.resolve(2026, kickoff.plusSeconds(49 * 3600)).orElseThrow().week())
                .isEqualTo(new SeasonWeek(2026, 6));
        assertThat(db.jdbc.queryForObject("SELECT delisted_at IS NULL FROM games WHERE id = ?", Boolean.class, monday))
                .as("rolled past, not removed: the schedule still lists it").isTrue();

        played(5, DayOfWeek.MONDAY);
        assertThat(resolver.resolve(2026, kickoff.plusSeconds(60 * 3600)).orElseThrow().week())
                .as("the stats landing changes nothing about the rollover").isEqualTo(new SeasonWeek(2026, 6));
    }

    /** A game with no date cannot be judged late: it holds its week until its stats exist. */
    @Test
    void aGameWithNoKickoffTimeIsNeverTakenAsFinished() {
        schedule(2026, 1, 1);
        complete(1, 1);
        db.game(2026, 2, teams[0], teams[1], null);

        assertThat(resolver.resolve(2026, Instant.parse("2027-01-30T12:00:00Z")).orElseThrow().week())
                .isEqualTo(new SeasonWeek(2026, 2));
    }

    @Test
    void afterWeekEighteenThereIsNothingToProjectWhateverThePlayoffsLookLike() {
        schedule(2026, 17, 18);
        complete(17, 18);
        db.game(2026, 19, "WC", teams[0], teams[1], Instant.parse("2027-01-16T21:30:00Z"));

        assertThat(resolver.resolve(2026, Instant.parse("2027-01-12T11:00:00Z"))).isEmpty();
    }

    /** 2020 had 17 regular-season weeks, and its wild card round is numbered week 18. */
    @Test
    void aWildCardGameNumberedWeekEighteenIsNotARegularSeasonWeek() {
        schedule(2020, 16, 17);
        complete(16, 17);
        db.game(2020, 18, "WC", teams[0], teams[1], Instant.parse("2021-01-09T18:05:00Z"));

        assertThat(resolver.resolve(2020, Instant.parse("2021-01-06T11:00:00Z"))).isEmpty();
    }

    @Test
    void theTargetCarriesEveryRegularSeasonGameOfItsWeekWithItsTeamsAndKickoff() {
        schedule(2026, 5, 5);

        Optional<TargetWeek> target = resolver.resolve(2026, Instant.parse("2026-09-01T10:00:00Z"));

        assertThat(target.orElseThrow().games()).extracting(TargetWeek.Game::kickoff).containsExactly(
                kickoff(5, DayOfWeek.THURSDAY), kickoff(5, DayOfWeek.SUNDAY), kickoff(5, DayOfWeek.MONDAY));
        assertThat(target.orElseThrow().games().getFirst().homeTeamId()).isEqualTo(teams[0]);
        assertThat(target.orElseThrow().games().getFirst().awayTeamId()).isEqualTo(teams[1]);
    }

    // --- the schedule ---------------------------------------------------------------------

    private int season = 2026;

    /** Weeks {@code from..to}: CIN-BAL Thursday 8:15, KC-DET Sunday 1:00, BUF-TB Monday 8:15 ET. */
    private void schedule(int season, int from, int to) {
        this.season = season;
        for (int week = from; week <= to; week++) {
            add(week, DayOfWeek.THURSDAY, teams[0], teams[1]);
            add(week, DayOfWeek.SUNDAY, teams[2], teams[3]);
            add(week, DayOfWeek.MONDAY, teams[4], teams[5]);
        }
    }

    private void add(int week, DayOfWeek day, int home, int away) {
        long id = db.game(season, week, home, away, kickoff(week, day));
        games.put(week + ":" + day, id);
        homeTeam.put(id, home);
    }

    /** Every game of weeks {@code from..to} gets its stat rows. */
    private void complete(int from, int to) {
        for (int week = from; week <= to; week++) {
            for (DayOfWeek day : List.of(DayOfWeek.THURSDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY)) {
                played(week, day);
            }
        }
    }

    private void played(int week, DayOfWeek day) {
        long game = games.get(week + ":" + day);
        db.played(scorer, game, homeTeam.get(game));
    }

    private Instant kickoff(int week, DayOfWeek day) {
        return day == DayOfWeek.SUNDAY ? day(week, day, 13, 0) : day(week, day, 20, 15);
    }

    /** {@code day} of the given week, in Eastern; a week runs Thursday to the next Wednesday. */
    private Instant day(int week, DayOfWeek day, int hour, int minute) {
        LocalDate sunday = firstSunday(season).plusWeeks(week - 1);
        int offset = switch (day) {
            case THURSDAY -> -3;
            case FRIDAY -> -2;
            case SATURDAY -> -1;
            case SUNDAY -> 0;
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
        };
        return et(sunday.plusDays(offset), hour, minute);
    }

    private static LocalDate firstSunday(int season) {
        return season == 2020 ? LocalDate.of(2020, 9, 13) : LocalDate.of(2026, 9, 13);
    }

    private static Instant et(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(EASTERN).toInstant();
    }
}
