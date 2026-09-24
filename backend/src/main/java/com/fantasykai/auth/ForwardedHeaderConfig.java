package com.fantasykai.auth;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Spring's {@link ForwardedHeaderFilter}, shown only the headers Caddy writes.
 *
 * <p>{@code server.forward-headers-strategy: framework} is what makes HSTS work
 * behind a TLS-terminating proxy (see the trap in CLAUDE.md), but the filter it
 * installs honours <em>seven</em> headers and Caddy overwrites only three:
 * {@code X-Forwarded-For}, {@code -Proto} and {@code -Host}. The other four --
 * RFC 7239 {@code Forwarded}, {@code X-Forwarded-Prefix}, {@code -Port} and
 * {@code -Ssl} -- arrived from the client untouched and were believed. Two of
 * them broke the auth rate limiter through Caddy as configured for production,
 * reproduced against a local copy of that stack on 2026-09-24 (production itself
 * was not probed):
 * {@code Forwarded: for=...} minted a fresh bucket per forged address, and
 * {@code X-Forwarded-Prefix} moved the request URI outside the limiter's path.
 *
 * <p>So this is an allowlist, not a denylist: a forwarded header is believed
 * only if the proxy in front of us is known to overwrite it. Caddy strips the
 * other four as well ({@code deploy/Caddyfile}); either layer alone closes both
 * header bypasses, which is the point -- a future CDN, a second proxy, or a
 * published port turns one of them off without an error.
 *
 * <p>{@code X-Forwarded-For} stays trusted, and stays forgeable at this layer:
 * Caddy replacing it is still load-bearing. {@code AuthRateLimitTests} pins that.
 *
 * <p>Boot's own registration backs off ({@code @ConditionalOnMissingFilterBean})
 * when this bean exists; {@code AuthRateLimitTests} asserts there is exactly one.
 * Same conditions as Boot's, so switching the strategy off removes this too.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "server.forward-headers-strategy", havingValue = "framework")
class ForwardedHeaderConfig {

    /** Lower-cased. What Caddy sets or replaces when {@code trusted_proxies} is unset. */
    static final Set<String> WRITTEN_BY_PROXY =
            Set.of("x-forwarded-for", "x-forwarded-proto", "x-forwarded-host");

    /**
     * Everything Boot 3.5.16's registration does, which this one replaces --
     * including its Tomcat customizer, which reads
     * {@code server.tomcat.use-relative-redirects}. Dropping that would make the
     * property a silent no-op. Re-check this against Boot's own registration when
     * Phase 11.5 moves to Boot 4.
     */
    @Bean
    FilterRegistrationBean<ForwardedHeaderFilter> proxyForwardedHeaderFilter(ServerProperties server) {
        ProxyWrittenOnly filter = new ProxyWrittenOnly();
        filter.setRelativeRedirects(server.getTomcat().isUseRelativeRedirects());
        FilterRegistrationBean<ForwardedHeaderFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * Hides every untrusted forwarded header, then lets Spring do the rest.
     * The error dispatch reaches {@code doFilterInternal} too (Spring's
     * {@code doFilterNestedErrorDispatch} calls it), so one override covers both.
     */
    static final class ProxyWrittenOnly extends ForwardedHeaderFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            super.doFilterInternal(new UntrustedForwardedHeadersHidden(request), response, chain);
        }
    }

    private static final class UntrustedForwardedHeadersHidden extends HttpServletRequestWrapper {

        UntrustedForwardedHeadersHidden(HttpServletRequest request) {
            super(request);
        }

        static boolean untrusted(String name) {
            String header = name.toLowerCase(Locale.ROOT);
            boolean forwarding = header.equals("forwarded") || header.startsWith("x-forwarded-");
            return forwarding && !WRITTEN_BY_PROXY.contains(header);
        }

        @Override
        public String getHeader(String name) {
            return untrusted(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return untrusted(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
                    .filter(name -> !untrusted(name))
                    .toList());
        }
    }
}
