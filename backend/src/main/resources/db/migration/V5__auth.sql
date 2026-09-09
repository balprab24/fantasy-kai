-- V5: refresh tokens, and the two indexes per-user profiles need (Phase 5).
--
-- users and scoring_profiles.user_id already exist -- V1 shipped both and
-- nothing has ever touched them. So this migration is smaller than the Phase 5
-- brief first assumed: one table plus two indexes, not a schema.
--
-- The section 9 baseline invariant is scoped to player_game_stats and nothing
-- here touches it. scoring_profiles is not frozen.

-- Rotating refresh tokens, handoff section 8.
--
-- Opaque and random, never a JWT: a refresh token has to be revocable, and a
-- self-contained token cannot be revoked without a denylist that is this table
-- with extra steps.
--
-- Stored as a SHA-256 hash, not the value. The threat this defends is a
-- database read: an attacker with a dump of this table holds 90 days of live
-- sessions if the values are plaintext, and holds nothing if they are hashed.
-- Argon2 is deliberately NOT used here -- it is the right choice for a password
-- because a password is low-entropy and guessable, and the wrong choice for a
-- 256-bit random value, which is not. A fast hash over full entropy is not
-- brute-forcible, and this runs on every token refresh.
--
-- family_id is what makes rotation detect theft. Every refresh consumes one row
-- and issues another with the same family_id. Replaying a consumed token means
-- two parties hold the same value, which means one of them stole it -- and
-- since we cannot tell which, the whole family is revoked and both are logged
-- out. That is the correct trade: a false positive costs one re-login, a false
-- negative costs the account.
CREATE TABLE refresh_tokens (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    -- SHA-256 hex. CHAR(64) rather than TEXT so a truncated or mis-encoded
    -- value fails the insert instead of silently never matching on lookup.
    token_hash  CHAR(64)    NOT NULL,
    family_id   UUID        NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    -- Both NULL means live. consumed_at is set by a normal rotation;
    -- revoked_at is set for every row in a family when a replay is detected.
    -- They are separate columns because "used once, correctly" and "killed
    -- because someone stole a sibling" are different facts, and collapsing them
    -- would make the replay path unauditable.
    consumed_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    -- The lookup is by hash and it must find at most one row.
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

-- Revoking a family is the hot write on the theft path; without this it is a
-- seq scan of every token ever issued, performed at exactly the moment you
-- least want to be slow.
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

-- Logout-everywhere and expiry cleanup both sweep by user.
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);


-- scoring_profiles carried nothing but its primary key, because until now every
-- read was "WHERE user_id IS NULL" over four seeded rows. Phase 5 makes the
-- profile list a per-user query on every page load.
CREATE INDEX idx_scoring_profiles_user ON scoring_profiles (user_id);

-- Nothing stopped a user creating two profiles called "My League", which makes
-- the profile switcher ambiguous in the one place the user cannot disambiguate.
-- Partial, because presets legitimately share user_id IS NULL and are keyed by
-- name themselves; a plain UNIQUE would treat those NULLs as distinct anyway
-- and constrain nothing, which is the uq_games_matchup trap from V1.
CREATE UNIQUE INDEX uq_scoring_profiles_user_name
    ON scoring_profiles (user_id, name) WHERE user_id IS NOT NULL;
