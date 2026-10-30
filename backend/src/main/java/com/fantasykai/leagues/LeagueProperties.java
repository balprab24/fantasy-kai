package com.fantasykai.leagues;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * League import configuration; defaults live in application.yml.
 *
 * @param sleeperBaseUrl        Sleeper API root; its host must still be on {@link LeagueHttp}'s allowlist
 * @param sleeperCallsPerMinute every member's Sleeper calls together. Sleeper's limit is per IP
 *                              (1,000/min, plan A1), so all members spend one budget
 * @param syncCooldown          the least time between two syncs of one league
 * @param timeout               per outbound request
 * @param maxBodyBytes          a larger response is refused, not truncated
 * @param limitsEnabled         the Redis buckets; off only in tests that run without Redis
 */
@ConfigurationProperties(prefix = "fantasykai.leagues")
public record LeagueProperties(
        String sleeperBaseUrl,
        int sleeperCallsPerMinute,
        Duration syncCooldown,
        Duration timeout,
        int maxBodyBytes,
        boolean limitsEnabled) {}
