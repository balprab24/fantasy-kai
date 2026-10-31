package com.fantasykai.leagues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.redis.testcontainers.RedisContainer;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The shared Sleeper budget and the sync cooldown against a real Redis -- and
 * without a Spring context, which would cost another teardown
 * (docs/development.md) to test two buckets.
 */
@Testcontainers
class LeagueLimitsTests {

    @Container
    static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

    private static RedisClient client;
    private static ProxyManager<byte[]> buckets;

    @BeforeAll
    static void connect() {
        client = RedisClient.create("redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(6379)));
        buckets = LettuceBasedProxyManager.builderFor(client)
                .withExpirationStrategy(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1)))
                .build();
    }

    @AfterAll
    static void disconnect() {
        client.shutdown();
    }

    @SuppressWarnings("unchecked")
    private static LeagueLimits limits(ProxyManager<byte[]> store, int perMinute, boolean enabled) {
        ObjectProvider<ProxyManager<byte[]>> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(store);
        return new LeagueLimits(provider, new LeagueProperties("https://api.sleeper.app", perMinute,
                Duration.ofMinutes(5), Duration.ofSeconds(10), 2_097_152, enabled));
    }

    /** Every member's calls draw on one bucket: Sleeper counts by our IP, not by member. */
    @Test
    void theSleeperBudgetIsSharedAndRefusesWhenSpent() {
        flush();
        LeagueLimits first = limits(buckets, 3, true);
        LeagueLimits second = limits(buckets, 3, true); // another request, another member

        first.sleeperCall();
        second.sleeperCall();
        first.sleeperCall();

        assertThatThrownBy(second::sleeperCall).isInstanceOf(LeagueUnavailableException.class);
    }

    @Test
    void aLeagueSyncsOncePerCooldown_andOtherLeaguesAreUnaffected() {
        flush();
        LeagueLimits limits = limits(buckets, 600, true);

        limits.sync(41);
        assertThatThrownBy(() -> limits.sync(41)).isInstanceOf(LeagueSyncTooSoonException.class)
                .satisfies(e -> assertThat(((LeagueSyncTooSoonException) e).cooldown())
                        .isEqualTo(Duration.ofMinutes(5)));
        assertThatCode(() -> limits.sync(42)).doesNotThrowAnyException();
    }

    /** As the member limiter does: with Redis gone nothing leaves, rather than everything. */
    @Test
    @SuppressWarnings("unchecked")
    void withRedisUnreachableItFailsClosed() {
        ProxyManager<byte[]> unreachable = mock(ProxyManager.class);
        when(unreachable.builder()).thenThrow(new RedisConnectionException("Connection refused"));
        LeagueLimits limits = limits(unreachable, 600, true);

        assertThatThrownBy(limits::sleeperCall).isInstanceOf(LeagueUnavailableException.class);
        assertThatThrownBy(() -> limits.sync(7)).isInstanceOf(LeagueUnavailableException.class);
    }

    @Test
    void switchedOffItSpendsNothing() {
        LeagueLimits off = limits(null, 1, false);

        assertThatCode(() -> {
            for (int i = 0; i < 10; i++) {
                off.sleeperCall();
                off.sync(1);
            }
        }).doesNotThrowAnyException();
    }

    private static void flush() {
        try (var connection = client.connect()) {
            connection.sync().flushall();
        }
    }
}
