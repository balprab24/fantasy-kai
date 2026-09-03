-- V1: initial schema (handoff doc section 5).
--
-- DELIBERATELY UNINDEXED beyond primary keys and the uniqueness constraints
-- the model requires. Section 9 Step 1 says to build the rankings path slow on
-- purpose so the Phase 6 optimization has a real, measured baseline to beat.
-- The composite index, the partial index and the player_season_agg matview all
-- arrive as later migrations, after docs/perf/baseline.md is captured.
--
-- Do not add indexes here.

CREATE EXTENSION IF NOT EXISTS citext;


CREATE TABLE teams (
    id         INT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    abbr       VARCHAR(4)  NOT NULL,   -- 'DET'
    name       VARCHAR(64) NOT NULL,
    conference VARCHAR(4),
    division   VARCHAR(16),
    CONSTRAINT uq_teams_abbr UNIQUE (abbr)
);


CREATE TABLE players (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gsis_id      VARCHAR(16),           -- nflverse canonical, '00-0036322'
    external_ids JSONB       NOT NULL DEFAULT '{}'::jsonb,  -- {"sleeper":"6794","espn":"4262921"}
    full_name    VARCHAR(96) NOT NULL,
    position     VARCHAR(4)  NOT NULL,
    team_id      INT,                   -- nullable: free agents
    status       VARCHAR(16),           -- ACT / INA / IR / PS
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_players_gsis_id UNIQUE (gsis_id),
    CONSTRAINT fk_players_team FOREIGN KEY (team_id) REFERENCES teams (id)
);


CREATE TABLE games (
    id           BIGINT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    season       SMALLINT  NOT NULL,
    week         SMALLINT  NOT NULL,
    season_type  VARCHAR(8),            -- REG / POST
    -- NOT NULL is load-bearing: UNIQUE treats NULLs as distinct, so nullable
    -- team ids would let uq_games_matchup admit duplicate games.
    home_team_id INT       NOT NULL,
    away_team_id INT       NOT NULL,
    kickoff_at   TIMESTAMPTZ,
    CONSTRAINT uq_games_matchup UNIQUE (season, week, home_team_id, away_team_id),
    CONSTRAINT fk_games_home_team FOREIGN KEY (home_team_id) REFERENCES teams (id),
    CONSTRAINT fk_games_away_team FOREIGN KEY (away_team_id) REFERENCES teams (id)
);


