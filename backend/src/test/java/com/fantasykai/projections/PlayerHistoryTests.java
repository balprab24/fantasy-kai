package com.fantasykai.projections;

import static com.fantasykai.projections.Games.receptions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A history cannot know the week it is projecting. Mutating {@link PlayerHistory#before}'s
 * strict comparison to admit the target week fails these (run, and they did).
 */
class PlayerHistoryTests {

    private static final SeasonWeek TARGET = new SeasonWeek(2025, 5);
    private static final double SENTINEL = 9999;

    private static List<HistoricalGame> past() {
        return List.of(receptions(2024, 18, 3), receptions(2025, 1, 4), receptions(2025, 2, 6),
                receptions(2025, 4, 5));
    }

    @Test
    void theTargetWeekLaterWeeksAndLaterSeasonsAreNeverHistory() {
        List<HistoricalGame> all = new ArrayList<>(past());
        all.add(receptions(2025, 5, SENTINEL));   // the week being projected
        all.add(receptions(2025, 6, SENTINEL));   // later this season
        all.add(receptions(2026, 1, SENTINEL));   // a later season
        PlayerHistory history = PlayerHistory.before(TARGET, all);
        assertThat(history.games()).extracting(HistoricalGame::when).containsExactly(
                new SeasonWeek(2024, 18), new SeasonWeek(2025, 1), new SeasonWeek(2025, 2),
                new SeasonWeek(2025, 4));
        assertThat(history.target()).isEqualTo(TARGET);
    }

    @Test
    void theFutureCannotMoveAProjection() {
        List<HistoricalGame> withFuture = new ArrayList<>(past());
        withFuture.add(receptions(2025, 5, SENTINEL));
        withFuture.add(receptions(2026, 3, SENTINEL));
        WeightedAverageProjectionProvider provider = new WeightedAverageProjectionProvider();
        RawProjection clean = provider.project(PlayerHistory.before(TARGET, past())).orElseThrow();
        RawProjection future = provider.project(PlayerHistory.before(TARGET, withFuture)).orElseThrow();
        assertThat(future.stats()).containsExactly(clean.stats());
        assertThat(future.gamesUsed()).isEqualTo(clean.gamesUsed());
    }

    @Test
    void gamesAreOrderedByWhenTheyWerePlayedWhateverOrderTheyArrive() {
        List<HistoricalGame> shuffled = List.of(receptions(2025, 4, 5), receptions(2024, 18, 3),
                receptions(2025, 2, 6), receptions(2025, 1, 4));
        assertThat(PlayerHistory.before(TARGET, shuffled).games()).containsExactlyElementsOf(past());
    }

    @Test
    void twoGamesInOneWeekAreRefused() {
        assertThatThrownBy(() -> PlayerHistory.before(TARGET,
                List.of(receptions(2025, 2, 6), receptions(2025, 2, 1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2025 week 2");
    }

    @Test
    void aStatThatCouldNotBeReadIsRefusedNotTreatedAsZero() {
        double[] stats = new double[com.fantasykai.scoring.StatKey.COUNT];
        stats[com.fantasykai.scoring.StatKey.REC_YD.index()] = Double.NaN;
        assertThatThrownBy(() -> new HistoricalGame(new SeasonWeek(2025, 3), stats, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2025 week 3");
        assertThatThrownBy(() -> new HistoricalGame(new SeasonWeek(2025, 3),
                new double[com.fantasykai.scoring.StatKey.COUNT], 0, Double.NaN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theHistoryCannotBeChangedAfterTheFact() {
        PlayerHistory history = PlayerHistory.before(TARGET, past());
        assertThatThrownBy(() -> history.games().add(receptions(2025, 3, SENTINEL)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
