package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantasykai.projections.Availability;
import com.fantasykai.projections.AvailabilityService;
import com.fantasykai.projections.AvailabilityStatus;
import com.fantasykai.projections.CurrentTargetWeek;
import com.fantasykai.projections.ProjectionDatabase;
import com.fantasykai.projections.JdbcProjectionInputs;
import com.fantasykai.projections.ProjectionProperties;
import com.fantasykai.projections.ProjectionRepository;
import com.fantasykai.projections.RawProjection;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.projections.TargetWeekResolver;
import com.fantasykai.projections.WeightedAverageProjectionProvider;
import com.fantasykai.query.ScoringProfileQueryRepository;
import com.fantasykai.scoring.Bonus;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.ScoringProfiles;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ProjectionOrderingTests {
    @Test void sortsRawPointsBeforePagingAndKeepsRawValueOutOfJson() {
        var db = ProjectionDatabase.get();
        db.reset();
        int home = db.team("BUF"), away = db.team("NYJ");
        long low = db.player("Lower", "WR", home), high = db.player("Higher", "WR", home);
        Instant now = Instant.parse("2030-09-01T00:00:00Z");
        long game = db.game(2030, 1, home, away, now.plusSeconds(86400));
        var week = new SeasonWeek(2030, 1);
        var provider = new WeightedAverageProjectionProvider();
        var rows = new java.util.ArrayList<ProjectionRepository.ProjectedRow>();
        for (long id : List.of(low, high)) {
            double[] stats = new double[StatKey.COUNT];
            stats[StatKey.REC_YD.index()] = id == low ? 100 : 100.04;
            rows.add(new ProjectionRepository.ProjectedRow(id, game, home,
                    new RawProjection(provider.source(), week, stats, 0, 0, 0, 4, new SeasonWeek(2029, 18))));
        }
        new ProjectionRepository(db.jdbc).replaceOpen(week, provider.source().id(), rows, now);
        var profiles = mock(ScoringProfiles.class);
        // The board asks for profile 1. The player page below also scores the line under the three
        // presets (ids 1-3), and no other id is stubbed: a board that resolved the wrong profile gets
        // null from the mock and fails, as it did before the strip existed.
        for (long id : List.of(1L, 2L, 3L)) {
            when(profiles.byId(id, 7L)).thenReturn(ResolvedRuleset.compile(
                    new Ruleset(1, Map.of(StatKey.REC_YD, 0.1), Map.of(), List.of())));
        }
        var availability = mock(AvailabilityService.class);
        when(availability.forWeek(eq(week), any())).thenReturn(Map.of());
        var controller = new ProjectionController(new ProjectionsService(db.jdbc, profiles, availability,
                new ProjectionProperties(provider.source().id(), false), provider,
                Clock.fixed(now, ZoneOffset.UTC), noCache(db), new JdbcProjectionInputs(db.jdbc), new ScoringProfileQueryRepository(db.jdbc)));
        var first = controller.board(1, null, "", 0, 1, 7L).page().content().getFirst();
        var second = controller.board(1, null, "", 1, 1, 7L).page().content().getFirst();
        assertThat(first.playerId()).isEqualTo(high);
        assertThat(second.playerId()).isEqualTo(low);
        assertThat(first.points()).isEqualTo(second.points()).isEqualTo(10.0);
        var json = new ObjectMapper().findAndRegisterModules().<com.fasterxml.jackson.databind.JsonNode>valueToTree(first);
        assertThat(json.has("rawPoints")).isFalse();
        // The explanation's wire shape: the parts, and one line per bonus (none in this ruleset).
        assertThat(json.get("parts")).hasSize(1);
        assertThat(json.get("parts").get(0).toString())
                .isEqualTo("{\"stat\":\"rec_yd\",\"value\":100.04,\"rate\":0.1,\"points\":" + (100.04 * 0.1) + "}");
        assertThat(json.get("bonuses")).isEmpty();
        assertThat(json.has("bonusPoints")).as("the one-number remainder is gone").isFalse();
        assertThat(controller.player(high, 1, 7L).projection().points()).isEqualTo(first.points());
    }

    /** Ties break on player id however the rows arrive: the SQL promises no order of its own. */
    @Test void breaksATieOnPlayerIdWhateverOrderTheRowsArriveIn() {
        var rows = new ArrayList<>(List.of(row(30, 10.0), row(10, 10.0), row(20, 10.004), row(5, 9.0)));
        for (int turn = 1; turn <= rows.size(); turn++) {
            Collections.rotate(rows, 1);
            assertThat(rows.stream().sorted(ProjectionsService.BOARD_ORDER).map(ProjectionRow::playerId).toList())
                    .as("arrival order after %d single rotations", turn).containsExactly(20L, 10L, 30L, 5L);
        }
    }

    /**
     * The same property through the board itself, so replacing {@code .sorted(BOARD_ORDER)} with a
     * comparator that forgets the id is caught. The stand-in below hands the service its lines in
     * descending player id whatever order the database chose, and records what it handed over: a
     * stable sort would keep that order for a tie, and the test fails loudly if the service ever
     * stops reading through the overload the stand-in controls.
     */
    @Test void theBoardBreaksTiesOnPlayerIdEvenWhenTheDatabaseReturnsThemBackwards() {
        var db = ProjectionDatabase.get();
        db.reset();
        int home = db.team("BUF"), away = db.team("NYJ");
        Instant now = Instant.parse("2030-09-01T00:00:00Z");
        long game = db.game(2030, 1, home, away, now.plusSeconds(86400));
        var week = new SeasonWeek(2030, 1);
        var provider = new WeightedAverageProjectionProvider();
        long a = db.player("Tie A", "WR", home), b = db.player("Tie B", "WR", home), c = db.player("Tie C", "WR", home);
        long top = db.player("Top", "WR", home);
        var rows = new ArrayList<ProjectionRepository.ProjectedRow>();
        for (long id : List.of(a, b, c, top)) {
            double[] stats = new double[StatKey.COUNT];
            stats[StatKey.REC_YD.index()] = id == top ? 100.04 : 100.0;
            rows.add(new ProjectionRepository.ProjectedRow(id, game, home,
                    new RawProjection(provider.source(), week, stats, 0, 0, 0, 4, new SeasonWeek(2029, 18))));
        }
        new ProjectionRepository(db.jdbc).replaceOpen(week, provider.source().id(), rows, now);

        List<List<Long>> handedOver = new ArrayList<>();
        JdbcTemplate backwards = new JdbcTemplate(db.jdbc.getDataSource()) {
            @Override public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                List<T> result = new ArrayList<>(super.query(sql, rowMapper, args));
                if (!result.isEmpty() && result.getFirst() instanceof ProjectionRow) {
                    result.sort(java.util.Comparator.comparingLong(line -> -((ProjectionRow) line).playerId()));
                    handedOver.add(result.stream().map(line -> ((ProjectionRow) line).playerId()).toList());
                }
                return result;
            }
        };

        var service = new ProjectionsService(backwards, profilesFor(1L, 7L,
                new Ruleset(1, Map.of(StatKey.REC_YD, 0.1), Map.of(), List.of())), noAvailability(week),
                new ProjectionProperties(provider.source().id(), false), provider, Clock.fixed(now, ZoneOffset.UTC), noCache(db), new JdbcProjectionInputs(db.jdbc), new ScoringProfileQueryRepository(db.jdbc));
        assertThat(ids(service.board(1, null, "", 0, 200, 7L))).containsExactly(top, a, b, c);
        for (int page = 0; page < 4; page++) {
            assertThat(ids(service.board(1, null, "", page, 1, 7L))).as("page %d of size 1", page)
                    .containsExactly(List.of(top, a, b, c).get(page));
        }
        assertThat(handedOver).as("the service read its lines through the stand-in, five times").hasSize(5);
        assertThat(handedOver).allSatisfy(order -> assertThat(order).as("what the service was handed")
                .containsExactly(top, c, b, a));
    }

    /**
     * What the member is ranked on is the unrounded score of the stored line under the ruleset,
     * bit for bit, over forty fractional lines (a position override and a bonus included). The
     * expected value is an independent dot product written out here, in the engine's summation
     * order, from the stored line and the player's position read from the players table, so it
     * does not go through {@code ScoringEngine} or through the row under test.
     *
     * <p>What this does not catch: re-summing the explanation's own parts in order, then the bonuses
     * that were reached, gives the engine's bits by construction ({@code ExplainedScoreTests} pins
     * that), so a row whose total were built that way would also pass. A compensated sum or a
     * reordering is not the engine's sum, and is caught.
     */
    @Test void theUnroundedPointsAreTheEnginesScoreBitForBit() {
        var db = ProjectionDatabase.get();
        db.reset();
        int home = db.team("BUF"), away = db.team("NYJ");
        Instant now = Instant.parse("2030-09-01T00:00:00Z");
        long game = db.game(2030, 1, home, away, now.plusSeconds(86400));
        var week = new SeasonWeek(2030, 1);
        var provider = new WeightedAverageProjectionProvider();
        Random random = new Random(7);
        String[] positions = {"QB", "RB", "WR", "TE"};
        var rows = new ArrayList<ProjectionRepository.ProjectedRow>();
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 40; i++) {
            long id = db.player("Bit " + i, positions[i % 4], home);
            ids.add(id);
            double[] stats = new double[StatKey.COUNT];
            for (StatKey key : StatKey.values()) {
                stats[key.index()] = random.nextInt(4) == 0 ? 0 : Math.round(random.nextDouble() * 120_000) / 1000.0;
            }
            rows.add(new ProjectionRepository.ProjectedRow(id, game, home,
                    new RawProjection(provider.source(), week, stats, 0, 0, 0, 4, new SeasonWeek(2029, 18))));
        }
        new ProjectionRepository(db.jdbc).replaceOpen(week, provider.source().id(), rows, now);

        Ruleset ruleset = new Ruleset(1,
                Map.of(StatKey.PASS_YD, 0.033, StatKey.PASS_TD, 4.0, StatKey.PASS_INT, -2.0, StatKey.RUSH_YD, 0.1,
                        StatKey.RUSH_TD, 6.0, StatKey.REC, 0.5, StatKey.REC_YD, 0.1, StatKey.REC_TD, 6.0),
                Map.of("TE", Map.of(StatKey.REC, 1.5)), List.of(new Bonus(StatKey.REC_YD, 50, 1.5)));
        ResolvedRuleset rules = ResolvedRuleset.compile(ruleset);
        var service = new ProjectionsService(db.jdbc, profilesFor(1L, 7L, ruleset), noAvailability(week),
                new ProjectionProperties(provider.source().id(), false), provider, Clock.fixed(now, ZoneOffset.UTC), noCache(db), new JdbcProjectionInputs(db.jdbc), new ScoringProfileQueryRepository(db.jdbc));

        var board = service.board(1, null, "", 0, 200, 7L).page().content();
        assertThat(board).hasSize(40);
        String columns = Arrays.stream(StatKey.values()).map(StatKey::json).collect(Collectors.joining(","));
        for (ProjectionRow row : board) {
            double[] stored = db.jdbc.queryForObject("SELECT " + columns + " FROM player_week_projection WHERE player_id = ?",
                    (rs, n) -> {
                        double[] values = new double[StatKey.COUNT];
                        for (StatKey key : StatKey.values()) {
                            values[key.index()] = rs.getDouble(key.json());
                        }
                        return values;
                    }, row.playerId());
            String position = db.jdbc.queryForObject("SELECT position FROM players WHERE id = ?", String.class,
                    row.playerId());
            assertThat(row.rawPoints()).as("player %d (%s)", row.playerId(), position)
                    .isEqualTo(dotProduct(ruleset, position, stored));
            assertThat(ScoringEngine.score(new StatLine(position, stored), rules))
                    .as("and the engine agrees with the oracle").isEqualTo(dotProduct(ruleset, position, stored));
        }
    }

    /** The ruleset applied by hand: rate x value in {@link StatKey} order, then each bonus that is reached. */
    private static double dotProduct(Ruleset ruleset, String position, double[] stored) {
        Map<StatKey, Double> overrides = ruleset.positionOverrides().getOrDefault(position, Map.of());
        double points = 0;
        for (StatKey key : StatKey.values()) {
            double rate = overrides.getOrDefault(key, ruleset.base().getOrDefault(key, 0.0));
            points += stored[key.index()] * rate;
        }
        for (Bonus bonus : ruleset.bonuses()) {
            if (stored[bonus.stat().index()] >= bonus.gte()) {
                points += bonus.points();
            }
        }
        return points;
    }

    /** The resolver itself, uncached: these tests change the schedule between calls. */
    private static CurrentTargetWeek noCache(ProjectionDatabase db) {
        return new CurrentTargetWeek(new TargetWeekResolver(db.jdbc), 0);
    }

    /**
     * The season is the job's: a January date belongs to the season that began the previous
     * September (week 18 is played in January), not to the calendar year. Using the year would
     * make the board ask about a season with no games while the job projects the real one.
     */
    @Test void theBoardAsksAboutTheSeasonTheJobProjectsInJanuary() {
        var db = ProjectionDatabase.get();
        db.reset();
        int home = db.team("BUF"), away = db.team("NYJ");
        Instant now = Instant.parse("2031-01-10T12:00:00Z");
        long game = db.game(2030, 18, home, away, now.plusSeconds(86400));
        var week = new SeasonWeek(2030, 18);
        var provider = new WeightedAverageProjectionProvider();
        long wr = db.player("January Receiver", "WR", home);
        double[] stats = new double[StatKey.COUNT];
        stats[StatKey.REC_YD.index()] = 80;
        new ProjectionRepository(db.jdbc).replaceOpen(week, provider.source().id(), List.of(
                new ProjectionRepository.ProjectedRow(wr, game, home, new RawProjection(provider.source(), week,
                        stats, 0, 0, 0, 4, new SeasonWeek(2030, 17)))), now);
        var service = new ProjectionsService(db.jdbc, profilesFor(1L, 7L,
                new Ruleset(1, Map.of(StatKey.REC_YD, 0.1), Map.of(), List.of())), noAvailability(week),
                new ProjectionProperties(provider.source().id(), false), provider,
                Clock.fixed(now, ZoneOffset.UTC), noCache(db), new JdbcProjectionInputs(db.jdbc), new ScoringProfileQueryRepository(db.jdbc));

        var board = service.board(1, null, "", 0, 50, 7L);
        assertThat(board.state()).as("2031-01-10 is in the 2030 season").isEqualTo("READY");
        assertThat(ids(board)).containsExactly(wr);
    }

    /**
     * The service with the cache the application runs (a 60 s time-to-live), which the HTTP tests
     * bypass so they can change the schedule between calls. When the last stats of a week arrive the
     * target moves to next week and last week's rows go STALE: with the cache, the board follows
     * that at most one time-to-live late -- still READY half a minute after, STALE a minute after --
     * and with no cache it follows at once.
     */
    @Test void theBoardFollowsAWeekRolloverOnceTheCachedAnswerExpires() {
        var db = ProjectionDatabase.get();
        db.reset();
        int home = db.team("BUF"), away = db.team("NYJ");
        Instant t0 = Instant.parse("2030-09-10T12:00:00Z");
        long played = db.game(2030, 1, home, away, t0.minus(Duration.ofHours(20)));   // over; its stats are not in
        db.game(2030, 2, home, away, t0.plus(Duration.ofDays(5)));
        long receiver = db.player("Rollover Receiver", "WR", home);
        long reporter = db.player("Reporting Receiver", "WR", away);
        var provider = new WeightedAverageProjectionProvider();
        var week1 = new SeasonWeek(2030, 1);
        double[] stats = new double[StatKey.COUNT];
        stats[StatKey.REC_YD.index()] = 80;
        new ProjectionRepository(db.jdbc).replaceOpen(week1, provider.source().id(), List.of(
                new ProjectionRepository.ProjectedRow(receiver, played, home, new RawProjection(provider.source(),
                        week1, stats, 0, 0, 0, 4, new SeasonWeek(2029, 17)))), t0);
        var cache = new CurrentTargetWeek(new TargetWeekResolver(db.jdbc), 60);
        var ruleset = new Ruleset(1, Map.of(StatKey.REC_YD, 0.1), Map.of(), List.of());
        java.util.function.BiFunction<CurrentTargetWeek, Instant, ProjectionsService> serviceAt = (targets, now) ->
                new ProjectionsService(db.jdbc, profilesFor(1L, 7L, ruleset), noAvailability(week1),
                        new ProjectionProperties(provider.source().id(), false), provider,
                        Clock.fixed(now, ZoneOffset.UTC), targets, new JdbcProjectionInputs(db.jdbc),
                        new ScoringProfileQueryRepository(db.jdbc));

        assertThat(serviceAt.apply(cache, t0).board(1, null, "", 0, 50, 7L).state()).isEqualTo("READY");

        db.played(reporter, played, away); // Monday night's last stats arrive: week 1 is finished

        assertThat(serviceAt.apply(cache, t0.plus(Duration.ofSeconds(30))).board(1, null, "", 0, 50, 7L).state())
                .as("inside the time-to-live the cached answer is still week 1").isEqualTo("READY");
        assertThat(serviceAt.apply(cache, t0.plus(Duration.ofSeconds(61))).board(1, null, "", 0, 50, 7L).state())
                .as("a minute on the cache has expired: the target is week 2 and week 1's rows are stale")
                .isEqualTo("STALE");
        assertThat(serviceAt.apply(noCache(db), t0.plus(Duration.ofSeconds(30))).board(1, null, "", 0, 50, 7L).state())
                .as("with no cache the board follows at once").isEqualTo("STALE");
    }

    /** The stored line a row carries for the preset strip is read, never changed, by whoever asks. */
    @Test void aRowsStoredLineCannotBeChangedThroughItsAccessor() {
        var row = new ProjectionRow(1L, "P1", "WR", "BUF", "NYJ", true, null, false, 0.0, 4, "ESTABLISHED",
                null, null, Instant.EPOCH, List.of(), List.of(), 2030, 1, null, null, null, 0.0, null,
                new double[] {1, 2, 3});

        row.stats()[0] = 99;

        assertThat(row.stats()).containsExactly(1, 2, 3);
    }

    // ------------------------------------------------------------------ a line's place in the week

    /**
     * A place is an ordinal in the board's own order over every ranked line of the week, and a
     * second one within the position. The Out receiver has the best total and no place at all:
     * he must not push anyone down, and he must not be given one.
     */
    @Test void aPlaceIsTheOrdinalInTheBoardsOrderOverTheRankedLinesAndTheUnrankedHaveNone() {
        var lines = List.of(placed(1, "WR", 10.0), placed(2, "QB", 20.0), placed(3, "WR", 15.0),
                placed(4, "TE", 12.0), unranked(5, "WR", 99.0));

        var byId = ProjectionsService.withRanks(lines).stream()
                .collect(Collectors.toMap(ProjectionRow::playerId, line -> line));

        assertThat(List.of(byId.get(2L).rank(), byId.get(3L).rank(), byId.get(4L).rank(), byId.get(1L).rank()))
                .as("QB 20, WR 15, TE 12, WR 10").containsExactly(1, 2, 3, 4);
        assertThat(List.of(byId.get(2L).positionRank(), byId.get(3L).positionRank(),
                byId.get(4L).positionRank(), byId.get(1L).positionRank()))
                .as("first QB, first WR, first TE, second WR").containsExactly(1, 1, 1, 2);
        assertThat(byId.get(5L).rank()).as("Out: his line is kept, he has no place").isNull();
        assertThat(byId.get(5L).positionRank()).isNull();
    }

    /** Two lines never share a place, and a tie is broken as the board breaks it: by player id. */
    @Test void aTieGetsConsecutivePlacesInPlayerIdOrderWhateverTheArrivalOrder() {
        var lines = new ArrayList<>(List.of(placed(30, "WR", 10.0), placed(10, "WR", 10.0),
                placed(20, "WR", 10.004), placed(5, "WR", 9.0)));
        for (int turn = 1; turn <= lines.size(); turn++) {
            Collections.rotate(lines, 1);
            var placedIds = ProjectionsService.withRanks(lines).stream()
                    .sorted(java.util.Comparator.comparingInt(ProjectionRow::rank)).map(ProjectionRow::playerId).toList();
            assertThat(placedIds).as("arrival order after %d rotations", turn).containsExactly(20L, 10L, 30L, 5L);
        }
    }

    /** Placing lines returns them in the order given, changed in nothing but their place. */
    @Test void placingLinesKeepsTheirOrderAndTheirLine() {
        var lines = List.of(placed(1, "WR", 10.0), placed(2, "WR", 20.0));

        var out = ProjectionsService.withRanks(lines);

        assertThat(out.stream().map(ProjectionRow::playerId).toList()).containsExactly(1L, 2L);
        assertThat(out.get(0).points()).isEqualTo(lines.get(0).points());
        assertThat(out.get(0).stats()).isEqualTo(lines.get(0).stats());
        assertThat(lines.get(0).rank()).as("the input is not modified").isNull();
    }

    private static ScoringProfiles profilesFor(long profileId, Long userId, Ruleset ruleset) {
        var profiles = mock(ScoringProfiles.class);
        when(profiles.byId(profileId, userId)).thenReturn(ResolvedRuleset.compile(ruleset));
        return profiles;
    }

    private static AvailabilityService noAvailability(SeasonWeek week) {
        var availability = mock(AvailabilityService.class);
        when(availability.forWeek(eq(week), any())).thenReturn(Map.of());
        return availability;
    }

    private static List<Long> ids(ProjectionBoard board) {
        return board.page().content().stream().map(ProjectionRow::playerId).toList();
    }

    private static ProjectionRow row(long playerId, double rawPoints) {
        return new ProjectionRow(playerId, "P" + playerId, "WR", "BUF", "NYJ", true, null, false,
                ScoringEngine.roundForDisplay(rawPoints), 4, "ESTABLISHED", null, null, Instant.EPOCH,
                List.of(), List.of(), 2030, 1, null, null, null, rawPoints, null, new double[0]);
    }

    /** A line of a given position and total that the board ranks (nothing known about him: ranked). */
    private static ProjectionRow placed(long playerId, String position, double rawPoints) {
        return withStatus(playerId, position, rawPoints, AvailabilityStatus.UNKNOWN);
    }

    /** A line the board does not rank: officially Out. */
    private static ProjectionRow unranked(long playerId, String position, double rawPoints) {
        return withStatus(playerId, position, rawPoints, AvailabilityStatus.OUT);
    }

    private static ProjectionRow withStatus(long playerId, String position, double rawPoints,
            AvailabilityStatus status) {
        var week = new SeasonWeek(2030, 1);
        return new ProjectionRow(playerId, "P" + playerId, position, "BUF", "NYJ", true, null, false,
                ScoringEngine.roundForDisplay(rawPoints), 4, "ESTABLISHED",
                new Availability(week, status, null, null, null, null, null, null, null), null, Instant.EPOCH,
                List.of(), List.of(), 2030, 1, null, null, new ProjectionRow.Usage(0, 0, 0), rawPoints, week,
                new double[] {1, 2, 3});
    }
}
