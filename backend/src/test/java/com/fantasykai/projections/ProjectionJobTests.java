package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fantasykai.ingest.DailyPull;
import com.fantasykai.ingest.IngestProperties;
import com.fantasykai.ingest.IngestRunRecorder;
import com.fantasykai.ingest.IngestService;
import com.fantasykai.scoring.StatKey;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The job across week 5 of 2026: what it writes, what it locks at kickoff, and what it never
 * touches again.
 *
 * <p>Weeks 1-4 are played and stored. Week 5 is CIN-BAL on Thursday night, KC-DET on Sunday
 * and BUF-TB on Monday night; MIA is on a bye. Each history is built by hand, written to the
 * database, and the job's rows are held to the provider run on that same hand-built history
 * -- bit for bit.
 */
class ProjectionJobTests {

    private static final SeasonWeek WEEK_5 = new SeasonWeek(2026, 5);

    /** Week 5, in UTC: Thursday 8:15 PM, Sunday 1:00 PM and Monday 8:15 PM Eastern. */
    private static final Instant THURSDAY_KICKOFF = Instant.parse("2026-10-09T00:15:00Z");
    private static final Instant SUNDAY_KICKOFF = Instant.parse("2026-10-11T17:00:00Z");
    private static final Instant MONDAY_KICKOFF = Instant.parse("2026-10-13T00:15:00Z");

    /** The 06:00 ET runs around them. */
    private static final Instant WEDNESDAY = Instant.parse("2026-10-07T10:00:00Z");
    private static final Instant THURSDAY = Instant.parse("2026-10-08T10:00:00Z");
    private static final Instant FRIDAY = Instant.parse("2026-10-09T10:00:00Z");
    private static final Instant SATURDAY = Instant.parse("2026-10-10T10:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-10-13T10:00:00Z");

    private final ProjectionDatabase db = ProjectionDatabase.get();
    private final WeightedAverageProjectionProvider provider = new WeightedAverageProjectionProvider();

    private int cin, bal, kc, det, buf, tb, mia, nyj;
    private long chase, flowers, kelce, rookie, blocker, evans, waddle, kicker;
    private long thursdayGame, sundayGame, mondayGame;
    /** Each player's history as built by hand, exactly as stored. */
    private final Map<Long, List<HistoricalGame>> history = new HashMap<>();
    private final Map<Long, Integer> team = new HashMap<>();

    @BeforeEach
    void seed() {
        db.reset();
        history.clear();
        cin = db.team("CIN");
        bal = db.team("BAL");
        kc = db.team("KC");
        det = db.team("DET");
        buf = db.team("BUF");
        tb = db.team("TB");
        mia = db.team("MIA");
        nyj = db.team("NYJ");

        chase = player("Chase", "WR", cin);
        flowers = player("Flowers", "WR", bal);
        kelce = player("Kelce", "TE", kc);
        rookie = player("Rookie", "WR", det);       // no NFL game yet
        blocker = player("Blocker", "TE", buf);     // two catches, then two games of blocking
        evans = player("Evans", "WR", tb);
        waddle = player("Waddle", "WR", mia);       // MIA has no game in week 5
        kicker = player("Kicker", "K", cin);        // not a skill position

        for (int week = 1; week <= 4; week++) {
            Instant sunday = Instant.parse("2026-09-13T17:00:00Z").plusSeconds((week - 1) * 7L * 86400);
            long cinBal = db.game(2026, week, cin, bal, sunday);
            long kcDet = db.game(2026, week, kc, det, sunday);
            long bufTb = db.game(2026, week, buf, tb, sunday);
            long miaNyj = db.game(2026, week, mia, nyj, sunday);
            stats(chase, cinBal, Games.receiver(2026, week, 5 + week, 60 + 9 * week, week % 2, 8 + week));
            stats(flowers, cinBal, Games.receiver(2026, week, 7 - week, 81 - 7 * week, 0, 9));
            stats(kelce, kcDet, Games.receiver(2026, week, 4 + week % 3, 43 + week, week == 3 ? 2 : 0, 6));
            stats(evans, bufTb, Games.receiver(2026, week, 3 + week, 55 + 13 * week, week / 2, 7 + week));
            stats(waddle, miaNyj, Games.receiver(2026, week, 6, 70, 1, 8));
            db.played(kicker, cinBal, cin);
            if (week <= 2) {
                stats(blocker, bufTb, Games.receiver(2026, week, 2, 18, 0, 3));
            } else {
                db.appearance(blocker, bufTb, buf, 40);
                history.computeIfAbsent(blocker, id -> new ArrayList<>()).add(Games.zero(2026, week));
            }
        }
        thursdayGame = db.game(2026, 5, cin, bal, THURSDAY_KICKOFF);
        sundayGame = db.game(2026, 5, kc, det, SUNDAY_KICKOFF);
        mondayGame = db.game(2026, 5, buf, tb, MONDAY_KICKOFF);
    }

