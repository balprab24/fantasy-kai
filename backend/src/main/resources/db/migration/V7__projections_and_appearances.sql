-- V7: Phase 6 projections, Slice 3 (docs/projections-v1-architecture.md section 8).
--
-- Two tables, and neither touches player_game_stats: it keeps nothing but its primary key,
-- and its row count stays the section 9 baseline.


-- Every game a skill player took an offensive snap in, from snap_counts_{season}.csv -- the
-- file SnapCountIngestor already downloads every day to fill player_game_stats.snap_pct.
--
-- Why it exists: a skill player who ran routes and recorded nothing leaves no stat row, so
-- the projection baseline never saw his empty games and over-projected him. By the rule
-- stored here: 818 skill player-games in 2024 had offensive snaps and no stat line, 439 of them
-- tight ends; 687-851 a season in 2020-25 (measured 2026-10-12). Owner decision 2026-10-12,
-- section 15 option (a).
--
-- Every appearance is stored, with or without a stat line. "Played and recorded nothing" is
-- decided when it is read -- an appearance with no stat row for the same game -- so a stat
-- line that lands after the snap file, or is corrected away, can never leave a game counted
-- twice or not at all.
--
-- No season or week: they come from the games join, so they cannot drift from it. No
-- position filter beyond the one the ingest applies; the reader asks players.position, as
-- the research did.
CREATE TABLE player_game_appearances (
    player_id     BIGINT   NOT NULL,
    game_id       BIGINT   NOT NULL,
    team_id       INT      NOT NULL,
    -- The definition of an appearance, held by the schema rather than only by the ingest.
    offense_snaps SMALLINT NOT NULL CONSTRAINT ck_appearances_offense_snaps CHECK (offense_snaps > 0),
    CONSTRAINT pk_player_game_appearances PRIMARY KEY (player_id, game_id),
    CONSTRAINT fk_appearances_player FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT fk_appearances_game   FOREIGN KEY (game_id)   REFERENCES games (id),
    CONSTRAINT fk_appearances_team   FOREIGN KEY (team_id)   REFERENCES teams (id)
);


-- A projected RAW stat line for one player's target week -- never points. Points depend on
-- a league; this row scores under every league without being regenerated. The same rule as
-- player_game_stats, one layer up (CLAUDE.md: "Project the stat line, never the points").
--
-- The thirteen scorable columns carry exactly StatKey's names, so the repository generates
-- its column list from the enum; ProjectionSchemaTests fails if the two ever differ, and if
-- any column here starts to look like a point value.
--
-- DOUBLE PRECISION, not NUMERIC: a projection is an unrounded expected value (0.43 touchdowns)
-- and it has to read back bit-identical to what the provider computed. Rounding happens once,
-- at the API boundary.
--
-- The primary key leads with (season, week, provider): the job replaces one week at a time
-- and the board reads one week at a time, and both are then a range of this index. A
-- player's own lookup names all four columns, so it is still a key lookup. No other index.
CREATE TABLE player_week_projection (
    season    SMALLINT    NOT NULL,
    week      SMALLINT    NOT NULL CONSTRAINT ck_projection_week CHECK (week BETWEEN 1 AND 18),
    -- ProjectionSource.id: an algorithm and a version, never a class name. The pattern and the
    -- length are ProjectionSource's (MAX_ID_LENGTH = 40), mirrored so a row can never claim a
    -- method nothing could define; ProjectionSchemaTests holds the two lengths together.
    provider  VARCHAR(40) NOT NULL
        CONSTRAINT ck_projection_provider CHECK (provider ~ '^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$'),
    player_id BIGINT      NOT NULL,
    -- The game projected, and the team he was assumed to play it for.
    game_id   BIGINT      NOT NULL,
    team_id   INT         NOT NULL,

    pass_yd   DOUBLE PRECISION NOT NULL,
    pass_td   DOUBLE PRECISION NOT NULL,
    pass_int  DOUBLE PRECISION NOT NULL,
    pass_2pt  DOUBLE PRECISION NOT NULL,
    rush_yd   DOUBLE PRECISION NOT NULL,
    rush_td   DOUBLE PRECISION NOT NULL,
    rush_2pt  DOUBLE PRECISION NOT NULL,
    rec       DOUBLE PRECISION NOT NULL,
    rec_yd    DOUBLE PRECISION NOT NULL,
    rec_td    DOUBLE PRECISION NOT NULL,
    rec_2pt   DOUBLE PRECISION NOT NULL,
    fum_lost  DOUBLE PRECISION NOT NULL,
    ret_td    DOUBLE PRECISION NOT NULL,

    -- Volume, display only and never scored ("6.1 targets").
    pass_att  DOUBLE PRECISION NOT NULL,
    rush_att  DOUBLE PRECISION NOT NULL,
    targets   DOUBLE PRECISION NOT NULL,

    -- How many games the line drew on, and the newest of them. "Limited history" is a label
    -- a later layer derives from games_used; it is not stored.
    games_used          SMALLINT NOT NULL CONSTRAINT ck_projection_games_used CHECK (games_used >= 1),
    data_through_season SMALLINT NOT NULL,
    data_through_week   SMALLINT NOT NULL,

    generated_at TIMESTAMPTZ NOT NULL,
    -- The game's kickoff, once it has passed. From then on the row is the record of what was
    -- projected before the game, and the trigger below refuses to change it.
    locked_at    TIMESTAMPTZ,

    CONSTRAINT pk_player_week_projection PRIMARY KEY (season, week, provider, player_id),
    CONSTRAINT fk_projection_player FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT fk_projection_game   FOREIGN KEY (game_id)   REFERENCES games (id),
    CONSTRAINT fk_projection_team   FOREIGN KEY (team_id)   REFERENCES teams (id)
);


