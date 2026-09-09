package com.fantasykai.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotating refresh tokens with reuse detection. Handoff §8.
 *
 * <p>Three properties, and the third is the one that matters:
 *
 * <ol>
 *   <li>The token is opaque and random, never a JWT, so it can be revoked.</li>
 *   <li>Only its hash is stored, so a database dump is not a set of live
 *       sessions.</li>
 *   <li>Every use consumes the token and issues a new one in the same family.
 *       Presenting an already-consumed token therefore means two parties hold
 *       it, which means one of them stole it -- and since there is no way to
 *       tell which, the entire family is revoked.</li>
 * </ol>
 *
 * <p>That last step is what turns a stolen refresh token from a permanent
 * silent compromise into one extra session that ends the moment the real user
 * comes back. It costs a legitimate user a re-login in the rare race; the
 * alternative costs them the account.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 256 bits, per §8. Not guessable, so a fast hash over it is not brute-forcible. */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final AuthProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, AuthProperties props, Clock clock) {
        this.tokens = tokens;
        this.props = props;
        this.clock = clock;
    }

    /** A brand new family: this is a login, not a rotation. */
    public String issueNewFamily(long userId) {
        return issue(userId, UUID.randomUUID());
    }

    /**
     * Consumes one refresh token and issues its successor.
     *
     * @throws InvalidTokenException if the token is unknown, expired, already
     *     revoked, or being replayed. The caller turns all four into 401 --
     *     telling them apart would confirm to an attacker that a token was
     *     once real.
     */
    /**
     * {@code noRollbackFor} is load-bearing and was found by a test rather than
     * by reading this.
     *
     * <p>The replay path <em>revokes the family and then throws</em>. Under a
     * plain {@code @Transactional} the throw rolls the revocation back, so
     * detecting the theft leaves no trace: the stolen token is refused once,
     * every sibling stays live, and the mechanism this whole class exists for
     * silently does nothing. The write is the point of the failure, not
     * collateral damage from it.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public Rotation rotate(String presented) {
        RefreshTokenRepository.Stored stored = tokens.findByHash(hash(presented))
                .orElseThrow(() -> new InvalidTokenException("refresh token is not valid"));

        if (!stored.notRevoked()) {
            throw new InvalidTokenException("refresh token has been revoked");
        }
        if (!stored.live()) {
            // Already consumed. Someone is holding a copy.
            int killed = tokens.revokeFamily(stored.familyId());
            log.warn("refresh token replay for user {}: revoked {} tokens in family {}",
                    stored.userId(), killed, stored.familyId());
            throw new InvalidTokenException("refresh token has already been used");
        }
        if (stored.expiresAt().isBefore(Instant.now(clock))) {
            throw new InvalidTokenException("refresh token has expired");
        }
        if (!tokens.consume(stored.id())) {
            // Lost the race with a concurrent refresh holding the same value.
            // Indistinguishable from theft, so treated as theft.
            tokens.revokeFamily(stored.familyId());
            throw new InvalidTokenException("refresh token has already been used");
        }

        return new Rotation(stored.userId(), issue(stored.userId(), stored.familyId()));
    }

    /** Logout. Kills the whole family, so every device on this login is out. */
    @Transactional
    public void revoke(String presented) {
        tokens.findByHash(hash(presented))
                .ifPresent(stored -> tokens.revokeFamily(stored.familyId()));
    }

    private String issue(long userId, UUID familyId) {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.insert(userId, hash(token), familyId, Instant.now(clock).plus(props.refreshTokenTtl()));
        return token;
    }

    /**
     * SHA-256, not Argon2, and the difference is the input rather than the use.
     * Argon2 is slow on purpose because a password is low-entropy and guessable.
     * This value is 256 random bits: there is nothing to guess, so the only job
     * left is one-wayness, and this runs on every refresh.
     */
    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    public record Rotation(long userId, String refreshToken) {}
}