    @Test
    void writesEveryCandidateWithAHistoryBitIdenticalToTheProvider() {
        ProjectionRun run = jobAt(WEDNESDAY).run(2026);

        assertThat(run.week()).contains(WEEK_5);
        assertThat(run.candidates()).as("the skill players of the six teams that play").isEqualTo(6);
        assertThat(run.written()).isEqualTo(5);
        assertThat(run.locked()).isZero();

        Map<Long, Map<String, Object>> rows = rows();
        assertThat(rows).containsOnlyKeys(chase, flowers, kelce, blocker, evans);
        for (long player : rows.keySet()) {
            assertMatchesTheProvider(rows.get(player), player);
            assertThat(rows.get(player)).containsEntry("generated_at", Timestamp.from(WEDNESDAY))
                    .containsEntry("locked_at", null)
                    .containsEntry("team_id", team.get(player))
                    .containsEntry("data_through_season", 2026)
                    .containsEntry("data_through_week", 4)
                    .containsEntry("games_used", 4);
        }
        assertThat(rows.get(chase)).containsEntry("game_id", thursdayGame);
        assertThat(rows.get(kelce)).containsEntry("game_id", sundayGame);
        assertThat(rows.get(evans)).containsEntry("game_id", mondayGame);
    }

    /** Two blocking games are two real games: the projection is pulled toward zero by them. */
    @Test
    void zeroStatAppearancesCountAsGames() {
        jobAt(WEDNESDAY).run(2026);

        Map<String, Object> row = rows().get(blocker);
        assertMatchesTheProvider(row, blocker);
        assertThat((Double) row.get("rec")).as("2 catches in each of the older two of four games")
                .isLessThan(2.0).isGreaterThan(0.0);
    }

    @Test
    void aByeARookieAndAKickerGetNoRow() {
        jobAt(WEDNESDAY).run(2026);

        assertThat(rows()).doesNotContainKeys(waddle, rookie, kicker);
    }

    /**
     * Friday: Thursday night has kicked off. Its rows are locked as they stood at kickoff -- even
     * though a stat correction since would change them -- while Sunday's are regenerated from it.
     */
    @Test
    void thursdayNightLocksAtKickoffWhileSundayIsStillRegenerated() {
        jobAt(WEDNESDAY).run(2026);
        Map<Long, Map<String, Object>> wednesday = rows();
        correctWeekFour(chase, Games.receiver(2026, 4, 1, 4, 0, 2));
        correctWeekFour(kelce, Games.receiver(2026, 4, 12, 140, 2, 13));

        ProjectionRun friday = jobAt(FRIDAY).run(2026);

        assertThat(friday.locked()).as("Chase and Flowers").isEqualTo(2);
        Map<Long, Map<String, Object>> rows = rows();
        for (long thursdayNight : List.of(chase, flowers)) {
            Map<String, Object> expected = new HashMap<>(wednesday.get(thursdayNight));
            expected.put("locked_at", Timestamp.from(THURSDAY_KICKOFF));
            assertThat(rows.get(thursdayNight)).isEqualTo(expected);
        }
        assertMatchesTheProvider(rows.get(kelce), kelce);
        assertThat(rows.get(kelce)).containsEntry("generated_at", Timestamp.from(FRIDAY))
                .containsEntry("locked_at", null)
                .isNotEqualTo(wednesday.get(kelce));
    }

    /** "Kicked off" includes the instant of kickoff, so a run at that second cannot rewrite it. */
    @Test
    void aRowLocksAtTheExactSecondOfKickoff() {
        jobAt(WEDNESDAY).run(2026);

        jobAt(THURSDAY_KICKOFF).run(2026);

        assertThat(rows().get(chase)).containsEntry("locked_at", Timestamp.from(THURSDAY_KICKOFF));
    }

