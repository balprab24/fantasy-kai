package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fantasykai.scoring.StatKey;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Stored rows become exactly the history the baseline was measured on: regular season, every
 * team, crossing seasons, a played-and-recorded-nothing game as a zero line -- and nothing at or
 * after the target week.
 */
class JdbcProjectionInputsTests {

    private static final SeasonWeek TARGET = new SeasonWeek(2026, 2);

    private final ProjectionDatabase db = ProjectionDatabase.get();
    private final JdbcProjectionInputs inputs = new JdbcProjectionInputs(db.jdbc);

    private int cin;
    private int bal;

    @BeforeEach
    void seed() {
        db.reset();
        cin = db.team("CIN");
        bal = db.team("BAL");
    }

    @Test
    void aHistoryIsEveryRegularSeasonGameBeforeTheTargetOldestFirstAcrossSeasonsAndTeams() {
        long receiver = db.player("Receiver", "WR", cin);
        HistoricalGame w17 = Games.receiver(2025, 17, 6, 70, 1, 9);
        HistoricalGame w18 = Games.receiver(2025, 18, 3, 31, 0, 5);
        HistoricalGame w1 = Games.receiver(2026, 1, 8, 102, 1, 11);
        // Inserted out of order, and for two teams: the history follows the player.
        db.stats(receiver, game(2026, 1), cin, w1);
        db.stats(receiver, game(2025, 18), bal, w18);
        db.stats(receiver, game(2025, 17), bal, w17);
        db.stats(receiver, game(2026, 2), cin, Games.receiver(2026, 2, 12, 150, 2, 14)); // the target
        db.stats(receiver, game(2026, 3), cin, Games.receiver(2026, 3, 1, 9, 0, 2));     // after it

        assertThat(inputs.histories(TARGET).get(receiver).games()).containsExactly(w17, w18, w1);
    }

    /**
     * Playoff weeks are not history, and the week number cannot tell: 2020's wild card is week
     * 18. Only games.season_type can.
     */
    @Test
    void playoffGamesAreNeverHistoryEvenWhenTheirWeekLooksRegular() {
        long receiver = db.player("Receiver", "WR", cin);
        HistoricalGame regular = Games.receiver(2020, 17, 4, 40, 0, 6);
        db.stats(receiver, game(2020, 17), cin, regular);
        db.stats(receiver, db.game(2020, 18, "WC", cin, bal, null), cin, Games.receiver(2020, 18, 9, 90, 1, 12));
        db.stats(receiver, db.game(2025, 19, "WC", cin, bal, null), cin, Games.receiver(2025, 18, 9, 90, 1, 12));

        assertThat(inputs.histories(TARGET).get(receiver).games()).containsExactly(regular);
    }

    /** Ran routes, caught nothing: a real game, and the reason V7 stores appearances. */
    @Test
    void anAppearanceWithNoStatLineIsAnAllZeroGame() {
        long tightEnd = db.player("Blocking Tight End", "TE", cin);
        HistoricalGame caught = Games.receiver(2025, 17, 2, 18, 0, 3);
        db.stats(tightEnd, game(2025, 17), cin, caught);
        db.appearance(tightEnd, game(2025, 18), cin, 46);

        assertThat(inputs.histories(TARGET).get(tightEnd).games())
                .containsExactly(caught, Games.zero(2025, 18));
    }

    /**
     * Every appearance is stored, with or without a stat line; one with a stat line is that
     * line, once -- not a second game in the same week, which PlayerHistory would refuse.
     */
    @Test
    void anAppearanceThatAlsoHasAStatLineIsThatLineOnce() {
        long receiver = db.player("Receiver", "WR", cin);
        long g = game(2025, 17);
        HistoricalGame line = Games.receiver(2025, 17, 11, 264, 3, 17);
        db.stats(receiver, g, cin, line);
        db.appearance(receiver, g, cin, 77);

        assertThat(inputs.histories(TARGET).get(receiver).games()).containsExactly(line);
    }

    @Test
    void anAppearanceInThePlayoffsOrAtTheTargetIsNotHistory() {
        long tightEnd = db.player("Blocking Tight End", "TE", cin);
        db.appearance(tightEnd, db.game(2025, 19, "WC", cin, bal, null), cin, 20);
        db.appearance(tightEnd, game(2026, 2), cin, 20);

        assertThat(inputs.histories(TARGET)).doesNotContainKey(tightEnd);
    }

    @Test
    void volumeIsCarriedAlongButNeverAsAStat() {
        long quarterback = db.player("Quarterback", "QB", cin);
        double[] stats = new double[StatKey.COUNT];
        stats[StatKey.PASS_YD.index()] = 251;
        stats[StatKey.PASS_TD.index()] = 2;
        stats[StatKey.RUSH_YD.index()] = 12;
        db.stats(quarterback, game(2026, 1), cin, stats, 34, 4, 0);

        HistoricalGame only = inputs.histories(TARGET).get(quarterback).games().getFirst();
        assertThat(only.passAttempts()).isEqualTo(34);
        assertThat(only.carries()).isEqualTo(4);
        assertThat(only.targets()).isZero();
        assertThat(only.stats()).containsExactly(stats);
    }