-- Normal application code never rewrites a projection after its game has kicked off. The
-- live-accuracy record (Slice 7) compares these rows with what happened, and it is only honest
-- if nothing touched them after kickoff. ProjectionJob already never writes a locked row; this
-- trigger makes that hold for every code path, including one that forgets the rule.
--
-- It is not a claim that no correction can ever happen. Genuine corruption -- a projection for
-- a game that never existed -- can be repaired one row at a time, deliberately, by an operator:
--
--   BEGIN;
--   SET LOCAL fantasykai.projection_repair_reason = '<why, in a sentence>';
--   DELETE FROM player_week_projection WHERE season = .. AND week = .. AND provider = ..
--                                         AND player_id = ..;
--   COMMIT;
--
-- (deploy/README.md, "Repairing a locked projection"). The reason lasts one transaction, no
-- application code sets it (ProjectionSchemaTests checks src/main), and the row as it stood is
-- kept in player_week_projection_repairs first, so a repair corrects the board without erasing
-- what was recorded. Locking itself is allowed: setting locked_at on an open row does not fire.
-- TRUNCATE does not fire row triggers; it is for an empty test database, never a repair.
CREATE TABLE player_week_projection_repairs (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    repaired_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    operation   VARCHAR(6)  NOT NULL CONSTRAINT ck_repairs_operation CHECK (operation IN ('UPDATE', 'DELETE')),
    reason      TEXT        NOT NULL CONSTRAINT ck_repairs_reason CHECK (length(btrim(reason)) >= 10),
    db_user     TEXT        NOT NULL DEFAULT current_user,
    -- The locked row exactly as it stood before the repair.
    old_row     JSONB       NOT NULL
);

CREATE FUNCTION guard_locked_projection() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    -- missing_ok: NULL when never set; '' once a SET LOCAL has ended in this session.
    reason TEXT := current_setting('fantasykai.projection_repair_reason', true);
BEGIN
    IF reason IS NULL OR btrim(reason) = '' THEN
        RAISE EXCEPTION 'projection for player % in % week % (%) was locked at % and is never rewritten by the application; a repair needs fantasykai.projection_repair_reason (deploy/README.md)',
            OLD.player_id, OLD.season, OLD.week, OLD.provider, OLD.locked_at
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    INSERT INTO player_week_projection_repairs (operation, reason, old_row)
    VALUES (TG_OP, reason, to_jsonb(OLD));
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_projection_locked
    BEFORE UPDATE OR DELETE ON player_week_projection
    FOR EACH ROW
    WHEN (OLD.locked_at IS NOT NULL)
    EXECUTE FUNCTION guard_locked_projection();
