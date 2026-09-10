package com.fantasykai.auth;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * The Redis-backed bucket store for {@link AuthRateLimitFilter}.
 *
 * <p>Lettuce directly rather than through Spring's {@code RedisTemplate},
 * because Bucket4j needs the compare-and-swap primitives that template does not
 * expose. The starter is still on the classpath for its properties and for
 * Phase 11's ruleset cache.
 */
@Configuration
class RateLimitConfig {

    /**
     * Buckets expire an hour after their last write. Without a TTL this store
     * grows one key per IP forever, which is a slow leak that only shows up
     * once the app has been public for a while.
     */
    private static final Duration BUCKET_TTL = Duration.ofHours(1);

    /**
     * Lazy, and both beans are, so the application context does not require a
     * reachable Redis to start. Only a request to {@code /api/v1/auth/**}
     * resolves them; everything else -- the whole read API, the ingest, the
     * health endpoints -- comes up without Redis.
     *
     * <p>That is deliberate rather than convenient. It means a Redis outage
     * degrades exactly one surface instead of taking the site down, and it
     * keeps a Redis container out of the five integration test classes that
     * have nothing to do with rate limiting.
     */
    @Bean(destroyMethod = "shutdown")
    @Lazy
    RedisClient redisClient(RedisProperties redis) {
        return RedisClient.create("redis://%s:%d".formatted(redis.getHost(), redis.getPort()));
    }

    /**
     * Keys are {@code byte[]}: {@code builderFor(RedisClient)} is typed that way
     * and the builder exposes no key mapper, so the caller encodes its own key.
     * See {@link AuthRateLimitFilter}.
     */
    @Bean
    @Lazy
    ProxyManager<byte[]> buckets(RedisClient client) {
        return LettuceBasedProxyManager.builderFor(client)
                .withExpirationStrategy(
                        io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(BUCKET_TTL))
                .build();
    }
}
