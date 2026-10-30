-- V11: a member's imported fantasy leagues (league plan S1).
--
-- One copy per member, never shared. Twelve members of one Sleeper league hold
-- twelve independent copies: that is the "fetched as the user, never aggregated"
-- invariant, and it makes a cross-member read structurally impossible rather
-- than something every query has to remember. Every table carries user_id, and
-- each child reaches its league through a composite (id, user_id) key, so a row
-- cannot claim one member while pointing at another member's league.
--
-- No fantasy points anywhere. The platforms report points-for and matchup
-- scores; storing them would be the fantasy_points mistake again, correct for
-- one ruleset. Ours are computed on read.

CREATE TABLE league_connections (
    id                    BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id               BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform              VARCHAR(16) NOT NULL CHECK (platform IN ('sleeper', 'espn', 'yahoo')),
    -- The platform's stable account id (Sleeper user_id), never a username:
    -- Sleeper usernames can change. NOT NULL, because a NULL here would let
    -- the UNIQUE below admit duplicates (docs/development.md trap).
    external_user_id      VARCHAR(64) NOT NULL CHECK (length(trim(external_user_id)) > 0),
    -- S4/S5 only (ESPN opt-in cookies, Yahoo refresh token), AES-256-GCM.
    credential_ciphertext BYTEA,
    credential_nonce      BYTEA,
    key_version           SMALLINT,
    status                VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ERROR')),
    last_error            TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_league_connections_account UNIQUE (user_id, platform, external_user_id),
    CONSTRAINT uq_league_connections_owner UNIQUE (id, user_id),
    -- A credential is all three columns or none of them.
    CONSTRAINT ck_league_connections_credential CHECK (
        (credential_ciphertext IS NULL) = (credential_nonce IS NULL)
        AND (credential_nonce IS NULL) = (key_version IS NULL)),
    -- Sleeper is public and read-only: nothing secret exists to store.
    CONSTRAINT ck_league_connections_sleeper_holds_nothing CHECK (
        platform <> 'sleeper' OR credential_ciphertext IS NULL)
);

CREATE TABLE leagues (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    connection_id       BIGINT       NOT NULL,
    platform            VARCHAR(16)  NOT NULL CHECK (platform IN ('sleeper', 'espn', 'yahoo')),
    external_league_id  VARCHAR(64)  NOT NULL CHECK (length(trim(external_league_id)) > 0),
    season              SMALLINT     NOT NULL CHECK (season BETWEEN 2020 AND 2100),
    name                VARCHAR(128) NOT NULL,
    num_teams           SMALLINT     NOT NULL CHECK (num_teams BETWEEN 2 AND 32),
    league_type         VARCHAR(16)  NOT NULL
                        CHECK (league_type IN ('REDRAFT', 'KEEPER', 'DYNASTY', 'UNKNOWN')),
    -- The auto-created profile. SET NULL: a member may delete it from the
    -- scoring page, and the league must survive that; the next sync recreates it.
    scoring_profile_id  BIGINT       REFERENCES scoring_profiles (id) ON DELETE SET NULL,
    -- Why no profile exists, when the league's scoring did not validate.
    -- RulesetValidator's rule is "rejections are loud": never clamp.
    scoring_note        TEXT,
    -- Starting slots as RosterSlot counts, e.g. {"QB":1,"RB":2,"FLEX_RWT":1}.
    roster_slots        JSONB        NOT NULL,
    playoff_teams       SMALLINT,
    playoff_week_start  SMALLINT,
    trade_deadline      SMALLINT,
    my_team_external_id VARCHAR(64),
    -- Scoring and slots the league uses that Fantasy Kai does not model, shown
    -- to the member, never silently dropped: {"key": value}.
    unmodeled_scoring   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    synced_at           TIMESTAMPTZ  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_leagues_connection FOREIGN KEY (connection_id, user_id)
        REFERENCES league_connections (id, user_id) ON DELETE CASCADE,
    CONSTRAINT uq_leagues_import UNIQUE (user_id, platform, external_league_id, season),
    CONSTRAINT uq_leagues_owner UNIQUE (id, user_id)
);

CREATE INDEX idx_leagues_user ON leagues (user_id);

-- Standings only: wins, losses and ties are results, not points.
CREATE TABLE league_teams (
    league_id        BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    external_team_id VARCHAR(64)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    manager_name     VARCHAR(128),
    wins             SMALLINT     NOT NULL DEFAULT 0,
    losses           SMALLINT     NOT NULL DEFAULT 0,
    ties             SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (league_id, external_team_id),
    CONSTRAINT fk_league_teams_league FOREIGN KEY (league_id, user_id)
        REFERENCES leagues (id, user_id) ON DELETE CASCADE
);

-- The current snapshot, replaced in one transaction per sync (V9's pattern).
CREATE TABLE league_rosters (
    league_id          BIGINT      NOT NULL,
    user_id            BIGINT      NOT NULL,
    external_team_id   VARCHAR(64) NOT NULL,
    external_player_id VARCHAR(64) NOT NULL,
    -- NULL is unmatched, and is shown to the member as such.
    player_id          BIGINT      REFERENCES players (id) ON DELETE SET NULL,
    -- Where the player sits: a RosterSlot name for a starter, or BENCH / IR / TAXI.
    slot               VARCHAR(16) NOT NULL,
    PRIMARY KEY (league_id, external_player_id),
    CONSTRAINT fk_league_rosters_league FOREIGN KEY (league_id, user_id)
        REFERENCES leagues (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_league_rosters_team FOREIGN KEY (league_id, external_team_id)
        REFERENCES league_teams (league_id, external_team_id) ON DELETE CASCADE
);

-- Who plays whom. No scores: the schedule is the fact, the points are ours.
CREATE TABLE league_matchups (
    league_id                 BIGINT      NOT NULL,
    user_id                   BIGINT      NOT NULL,
    week                      SMALLINT    NOT NULL CHECK (week BETWEEN 1 AND 22),
    external_team_id          VARCHAR(64) NOT NULL,
    opponent_external_team_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (league_id, week, external_team_id),
    CONSTRAINT fk_league_matchups_league FOREIGN KEY (league_id, user_id)
        REFERENCES leagues (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_league_matchups_not_self CHECK (external_team_id <> opponent_external_team_id)
);
