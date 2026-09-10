package com.fantasykai.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer} into the security context.
 *
 * <p>A request with no token passes straight through unauthenticated. That is
 * not a hole: the chain decides what unauthenticated may reach, and the read
 * endpoints are deliberately public (north-star §5b). Rejecting here instead
 * would make every public endpoint require a token.
 *
 * <p>A request with a <em>bad</em> token is different, and is rejected rather
 * than downgraded to anonymous. Silently ignoring an expired token would serve
 * a logged-out-looking page to someone who believes they are logged in, and
 * would hide the moment a refresh is due.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        long userId;
        try {
            userId = jwt.verify(header.substring("Bearer ".length()));
        } catch (InvalidTokenException e) {
            // Hand it to the entry point, which renders problem+json. Throwing
            // from a filter would escape @RestControllerAdvice and produce an
            // HTML error page.
            SecurityContextHolder.clearContext();
            request.setAttribute(ProblemAuthenticationEntryPoint.REASON, e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            new ProblemAuthenticationEntryPoint().commence(request, response, null);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                userId, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
