package com.fantasykai.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Two buckets per <em>account</em> on the member API: reads ({@code GET}) and
 * everything else, each refilled once a minute. Keyed on the JWT subject, not
 * the client address, so no forwarded header can mint a fresh bucket -- the
 * {@code AuthRateLimitFilter} lesson -- and one member's traffic never spends
 * another's.
 *
 * <p>Runs after {@code JwtAuthFilter}, so the principal is known. An anonymous
 * request is not counted here: the chain refuses it anyway. {@code /auth/**}
 * keeps its own per-IP buckets and is skipped, and anything outside
 * {@code /api/v1/} (health) is never limited, so a platform probe cannot be
 * throttled into a restart.
 *
 * <p><strong>Fails closed</strong>, like the auth limiter: with Redis
 * unreachable a member request is a 503, not a free pass. That makes Redis a
 * dependency of every member read as well as of sign-in -- a real operational
 * cost, recorded in the 2026-10-28 handoff.
 *
 * <p>Built by {@code SecurityConfig}, not a {@code @Component}: a component
 * filter is also registered with the servlet container, where it would run
 * before authentication and see no member at all.
 */
public class MemberRateLimitFilter extends OncePerRequestFilter {

    private final ObjectProvider<ProxyManager<byte[]>> buckets;
    private final int readsPerMinute;
    private final int writesPerMinute;
    private final boolean enabled;

    public MemberRateLimitFilter(ObjectProvider<ProxyManager<byte[]>> buckets,
            int readsPerMinute, int writesPerMinute, boolean enabled) {
        this.buckets = buckets;
        this.readsPerMinute = readsPerMinute;
        this.writesPerMinute = writesPerMinute;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Long member = memberOf(SecurityContextHolder.getContext().getAuthentication());
        if (!enabled || member == null || !limited(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        boolean read = "GET".equals(request.getMethod());
        int perMinute = read ? readsPerMinute : writesPerMinute;
        byte[] key = ("rl:member:" + (read ? "read:" : "write:") + member)
                .getBytes(StandardCharsets.UTF_8);
        boolean allowed;
        try {
            allowed = buckets.getObject().builder()
                    .build(key, () -> perMinute(perMinute))
                    .tryConsume(1);
        } catch (RuntimeException e) {
            Problems.write(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Rate limiter unavailable", "Please try again shortly.");
            return;
        }
        if (!allowed) {
            response.setHeader("Retry-After", "60");
            Problems.write(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests", "Please wait a minute before trying again.");
            return;
        }
        chain.doFilter(request, response);
    }

    /** The member's id, or null for an anonymous or non-JWT principal. */
    private static Long memberOf(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof Long id ? id : null;
    }

    /** The servlet path is decoded, so {@code %61uth} is {@code auth} here, as it is to routing. */
    private static boolean limited(String path) {
        return path.startsWith("/api/v1/") && !path.startsWith("/api/v1/auth/");
    }

    private static BucketConfiguration perMinute(int requests) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requests)
                        .refillIntervally(requests, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
