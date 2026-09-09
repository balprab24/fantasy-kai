package com.fantasykai.auth;

import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The users table, which V1 created and nothing has touched until now.
 *
 * <p>{@code JdbcTemplate} rather than an entity, deliberately: north-star §5a.
 * Every read path in the codebase is already a hand-written query, and two
 * persistence idioms for two small tables is worse than one.
 *
 * <p>{@code email} is {@code CITEXT}, so uniqueness and lookup are already
 * case-insensitive in the database. Do not add {@code LOWER()} here; it would
 * defeat the index and duplicate a guarantee the column type already makes.
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @throws EmailAlreadyRegisteredException on a duplicate, caught from the
     *     unique constraint rather than pre-checked with a SELECT. A check-then-act
     *     races: two simultaneous registrations both see "free" and one gets an
     *     unhandled 500. The constraint is the only thing that actually decides.
     */
    public long create(String email, String passwordHash) {
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO users (email, password_hash) VALUES (?, ?) RETURNING id
                    """, Long.class, email, passwordHash);
            if (id == null) {
                throw new IllegalStateException("insert returned no id for " + email);
            }
            return id;
        } catch (DuplicateKeyException e) {
            throw new EmailAlreadyRegisteredException("that email is already registered");
        }
    }

    public Optional<Credentials> findByEmail(String email) {
        return jdbc.query("SELECT id, password_hash FROM users WHERE email = ?",
                        (rs, n) -> new Credentials(rs.getLong("id"), rs.getString("password_hash")),
                        email)
                .stream().findFirst();
    }

    public boolean exists(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE id = ?)", Boolean.class, userId));
    }

    public record Credentials(long userId, String passwordHash) {}
}