    /** Skill is the player's position today, as on the board and in the research. */
    @Test
    void onlyQuarterbacksBacksReceiversAndTightEndsAndOnlyWithAGame() {
        long kicker = db.player("Kicker", "K", cin);
        long tackle = db.player("Tackle", "T", cin);
        long rookie = db.player("Rookie", "WR", cin);
        long receiver = db.player("Receiver", "WR", cin);
        long g = game(2026, 1);
        db.played(kicker, g, cin);
        db.appearance(tackle, g, cin, 70);
        db.played(receiver, g, cin);

        Map<Long, PlayerHistory> histories = inputs.histories(TARGET);

        assertThat(histories).containsOnlyKeys(receiver);
        assertThat(histories).doesNotContainKeys(kicker, tackle, rookie);
    }

    /**
     * Two games in one week is structural corruption, and the run stops rather than average it
     * or quietly drop the player (owner decision 2026-10-12). The failure is the diagnosis: the
     * player, the week, and each game with where it came from.
     */
    @Test
    void twoGamesInOneWeekFailNamingThePlayerTheWeekAndBothGames() {
        long receiver = db.player("Receiver", "WR", cin);
        int det = db.team("DET");
        long first = game(2026, 1);
        long second = db.game(2026, 1, det, cin, null);
        db.played(receiver, first, cin);
        db.played(receiver, second, cin);

        assertThatThrownBy(() -> inputs.histories(TARGET))
                .isInstanceOfSatisfying(CorruptHistoryException.class, e -> {
                    assertThat(e.playerId()).isEqualTo(receiver);
                    assertThat(e.week()).isEqualTo(new SeasonWeek(2026, 1));
                    assertThat(e.games()).containsExactly(
                            nflverseId(first) + " (stat row)", nflverseId(second) + " (stat row)");
                })
                .hasMessageContaining("player " + receiver + " (gsis ")
                .hasMessageContaining("2 regular-season games in 2026 week 1");
    }

    /** A stat row and an appearance in different games of one week conflict the same way. */
    @Test
    void aStatRowAndAnAppearanceInOneWeekAreNamedAsSuch() {
        long tightEnd = db.player("Tight End", "TE", cin);
        int det = db.team("DET");
        long other = db.game(2026, 1, det, cin, null);
        db.played(tightEnd, game(2026, 1), cin);
        db.appearance(tightEnd, other, cin, 12);
        long receiver = db.player("Receiver", "WR", cin);
        db.played(receiver, db.game(2025, 18, det, cin, null), cin);
        db.played(receiver, game(2025, 18), cin);

        assertThatThrownBy(() -> inputs.histories(TARGET))
                .isInstanceOf(CorruptHistoryException.class)
                .hasMessageContaining(nflverseId(other) + " (appearance, no stat row)")
                .hasMessageContaining("1 more player-week(s)");
    }

    @Test
    void anEmptyDatabaseHasNoHistories() {
        assertThat(inputs.histories(TARGET)).isEmpty();
    }

    private String nflverseId(long gameId) {
        return db.jdbc.queryForObject("SELECT nflverse_game_id FROM games WHERE id = ?", String.class, gameId);
    }

    /** A regular-season game between CIN and BAL in that week, home side alternating. */
    /**
     * The request-path read (a league's rostered players) is the job's read, filtered: the same
     * games for the players asked about -- zero-stat appearances included -- and no one else.
     */
    @Test
    void theFilteredReadIsTheFullReadForThosePlayersAndNoOneElse() {
        long receiver = db.player("Receiver", "WR", cin);
        long blocker = db.player("Blocking Tight End", "TE", cin);
        long other = db.player("Someone Else", "RB", bal);
        long week1 = game(2026, 1);
        db.stats(receiver, week1, cin, Games.receiver(2026, 1, 5, 60, 0, 7));
        db.appearance(blocker, week1, cin, 40); // ran routes, caught nothing: an all-zero game
        db.stats(other, week1, bal, Games.receiver(2026, 1, 2, 20, 0, 3));

        var full = inputs.histories(TARGET);
        var filtered = inputs.histories(TARGET, List.of(receiver, blocker));

        assertThat(filtered.keySet()).containsExactlyInAnyOrder(receiver, blocker);
        assertThat(filtered.get(receiver).games()).isEqualTo(full.get(receiver).games());
        assertThat(filtered.get(blocker).games()).isEqualTo(full.get(blocker).games()).hasSize(1);
        assertThat(inputs.histories(TARGET, List.of())).isEmpty();
    }

    private long game(int season, int week) {
        boolean cinHome = week % 2 == 0;
        return db.game(season, week, cinHome ? cin : bal, cinHome ? bal : cin,
                Instant.parse("2020-01-01T00:00:00Z"));
    }
}
