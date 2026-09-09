package com.fantasykai.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the 15-minute access token. Handoff §8.
 *
 * <p>HS256 rather than RS256 because both ends of this token are the same
 * process: there is no authorization server, no third party verifying it, and
 * nothing to publish a JWK set to. Asymmetric signing solves a distribution
 * problem this system does not have.
 *
 * <p>HS256 is pinned rather than inferred. {@code Keys.hmacShaKeyFor} picks the
 * algorithm from the key's length, so a 48-byte secret silently produces HS384
 * and a 64-byte one HS512 -- which means the algorithm in production depends on
 * how long a string somebody pasted into an environment variable, and the
 * documented "HS256" quietly stops being true. Pinning it makes the token
 * identical everywhere and keeps §8 honest.
 *
 * <p>The token carries the user id as its subject and nothing else. Not the
 * email, not a role, not a display name: every claim in a JWT is a claim you
 * have promised to keep valid for 15 minutes after the database says otherwise.
 * The id is the one fact that cannot go stale.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final AuthProperties props;
    private final Clock clock;

    public JwtService(AuthProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                props.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(long userId) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTokenTtl())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * The user id in a valid, unexpired token.
     *
     * @throws InvalidTokenException for every rejection -- wrong signature,
     *     expired, malformed, or a subject that is not a number. One exception
     *     type on purpose: a caller that could tell "expired" from "forged"
     *     would leak whether a token was ever real, and every one of them is a
     *     401 to the client anyway.
     */
    public long verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(Instant.now(clock)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("access token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("access token is not valid");
        }
    }
}
