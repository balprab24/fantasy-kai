-- Explicit, auditable exceptions to automatic provider identity matching.
CREATE TABLE player_id_overrides (
    platform VARCHAR(16) NOT NULL CHECK (platform IN ('sleeper', 'espn', 'yahoo')),
    external_id VARCHAR(64) NOT NULL CHECK (length(trim(external_id)) > 0),
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    PRIMARY KEY (platform, external_id),
    UNIQUE (platform, player_id)
);

CREATE INDEX players_espn_id ON players ((external_ids->>'espn'));