-- The core table. Raw stat lines ONLY -- never fantasy points. Points are
-- computed on demand against a scoring ruleset (section 6).
CREATE TABLE player_game_stats (
    player_id BIGINT       NOT NULL,
    game_id   BIGINT       NOT NULL,
    season    SMALLINT     NOT NULL,    -- denormalized on purpose, see section 9
    week      SMALLINT     NOT NULL,
    team_id   INT          NOT NULL,
    snap_pct  NUMERIC(5,2),          -- nullable: snap counts are missing pre-2012 and for some weeks

    pass_att  SMALLINT     NOT NULL DEFAULT 0,
    pass_cmp  SMALLINT     NOT NULL DEFAULT 0,
    pass_yd   SMALLINT     NOT NULL DEFAULT 0,
    pass_td   SMALLINT     NOT NULL DEFAULT 0,
    pass_int  SMALLINT     NOT NULL DEFAULT 0,
    pass_2pt  SMALLINT     NOT NULL DEFAULT 0,

    rush_att  SMALLINT     NOT NULL DEFAULT 0,
    rush_yd   SMALLINT     NOT NULL DEFAULT 0,
    rush_td   SMALLINT     NOT NULL DEFAULT 0,
    rush_2pt  SMALLINT     NOT NULL DEFAULT 0,

    targets   SMALLINT     NOT NULL DEFAULT 0,
    rec       SMALLINT     NOT NULL DEFAULT 0,
    rec_yd    SMALLINT     NOT NULL DEFAULT 0,
    rec_td    SMALLINT     NOT NULL DEFAULT 0,
    rec_2pt   SMALLINT     NOT NULL DEFAULT 0,

    fum_lost  SMALLINT     NOT NULL DEFAULT 0,
    ret_td    SMALLINT     NOT NULL DEFAULT 0,   -- special_teams_tds

    punt_ret     SMALLINT  NOT NULL DEFAULT 0,
    punt_ret_yd  SMALLINT  NOT NULL DEFAULT 0,
    kick_ret     SMALLINT  NOT NULL DEFAULT 0,
    kick_ret_yd  SMALLINT  NOT NULL DEFAULT 0,

    -- Kicking. Distance buckets are stored raw because most rulesets score a
    -- 50-yarder differently from a 20-yarder; deriving them later is impossible.
    fg_att          SMALLINT NOT NULL DEFAULT 0,
    fg_made         SMALLINT NOT NULL DEFAULT 0,
    fg_missed       SMALLINT NOT NULL DEFAULT 0,
    fg_blocked      SMALLINT NOT NULL DEFAULT 0,
    fg_long         SMALLINT NOT NULL DEFAULT 0,
    fg_made_0_19    SMALLINT NOT NULL DEFAULT 0,
    fg_made_20_29   SMALLINT NOT NULL DEFAULT 0,
    fg_made_30_39   SMALLINT NOT NULL DEFAULT 0,
    fg_made_40_49   SMALLINT NOT NULL DEFAULT 0,
    fg_made_50_59   SMALLINT NOT NULL DEFAULT 0,
    fg_made_60_plus SMALLINT NOT NULL DEFAULT 0,
    pat_att         SMALLINT NOT NULL DEFAULT 0,
    pat_made        SMALLINT NOT NULL DEFAULT 0,
    pat_missed      SMALLINT NOT NULL DEFAULT 0,

    -- Individual defensive stats. These aggregate to a team DST line for every
    -- category EXCEPT points and yards allowed, which are not player stats and
    -- must come from a team-level source. See section 6.
    --
    -- def_sacks is NUMERIC, not SMALLINT: a shared sack is credited as 0.5.
    -- 272 rows in the 2024 season alone are fractional.
    def_sacks           NUMERIC(4,1) NOT NULL DEFAULT 0,
    def_int             SMALLINT NOT NULL DEFAULT 0,
    def_td              SMALLINT NOT NULL DEFAULT 0,
    def_safety          SMALLINT NOT NULL DEFAULT 0,
    def_fumbles_forced  SMALLINT NOT NULL DEFAULT 0,
    def_fumble_rec      SMALLINT NOT NULL DEFAULT 0,
    def_pass_defended   SMALLINT NOT NULL DEFAULT 0,
    def_tackles_solo    SMALLINT NOT NULL DEFAULT 0,
    def_tackle_assists  SMALLINT NOT NULL DEFAULT 0,
    def_tackles_for_loss SMALLINT NOT NULL DEFAULT 0,
    def_qb_hits         SMALLINT NOT NULL DEFAULT 0,
    def_blocked_kicks   SMALLINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_player_game_stats PRIMARY KEY (player_id, game_id),
    CONSTRAINT fk_pgs_player FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT fk_pgs_game   FOREIGN KEY (game_id)   REFERENCES games (id),
    CONSTRAINT fk_pgs_team   FOREIGN KEY (team_id)   REFERENCES teams (id)
);


CREATE TABLE users (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         CITEXT      NOT NULL,
    password_hash TEXT        NOT NULL,   -- Argon2id
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);


CREATE TABLE scoring_profiles (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT,                    -- NULL = system preset
    name       VARCHAR(64) NOT NULL,
    rules      JSONB       NOT NULL,
    is_preset  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_scoring_profiles_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);


-- Observability, and the evidence behind the "10K+ records daily" claim.
CREATE TABLE ingest_runs (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source        VARCHAR(32) NOT NULL,   -- 'nflverse.player_stats'
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    status        VARCHAR(16) NOT NULL,   -- RUNNING / SUCCESS / FAILED

    -- Unknown until the run ends.
    finished_at   TIMESTAMPTZ,
    rows_read     INT,
    rows_upserted INT,
    error         TEXT
);
