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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Two buckets per IP on {@code /api/v1/auth/**}. Handoff §8.
 *
 * <p><strong>Strict, 5/min</strong>: login, register, and every other path under
 * {@code /auth} -- including ones that do not exist yet, so a new endpoint there
 * is limited before anyone thinks about it. This is the surface where a request
 * is worth repeating: password guessing, email enumeration, and hammering
 * Argon2 (which is expensive by design, so an unlimited login endpoint is a CPU
 * exhaustion primitive pointed at yourself). Login and register share the
 * bucket on purpose, so guessing and enumerating spend one count between them.
 *
 * <p><strong>Loose, 30/min</strong>: refresh and logout, named explicitly and
 * nothing else. Until 2026-09-29 they shared the strict bucket, and every full
 * page load of the web app spends a refresh -- so a member's own page loads
 * spent the attempts signing in needs, and a 429 was hit while testing. A
 * refresh verifies a random 256-bit token by hash lookup: no Argon2, and nothing
 * to guess, so the looser limit gives an attacker nothing the strict one denied.
 * The read endpoints stay unthrottled.
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

    /**
     * Matched the way routing matches -- decoded segments, context path removed
     * -- never with {@code getRequestURI().startsWith(...)}. The raw URI is
     * still percent-encoded and carries any forwarded prefix, so
     * {@code /api/v1/%61uth/login} and {@code X-Forwarded-Prefix: /x} both
     * reached login with this filter skipped. Reproduced 2026-09-24 through Caddy
     * as configured for production, on a local copy of that stack;
     * {@code AuthRateLimitTests} pins both.
     */
    private static final RequestMatcher AUTH =
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/**");

    /**
     * The loose bucket's whole membership. An allowlist, so that anything not
     * named here -- a wrong method, a new endpoint, a typo -- falls to the strict
     * bucket. Matched the same decoded way as {@link #AUTH}, so
     * {@code /api/v1/%61uth/refresh} is a refresh and {@code /api/v1/%61uth/login}
     * is still a login.
     */
    private static final RequestMatcher SESSION = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/refresh"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/logout"));

    private final ObjectProvider<ProxyManager<byte[]>> buckets;
    private final Supplier<BucketConfiguration> strict;
    private final Supplier<BucketConfiguration> session;

    public AuthRateLimitFilter(ObjectProvider<ProxyManager<byte[]>> buckets, AuthProperties props) {
        this.buckets = buckets;
        this.strict = perMinute(props.loginAttemptsPerMinute());
        this.session = perMinute(props.sessionRequestsPerMinute());
    }

    private static Supplier<BucketConfiguration> perMinute(int requests) {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requests)
                        // Refill the whole bucket at once rather than trickling
                        // one token every 12s: "5 a minute" should mean five
                        // tries then a wait, not a slow drip that never quite
                        // locks anyone out.
                        .refillIntervally(requests, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AUTH.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        boolean sessionCall = SESSION.matches(request);
        // The strict key is unchanged from the single-bucket days, so a deploy
        // does not hand anyone a fresh strict bucket.
        byte[] key = ((sessionCall ? "rl:auth:session:" : "rl:auth:") + clientIp(request))
                .getBytes(StandardCharsets.UTF_8);
        boolean allowed;
        try {
            allowed = buckets.getObject().builder()
                    .build(key, sessionCall ? session : strict)
                    .tryConsume(1);
        } catch (RuntimeException e) {
            logger.error("rate limiter unavailable, refusing " + request.getRequestURI(), e);
            Problems.write(response, HttpStatus.SERVICE_UNAVAILABLE, "Rate limiter unavailable",
                    "authentication is temporarily unavailable");
            return;
        }

        if (!allowed) {
            response.setHeader("Retry-After", "60");
            Problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                    sessionCall ? "too many session requests; try again in a minute"
                            : "too many authentication attempts; try again in a minute");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Behind Caddy the socket address is the proxy, so the client comes from the
     * forwarded headers.
     *
     * <p>While {@code forward-headers-strategy: framework} is set, the header
     * branch below is dead: Spring's filter has already removed the header and
     * rewritten {@code getRemoteAddr()} from it. Either way the value is
     * client-controlled and trusted only because Caddy overwrites
     * {@code X-Forwarded-For}; every other forwarded header is refused before it
     * gets here ({@link ForwardedHeaderConfig}).
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
