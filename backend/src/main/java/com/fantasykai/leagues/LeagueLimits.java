package com.fantasykai.leagues;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The two Redis buckets league import spends, beside {@code MemberRateLimitFilter}'s.
 *
 * <ul>
 *   <li><strong>Outbound, per platform, shared by every member.</strong> Sleeper
 *       limits by IP, and every member's import leaves from our one address: a
 *       per-member budget would let a busy hour get the whole site blocked.
 *   <li><strong>Sync, per league.</strong> One sync per league per cooldown, so
 *       "Sync now" cannot be used to spend the shared budget.
 * </ul>
 *
 * <p>Fails closed, like the member limiter: with Redis unreachable no call
 * leaves, because an unmetered outbound path is how an IP gets blocked.
 */
@Component
public class LeagueLimits {

    private final ObjectProvider<ProxyManager<byte[]>> buckets;
    private final LeagueProperties props;

    public LeagueLimits(ObjectProvider<ProxyManager<byte[]>> buckets, LeagueProperties props) {
        this.buckets = buckets;
        this.props = props;
    }

    /**
     * Spends one call of Sleeper's shared budget, or refuses. ESPN and Yahoo get
     * their own buckets with their slices (S4, S5), sized from their own limits.
     */
    public void sleeperCall() {
        if (!props.limitsEnabled()) {
            return;
        }
        if (!tryConsume("rl:outbound:sleeper", props.sleeperCallsPerMinute(), Duration.ofMinutes(1))) {
            throw new LeagueUnavailableException(
                    "Too many league imports at once. Please try again in a minute.");
        }
    }

    /** Claims this league's sync slot, or refuses until the cooldown has passed. */
    public void sync(long leagueId) {
        if (!props.limitsEnabled()) {
            return;
        }
        if (!tryConsume("rl:league-sync:" + leagueId, 1, props.syncCooldown())) {
            throw new LeagueSyncTooSoonException(props.syncCooldown());
        }
    }

    private boolean tryConsume(String key, int capacity, Duration period) {
        try {
            return buckets.getObject().builder()
                    .build(key.getBytes(StandardCharsets.UTF_8), () -> BucketConfiguration.builder()
                            .addLimit(Bandwidth.builder()
                                    .capacity(capacity)
                                    .refillIntervally(capacity, period)
                                    .build())
                            .build())
                    .tryConsume(1);
        } catch (RuntimeException e) {
            throw new LeagueUnavailableException("League sync is unavailable right now.", e);
        }
    }
}
