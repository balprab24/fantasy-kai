package com.fantasykai.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth configuration; defaults live in application.yml.
 *
 * @param jwtSecret        HS256 signing key, from the environment and never from
 *                         application.yml. Handoff §8
 * @param accessTokenTtl   15 minutes. Short because an access token cannot be
 *                         revoked; the refresh token is the revocable half
 * @param refreshTokenTtl  how long a session survives without a login
 * @param loginAttemptsPerMinute  per IP, across the whole {@code /auth} surface
 * @param allowedOrigins   CORS allowlist. Never {@code *}
 */
@ConfigurationProperties(prefix = "fantasykai.auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        int loginAttemptsPerMinute,
        java.util.List<String> allowedOrigins) {

    /**
     * HS256 needs at least 256 bits of key, and jjwt refuses a shorter one at
     * signing time rather than at startup. Failing here instead turns "login
     * returns 500 in production" into "the app does not start", which is the
     * failure you want for a misconfigured secret.
     */
    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "fantasykai.auth.jwt-secret must be at least 32 bytes; set JWT_SECRET in the environment");
        }
    }
}
