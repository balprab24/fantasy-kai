package com.fantasykai.auth;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The filter chain, and it is <strong>default-deny</strong>. north-star §2.
 *
 * <p>{@code permitAll} on an explicit short list, {@code authenticated()} on
 * everything else — so a new endpoint is private until someone deliberately
 * opens it. The inverse, denying a list and allowing the rest, fails open every
 * time someone adds a controller and forgets.
 *
 * <p><strong>The reads need an account, since 2026-09-29</strong> (owner
 * decision, north-star §2). Until then they were {@code permitAll} -- the
 * website had gone members-only a day earlier, but the numbers behind the
 * sign-in were one {@code curl} away, and each unthrottled {@code /rankings}
 * call scores a whole season on a free VM whose ceiling is ~256 req/s.
 *
 * <p><em>The chain gates endpoints and the query gates rows</em>, and both still
 * do their job: the chain now turns an anonymous caller away, and the ownership
 * filter in {@code ScoringProfiles.byId} still decides which profiles a member
 * may score against. The filter was the only gate for three weeks; it stays,
 * because a chain rule is one line away from being reopened.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthProperties props;

    public SecurityConfig(AuthProperties props) {
        this.props = props;
    }

    /**
     * Servlet-only, and the annotation is load-bearing rather than defensive.
     *
     * <p>{@code HttpSecurity} exists only in a servlet context, but this
     * application has a second entrypoint that has no servlet context at all:
     * scripts/ingest-once.sh runs the daily pull with
     * {@code --spring.main.web-application-type=none}. Without this condition
     * the bean is still demanded there, and the one-shot job dies at startup
     * with "Parameter 0 of method filterChain required a bean of type
     * HttpSecurity" -- which is what happened for three days after Phase 5a,
     * invisibly, because every test in the suite boots a web application.
     *
     * <p>{@code @EnableMethodSecurity} deliberately stays on the class: method
     * security needs no servlet environment, and gating it would quietly turn
     * off every {@code @PreAuthorize} in the headless run.
     *
     * <p>{@link com.fantasykai.ingest.OneShotContextTests} is the test that
     * would have caught it.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter,
            AuthRateLimitFilter rateLimitFilter) throws Exception {
        return http
                // No cookie-backed session, so there is no session for a
                // cross-site form post to ride. The refresh cookie is SameSite=Strict
                // and is only ever read by /auth/refresh, which is why CSRF can go.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfiguration()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(auth -> auth
                        // The public list, in full. Every entry is a deliberate
                        // decision; adding one is not a formality.
                        // Signing in, and the health probes. /api/v1/public/**
                        // was here too, with nothing under it: a prefix that
                        // opens whatever is added beneath it later.
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Everything else, including every mutation.
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new ProblemAuthenticationEntryPoint())
                        .accessDeniedHandler(new ProblemAccessDeniedHandler()))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Argon2id, not BCrypt. Handoff §8.
     *
     * <p>These are Spring Security 6's own defaults spelled out rather than
     * inherited, because they are the parameters an interviewer asks about and
     * "whatever the framework picked" is not an answer: 16-byte salt, 32-byte
     * hash, 1 lane, 16 MiB of memory, 3 passes. The memory cost is the point —
     * it is what makes a GPU array bad at this, and it is the parameter BCrypt
     * does not have.
     *
     * <p>Delegates to BouncyCastle, which the starter does not pull in. The pom
     * declares it explicitly; without it this throws on the first login rather
     * than at startup.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 1 << 14, 3);
    }

    /**
     * An explicit origin allowlist, never {@code *}. Handoff §8.
     *
     * <p>{@code allowCredentials} is on because the refresh token is an
     * {@code HttpOnly} cookie, and a browser will not send it cross-origin
     * otherwise — which is also why {@code *} is not merely discouraged here
     * but rejected outright by the CORS spec in combination with credentials.
     */
    private CorsConfigurationSource corsConfiguration() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
