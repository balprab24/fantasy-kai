package com.fantasykai.auth;

import com.fantasykai.auth.AuthDtos.LoginRequest;
import com.fantasykai.auth.AuthDtos.RegisterRequest;
import com.fantasykai.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Register, login, refresh, logout. Handoff §7.
 *
 * <p>The access token comes back in the body and the refresh token in an
 * {@code HttpOnly} cookie. That asymmetry is the design: an XSS that reads
 * {@code localStorage} gets 15 minutes, not a permanent session, and the token
 * that <em>would</em> be permanent is the one script cannot touch.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final AuthProperties props;

    AuthController(UserRepository users, PasswordEncoder passwords, JwtService jwt,
            RefreshTokenService refreshTokens, AuthProperties props) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.props = props;
    }

    @PostMapping("/register")
    ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        long userId = users.create(request.email(), passwords.encode(request.password()));
        return issue(userId, HttpStatus.CREATED);
    }

    /**
     * One failure message for both "no such email" and "wrong password", and the
     * password is verified even when the email is unknown.
     *
     * <p>Both halves matter. Different messages let anyone enumerate who has an
     * account; skipping the Argon2 verify on an unknown email leaks the same
     * thing through response time, since a real account costs ~100ms more than
     * a fake one. Doing the work anyway makes the two indistinguishable.
     */
    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        var credentials = users.findByEmail(request.email());
        boolean valid = credentials
                .map(found -> passwords.matches(request.password(), found.passwordHash()))
                .orElseGet(() -> {
                    passwords.matches(request.password(), DUMMY_HASH);
                    return false;
                });

        if (!valid) {
            throw new InvalidTokenException("email or password is not correct");
        }
        return issue(credentials.orElseThrow().userId(), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String presented) {
        if (presented == null) {
            throw new InvalidTokenException("no refresh token");
        }
        RefreshTokenService.Rotation rotated = refreshTokens.rotate(presented);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(rotated.refreshToken(), props.refreshTokenTtl()).toString())
                .body(accessToken(rotated.userId()));
    }

    /** Always 204, even for a token that was already dead. Logout is idempotent. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String presented) {
        if (presented != null) {
            refreshTokens.revoke(presented);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<TokenResponse> issue(long userId, HttpStatus status) {
        String refresh = refreshTokens.issueNewFamily(userId);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(refresh, props.refreshTokenTtl()).toString())
                .body(accessToken(userId));
    }

    private TokenResponse accessToken(long userId) {
        return TokenResponse.bearer(jwt.issue(userId), props.accessTokenTtl().toSeconds());
    }

    /**
     * {@code SameSite=Strict} rather than {@code Lax}: the cookie is only ever
     * needed on a fetch this app makes to its own API, never on a top-level
     * navigation, so there is no flow that Strict breaks and it removes
     * cross-site sends entirely.
     *
     * <p>{@code Path=/api/v1/auth} keeps it off every other request. A refresh
     * token attached to a rankings call is a refresh token in more logs and
     * more proxies than it needs to be.
     */
    private static ResponseCookie cookie(String value, Duration ttl) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(ttl)
                .build();
    }

    /**
     * A real Argon2 hash of a value nobody knows, so the unknown-email path
     * pays the same CPU as the known-email one. Generated once at class load.
     */
    private static final String DUMMY_HASH = new org.springframework.security.crypto.argon2
            .Argon2PasswordEncoder(16, 32, 1, 1 << 14, 3)
            .encode(java.util.UUID.randomUUID().toString());
}
