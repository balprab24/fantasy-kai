package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The request path's view of the target week: the resolver's answer, asked at most once per
 * time-to-live, and never beyond what the caller's clock allows.
 */
class CurrentTargetWeekTests {

    private static final Instant T0 = Instant.parse("2030-11-20T12:00:00Z");
    private static final Optional<TargetWeek> WEEK_11 =
            Optional.of(new TargetWeek(new SeasonWeek(2030, 11), List.of()));
    private static final Optional<TargetWeek> WEEK_12 =
            Optional.of(new TargetWeek(new SeasonWeek(2030, 12), List.of()));

    private final TargetWeekResolver resolver = mock(TargetWeekResolver.class);

    @Test
    void answersFromTheCacheInsideTheTimeToLive() {
        when(resolver.resolve(2030, T0)).thenReturn(WEEK_11);
        var targets = new CurrentTargetWeek(resolver, 60);

        assertThat(targets.resolve(2030, T0)).isEqualTo(WEEK_11);
        assertThat(targets.resolve(2030, T0.plusSeconds(59))).as("one second short of the ttl").isEqualTo(WEEK_11);

        verify(resolver, times(1)).resolve(2030, T0);
        verify(resolver, times(1)).resolve(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void asksAgainOnceTheTimeToLiveHasPassed() {
        when(resolver.resolve(2030, T0)).thenReturn(WEEK_11);
        when(resolver.resolve(2030, T0.plusSeconds(60))).thenReturn(WEEK_12);
        var targets = new CurrentTargetWeek(resolver, 60);

        assertThat(targets.resolve(2030, T0)).isEqualTo(WEEK_11);
        assertThat(targets.resolve(2030, T0.plusSeconds(60))).as("exactly the ttl later").isEqualTo(WEEK_12);
    }

    @Test
    void anotherSeasonIsAnotherQuestion() {
        when(resolver.resolve(2030, T0)).thenReturn(WEEK_11);
        when(resolver.resolve(2031, T0)).thenReturn(Optional.empty());
        var targets = new CurrentTargetWeek(resolver, 60);

        assertThat(targets.resolve(2030, T0)).isEqualTo(WEEK_11);
        assertThat(targets.resolve(2031, T0)).isEmpty();
        assertThat(targets.resolve(2030, T0)).as("and the first answer was not lost").isEqualTo(WEEK_11);
    }

    @Test
    void anAnswerFromAFutureClockIsNotReusedByAnEarlierOne() {
        when(resolver.resolve(2030, T0.plusSeconds(30))).thenReturn(WEEK_12);
        when(resolver.resolve(2030, T0)).thenReturn(WEEK_11);
        var targets = new CurrentTargetWeek(resolver, 60);

        assertThat(targets.resolve(2030, T0.plusSeconds(30))).isEqualTo(WEEK_12);
        assertThat(targets.resolve(2030, T0)).as("the clock went backwards").isEqualTo(WEEK_11);
    }

    @Test
    void anEmptyAnswerIsCachedToo() {
        when(resolver.resolve(2030, T0)).thenReturn(Optional.empty());
        var targets = new CurrentTargetWeek(resolver, 60);

        assertThat(targets.resolve(2030, T0)).isEmpty();
        assertThat(targets.resolve(2030, T0.plusSeconds(10))).isEmpty();

        verify(resolver, times(1)).resolve(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aTimeToLiveOfZeroAsksEveryTime() {
        when(resolver.resolve(2030, T0)).thenReturn(WEEK_11);
        var targets = new CurrentTargetWeek(resolver, 0);

        targets.resolve(2030, T0);
        targets.resolve(2030, T0);

        verify(resolver, times(2)).resolve(2030, T0);
    }

    @Test
    void aNegativeTimeToLiveIsAConfigurationError() {
        assertThatThrownBy(() -> new CurrentTargetWeek(resolver, -1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("target-cache-seconds");
    }
}
