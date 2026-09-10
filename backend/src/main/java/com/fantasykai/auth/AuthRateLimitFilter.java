package com.fantasykai.auth;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 5 requests per minute per IP across {@code /api/v1/auth/**}. Handoff §8.
 *
 * <p>Tight here and nowhere else, because this is the only surface where a
 * request is worth repeating: password guessing, email enumeration, and
 * hammering Argon2 (which is expensive by design, so an unlimited login
 * endpoint is a CPU exhaustion primitive pointed at yourself). The read
 * endpoints stay unthrottled.
 *
 * <p>Backed by Redis rather than a local map so the limit is per user rather
 * than per instance. Redis has been running in {@code docker-compose.yml} since
 * Phase 0 and used by nothing; this is the first thing to claim it.
 *
 * <p><strong>Fails closed.</strong> If Redis is unreachable the request is
 * refused with a 503 rather than waved through: a rate limiter that opens
 * under load is one an attacker can switch off by causing load. That makes
 * Redis a hard dependency of {@code /auth} and of nothing else -- the bucket
 * store is resolved lazily, on the first authentication request, so a Redis
 * outage costs you logins and not the site. north-star §5d.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String PATH = "/api/v1/auth/";

    private final ObjectProvider<ProxyManager<byte[]>> buckets;
    private final Supplier<BucketConfiguration> configuration;

    public AuthRateLimitFilter(ObjectProvider<ProxyManager<byte[]>> buckets, AuthProperties props) {
        this.buckets = buckets;
        this.configuration = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(props.loginAttemptsPerMinute())
                        // Refill the whole bucket at once rather than trickling
                        // one token every 12s: "5 a minute" should mean five
                        // tries then a wait, not a slow drip that never quite
                        // locks anyone out.
                        .refillIntervally(props.loginAttemptsPerMinute(), Duration.ofMinutes(1))
                        .build())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        byte[] key = ("rl:auth:" + clientIp(request)).getBytes(StandardCharsets.UTF_8);
        boolean allowed;
        try {
            allowed = buckets.getObject().builder().build(key, configuration).tryConsume(1);
        } catch (RuntimeException e) {
            logger.error("rate limiter unavailable, refusing " + request.getRequestURI(), e);
            Problems.write(response, HttpStatus.SERVICE_UNAVAILABLE, "Rate limiter unavailable",
                    "authentication is temporarily unavailable");
            return;
        }

        if (!allowed) {
            response.setHeader("Retry-After", "60");
            Problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                    "too many authentication attempts; try again in a minute");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Behind Fly and Vercel the socket address is a proxy, so the client is the
     * first hop in X-Forwarded-For.
     *
     * <p>That header is client-controlled and therefore forgeable, which would
     * let an attacker mint a fresh bucket per request. It is trusted here only
     * because the deployment terminates TLS at a proxy that overwrites it. On a
     * host that does not, this must go back to {@code getRemoteAddr}.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }
}
