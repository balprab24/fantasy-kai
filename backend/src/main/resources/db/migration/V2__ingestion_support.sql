-- V2: what ingestion needs that V1 did not anticipate.
--
-- Neither of these is a rankings-path index, so the section 9 baseline is
-- untouched: player_game_stats still carries nothing but its primary key.

-- nflverse identifies a game as '2024_01_NYJ_SF'. Carrying it as a natural key
-- lets a stat line resolve to a game with a single subselect, instead of
-- parsing the string back into season/week/home/away and matching on the
-- surrogate ids. It is also what makes the games upsert idempotent.
ALTER TABLE games ADD COLUMN nflverse_game_id VARCHAR(20) NOT NULL;
ALTER TABLE games ADD CONSTRAINT uq_games_nflverse_id UNIQUE (nflverse_game_id);

-- The Sleeper crosswalk looks players up by their Sleeper id. This serves the
-- ingestion path only -- the rankings query never touches external_ids -- so it
-- belongs in Phase 1 rather than the Phase 6 performance pass.
CREATE INDEX idx_players_sleeper_id ON players ((external_ids ->> 'sleeper'));