    /**
     * A player traded after his old team has played keeps that row and gets no second one --
     * the record says what was projected for the game he was expected to play.
     */
    @Test
    void aLockedRowSurvivesARerunEvenAfterItsPlayerIsTradedToASundayTeam() {
        jobAt(WEDNESDAY).run(2026);
        jobAt(FRIDAY).run(2026);
        Map<String, Object> locked = rows().get(chase);
        db.moveTo(chase, kc);

        ProjectionRun saturday = jobAt(SATURDAY).run(2026);

        assertThat(rows().get(chase)).isEqualTo(locked).containsEntry("game_id", thursdayGame);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM player_week_projection WHERE player_id = ?",
                Long.class, chase)).isOne();
        assertThat(saturday.written()).as("Kelce, Blocker, Evans; Chase is a candidate but keeps his row")
                .isEqualTo(3);
    }

    @Test
    void aPlayerWhoLeavesTheWeekLosesHisOpenRow() {
        jobAt(WEDNESDAY).run(2026);
        db.moveTo(kelce, mia);     // a team on a bye
        db.moveTo(evans, null);    // released

        jobAt(THURSDAY).run(2026);

        assertThat(rows()).doesNotContainKeys(kelce, evans).containsKeys(chase, flowers, blocker);
    }

    @Test
    void twoRunsAtTheSameInstantWriteTheSameRows() {
        jobAt(WEDNESDAY).run(2026);
        Map<Long, Map<String, Object>> first = rows();

        jobAt(WEDNESDAY).run(2026);

        assertThat(rows()).isEqualTo(first);
    }

    /**
     * Tuesday, every week-5 game in and no week 6 scheduled: nothing to project. The sweep still
     * locks what kicked off since the last run.
     */
    @Test
    void withNoWeekLeftItWritesNothingButStillLocks() {
        jobAt(WEDNESDAY).run(2026);
        db.played(chase, thursdayGame, cin);
        db.played(kelce, sundayGame, kc);
        db.played(evans, mondayGame, tb);

        ProjectionRun run = jobAt(TUESDAY).run(2026);

        assertThat(run.week()).isEmpty();
        assertThat(run.written()).isZero();
        assertThat(run.locked()).isEqualTo(5);
        assertThat(rows()).hasSize(5).allSatisfy((player, row) -> assertThat(row.get("locked_at")).isNotNull());
    }

    /**
     * One transaction: a failure part-way leaves the previous run's rows exactly as they were,
     * and does not even keep the locks the failed run took.
     */
    @Test
    void aFailedRunLeavesThePreviousRowsUntouched() {
        jobAt(WEDNESDAY).run(2026);
        Map<Long, Map<String, Object>> before = rows();
        // A second week-4 game for Chase: a corrupt history, which the job refuses to average.
        db.played(chase, db.game(2026, 4, cin, nyj, Instant.parse("2026-10-04T17:00:00Z")), cin);

        assertThatThrownBy(() -> jobAt(FRIDAY).run(2026))
                .isInstanceOf(CorruptHistoryException.class)
                .hasMessageContaining("player " + chase)
                .hasMessageContaining("2026 week 4");

        assertThat(rows()).isEqualTo(before);
    }

    /**
     * Fail loudly, and say exactly what: the daily pull's {@code ingest_runs} row -- what
     * {@code ingestFreshness} shows -- names the player, the week and both games.
     */
    @Test
    void aCorruptHistoryIsReportedByPlayerWeekAndGamesNotAsAGenericFailure() {
        long secondGame = db.game(2026, 4, cin, nyj, Instant.parse("2026-10-04T17:00:00Z"));
        db.played(chase, secondGame, cin);
        String second = db.jdbc.queryForObject("SELECT nflverse_game_id FROM games WHERE id = ?",
                String.class, secondGame);
        String first = db.jdbc.queryForObject("SELECT nflverse_game_id FROM games WHERE season = 2026"
                + " AND week = 4 AND home_team_id = ? AND away_team_id = ?", String.class, cin, bal);
        DailyPull pull = new DailyPull(mock(IngestService.class), jobAt(FRIDAY),
                new ProjectionProperties("weighted-average-v1", true), new IngestRunRecorder(db.jdbc),
                new IngestProperties("https://example.invalid", "https://example.invalid", 2020,
                        "0 0 6 * * *", false, false, false),
                Clock.fixed(FRIDAY, ZoneOffset.UTC));

        assertThatThrownBy(pull::run).isInstanceOf(CorruptHistoryException.class);

        String error = db.jdbc.queryForObject(
                "SELECT error FROM ingest_runs WHERE source = 'fantasykai.projections' AND status = 'FAILED'",
                String.class);
        assertThat(error).contains("player " + chase, "2026 week 4", first + " (stat row)",
                second + " (stat row)");
    }

    // --- the schedule changes under the job (games.delisted_at, V8) -----------------------

    /** Cancelled before kickoff: its open rows go, and nothing is projected for it again. */
    @Test
    void aGameCancelledBeforeKickoffHasItsOpenRowsWithdrawn() {
        jobAt(WEDNESDAY).run(2026);
        db.delist(sundayGame);

        ProjectionRun thursday = jobAt(THURSDAY).run(2026);

        assertThat(thursday.withdrawn()).as("Kelce's KC-DET row").isEqualTo(1);
        assertThat(rows()).doesNotContainKey(kelce).containsKeys(chase, flowers, blocker, evans);
        assertThat(thursday.written()).as("KC and DET have no game this week now").isEqualTo(4);
    }

    /**
     * Cancelled after it kicked off -- 2022's BUF-CIN, listed for three days and then dropped:
     * the rows locked at kickoff are the record of what was projected, and stay.
     */
    @Test
    void aGameCancelledAfterKickoffKeepsItsLockedRows() {
        jobAt(WEDNESDAY).run(2026);
        jobAt(FRIDAY).run(2026);
        Map<String, Object> locked = rows().get(chase);
        db.delist(thursdayGame);

        ProjectionRun saturday = jobAt(SATURDAY).run(2026);

        assertThat(saturday.withdrawn()).isZero();
        assertThat(rows().get(chase)).isEqualTo(locked);
        assertThat(rows().get(flowers)).containsEntry("locked_at", Timestamp.from(THURSDAY_KICKOFF));
    }

    /**
     * Dropped from the schedule before the job saw it kick off: withdrawn, never locked. The
     * order matters -- locking first would make a game that never happened part of the record.
     */
    @Test
    void aDroppedGameWhoseOldKickoffPassedBetweenRunsIsWithdrawnNotLocked() {
        jobAt(WEDNESDAY).run(2026);
        db.delist(sundayGame);

        ProjectionRun monday = jobAt(Instant.parse("2026-10-12T10:00:00Z")).run(2026);

        assertThat(monday.withdrawn()).isEqualTo(1);
        assertThat(rows()).doesNotContainKey(kelce);
    }

    /**
     * Moved within its week -- 2020's NE-KC, Sunday to Monday: the same game, a later kickoff.
     * The rows stay open past the old time and are regenerated, then lock at the new one.
     */
    @Test
    void aGameMovedLaterInItsWeekStaysOpenUntilItsNewKickoff() {
        jobAt(WEDNESDAY).run(2026);
        db.jdbc.update("UPDATE games SET kickoff_at = ? WHERE id = ?",
                MONDAY_KICKOFF.atOffset(ZoneOffset.UTC), thursdayGame);

        jobAt(FRIDAY).run(2026);

        assertThat(rows().get(chase)).containsEntry("locked_at", null)
                .containsEntry("generated_at", Timestamp.from(FRIDAY));
        jobAt(TUESDAY).run(2026);
        assertThat(rows().get(chase)).containsEntry("locked_at", Timestamp.from(MONDAY_KICKOFF));
    }

    /**
     * Moved to another week -- 2020's PIT-TEN, week 4 to week 7: nflverse drops the old id and
     * adds a new one. Week 5 withdraws the old game's rows; the new week projects the new game.
     */
    @Test
    void aGameMovedToAnotherWeekIsProjectedThereAndWithdrawnHere() {
        jobAt(WEDNESDAY).run(2026);
        db.delist(thursdayGame);
        long moved = db.game(2026, 6, cin, bal, Instant.parse("2026-10-18T17:00:00Z"));

        ProjectionRun thursday = jobAt(THURSDAY).run(2026);
        assertThat(thursday.withdrawn()).as("Chase and Flowers").isEqualTo(2);
        assertThat(rows()).doesNotContainKeys(chase, flowers);

        db.played(kelce, sundayGame, kc);
        db.played(evans, mondayGame, tb);
        ProjectionRun next = jobAt(TUESDAY).run(2026);

        assertThat(next.week()).as("the dropped game no longer holds week 5").contains(new SeasonWeek(2026, 6));
        assertThat(db.jdbc.queryForObject("SELECT game_id FROM player_week_projection"
                + " WHERE player_id = ? AND season = 2026 AND week = 6", Long.class, chase)).isEqualTo(moved);
    }

    /** A game with no kickoff time cannot be locked at a time nobody knows. */
    @Test
    void aGameWithNoKickoffTimeStaysOpen() {
        db.jdbc.update("UPDATE games SET kickoff_at = NULL WHERE id = ?", mondayGame);
        jobAt(WEDNESDAY).run(2026);

        jobAt(Instant.parse("2026-10-20T10:00:00Z")).run(2026);

        assertThat(rows().get(evans)).containsEntry("locked_at", null)
                .containsEntry("generated_at", Timestamp.from(Instant.parse("2026-10-20T10:00:00Z")));
    }

    /** The job replaces its own provider's rows; a second provider writing in shadow is not its. */
    @Test
    void anotherProvidersRowsAreNotTouched() {
        db.jdbc.update("""
                INSERT INTO player_week_projection (season, week, provider, player_id, game_id, team_id,
                    pass_yd, pass_td, pass_int, pass_2pt, rush_yd, rush_td, rush_2pt, rec, rec_yd,
                    rec_td, rec_2pt, fum_lost, ret_td, pass_att, rush_att, targets, games_used,
                    data_through_season, data_through_week, generated_at)
                VALUES (2026, 5, 'shadow-model-v1', ?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 3, 30, 0, 0, 0, 0,
                        0, 0, 5, 4, 2026, 4, now())
                """, kelce, sundayGame, kc);

        jobAt(WEDNESDAY).run(2026);

        assertThat(db.jdbc.queryForObject(
                "SELECT count(*) FROM player_week_projection WHERE provider = 'shadow-model-v1'", Long.class))
                .isOne();
    }

    // --- helpers -------------------------------------------------------------------------

    private ProjectionJob jobAt(Instant now) {
        return new ProjectionJob(db.jdbc, db.transaction, new TargetWeekResolver(db.jdbc),
                new JdbcProjectionInputs(db.jdbc), new ProjectionRepository(db.jdbc), provider,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private long player(String name, String position, int teamId) {
        long id = db.player(name, position, teamId);
        team.put(id, teamId);
        return id;
    }

    private void stats(long player, long game, HistoricalGame line) {
        db.stats(player, game, team.get(player), line);
        history.computeIfAbsent(player, id -> new ArrayList<>()).add(line);
    }

    /** A stat correction: nflverse revises a line after the fact, and the daily ingest upserts it. */
    private void correctWeekFour(long player, HistoricalGame line) {
        db.jdbc.update("DELETE FROM player_game_stats WHERE player_id = ? AND week = 4", player);
        long game = db.jdbc.queryForObject("""
                SELECT id FROM games WHERE season = 2026 AND week = 4
                   AND (home_team_id = ? OR away_team_id = ?)
                """, Long.class, team.get(player), team.get(player));
        history.get(player).removeIf(g -> g.when().week() == 4);
        stats(player, game, line);
    }

    /** The row's 13 stats and 3 volumes equal the provider's output on the hand-built history. */
    private void assertMatchesTheProvider(Map<String, Object> row, long player) {
        RawProjection expected = provider.project(PlayerHistory.before(WEEK_5, history.get(player)))
                .orElseThrow();
        for (StatKey stat : StatKey.values()) {
            assertThat(Double.doubleToLongBits((Double) row.get(stat.json()))).as(stat.json())
                    .isEqualTo(Double.doubleToLongBits(expected.get(stat)));
        }
        assertThat((Double) row.get("pass_att")).isEqualTo(expected.passAttempts());
        assertThat((Double) row.get("rush_att")).isEqualTo(expected.carries());
        assertThat((Double) row.get("targets")).isEqualTo(expected.targets());
        assertThat(row).containsEntry("games_used", expected.gamesUsed());
    }

    /** Week 5's rows by player. */
    private Map<Long, Map<String, Object>> rows() {
        Map<Long, Map<String, Object>> rows = new HashMap<>();
        db.jdbc.queryForList("""
                SELECT * FROM player_week_projection
                 WHERE season = 2026 AND week = 5 AND provider = 'weighted-average-v1'
                """).forEach(row -> rows.put(((Number) row.get("player_id")).longValue(), row));
        return rows;
    }
}
