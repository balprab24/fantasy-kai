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
    -- ProjectionSource.id: an algorithm and a version, never a class name. Mirrored here so a
    -- row can never claim a method nothing defines.
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


-- A locked projection is never rewritten -- not by the job, not by a hand-typed UPDATE, not
-- by a future code path that forgot the rule. The live-accuracy record (Slice 7) compares
-- these rows with what happened, and it is only honest if nothing touched them after kickoff.
-- ProjectionJob already never writes one; this makes "never" a property of the table.
--
-- Locking itself is allowed: setting locked_at on a row whose locked_at is NULL does not fire.
-- TRUNCATE does not fire row triggers, deliberately -- it is the one way to clear the table,
-- and it is not something a code path does by accident.
CREATE FUNCTION refuse_locked_projection_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'projection for player % in % week % (%) was locked at % and is never rewritten',
        OLD.player_id, OLD.season, OLD.week, OLD.provider, OLD.locked_at
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_projection_locked
    BEFORE UPDATE OR DELETE ON player_week_projection
    FOR EACH ROW
    WHEN (OLD.locked_at IS NOT NULL)
    EXECUTE FUNCTION refuse_locked_projection_change();
