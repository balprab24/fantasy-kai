package com.fantasykai.auth;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The {@code refresh_tokens} table from V5. */
@Repository
public class RefreshTokenRepository {

    private final JdbcTemplate jdbc;

    public RefreshTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long userId, String tokenHash, UUID familyId, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at)
                VALUES (?, ?, ?, ?)
                """, userId, tokenHash, familyId, Timestamp.from(expiresAt));
    }

    /**
     * Looks a token up by hash, whatever state it is in.
     *
     * <p>Deliberately not filtered to live rows. The consumed and revoked ones
     * are exactly what the replay check needs to see -- a query that returned
     * only usable tokens would make a stolen token indistinguishable from a
     * made-up one, and the family would never be revoked.
     */
    public Optional<Stored> findByHash(String tokenHash) {
        return jdbc.query("""
                        SELECT id, user_id, family_id, expires_at, consumed_at, revoked_at
                          FROM refresh_tokens WHERE token_hash = ?
                        """,
                        (rs, n) -> new Stored(
                                rs.getLong("id"),
                                rs.getLong("user_id"),
                                UUID.fromString(rs.getString("family_id")),
                                rs.getTimestamp("expires_at").toInstant(),
                                rs.getTimestamp("consumed_at") == null,
                                rs.getTimestamp("revoked_at") == null),
                        tokenHash)
                .stream().findFirst();
    }

    /**
     * Marks one token used, and returns whether this call is the one that did it.
     *
     * <p>The {@code consumed_at IS NULL} predicate makes rotation atomic: two
     * concurrent refreshes with the same token both reach here, and exactly one
     * updates a row. The loser gets 0 and is treated as a replay, which is the
     * correct reading -- a token is a one-use value and the second holder cannot
     * be distinguished from a thief.
     */
    public boolean consume(long tokenId) {
        return jdbc.update("""
                UPDATE refresh_tokens SET consumed_at = now()
                 WHERE id = ? AND consumed_at IS NULL
                """, tokenId) == 1;
    }

    /**
     * Whether a consumed token may be exchanged once more: it was consumed
     * within {@code grace}, and no token in its family has been consumed since
     * -- the owner has not moved on, so a second presentation is a parallel tab
     * or an aborted reload rather than a stolen copy being used later.
     *
     * <p>Both comparisons run on the database's clock, the clock that stamped
     * {@code consumed_at} in {@link #consume}. Mixing in the JVM's would widen
     * or shut the window by however far the two clocks have drifted.
     */
    public boolean reusableWithinGrace(long tokenId, Duration grace) {
        return jdbc.query("""
                        SELECT t.consumed_at >= now() - make_interval(secs => ?)
                               AND NOT EXISTS (SELECT 1 FROM refresh_tokens s
                                                WHERE s.family_id = t.family_id
                                                  AND s.consumed_at > t.consumed_at)
                          FROM refresh_tokens t
                         WHERE t.id = ? AND t.consumed_at IS NOT NULL AND t.revoked_at IS NULL
                        """,
                        (rs, n) -> rs.getBoolean(1),
                        grace.toMillis() / 1000.0, tokenId)
                .stream().findFirst().orElse(false);
    }

    /** The theft response: kill every token descended from the same login. */
    public int revokeFamily(UUID familyId) {
        return jdbc.update("""
                UPDATE refresh_tokens SET revoked_at = now()
                 WHERE family_id = ? AND revoked_at IS NULL
                """, familyId);
    }

    /**
     * @param live      no consumed_at
     * @param notRevoked no revoked_at
     */
    public record Stored(long id, long userId, UUID familyId, Instant expiresAt,
            boolean live, boolean notRevoked) {}
}
