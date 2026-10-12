-- V9: Phase 6 projections, Slice 4 -- who is on which roster each week, and the official injury
-- report. Known information only: nothing here predicts whether anyone plays
-- (docs/projections-v1-architecture.md sections 6 and 8).
--
-- Source values are stored as nflverse ships them. What they mean for a projection -- ranked,
-- badged or left off the board -- is AvailabilityRules' decision, made on read. Changing that rule
-- never needs a backfill, and a row always says what the source said.
--
-- Current season only, rewritten week by week from the source on every daily pull (the ingestors
-- say how). Two tables, because the feeds are different facts with different clocks: a roster
-- status persists until a transaction changes it; an injury designation belongs to one week's game.
-- Measured 2026-10-12 (2024-26 files): both feeds carry one row per player per regular-season week
-- (two injury player-weeks in 2024 excepted), every value below fits, and the Rams are 'LA' in
-- both, as in the schedule.
--
-- No index past the primary keys: one week of one season is ~2,600 roster rows and ~360 injury
-- rows, and every read is "this week", the leading key columns.


-- weekly_rosters/roster_weekly_{season}.csv. Every player on every team that plays that week --
-- active, practice squad, reserve, released -- so a team on its bye has no rows that week.
--
-- What `status` meant for 2025's regular season, rows with an id (measured against stat lines
-- and offensive snaps): ACT 26,129 rows (19,297 played) · DEV practice squad 8,333 (0 played) ·
-- RES reserve lists 5,486 (0) · INA 3,419 (1) · CUT 932 (0) · RET 349 (0) · EXE 7 (0).
-- INA is the GAME-DAY inactive list: written only once a team's inactives are announced, about
-- 90 minutes before kickoff (on 2026-10-12 the Monday night teams read ACT 53, INA 0, while every
-- team that had played read ACT 48 and ~7 INA). A 06:00 pull never sees it in time.
CREATE TABLE roster_week (
    season        SMALLINT    NOT NULL,
    week          SMALLINT    NOT NULL CONSTRAINT ck_roster_week_week CHECK (week BETWEEN 1 AND 18),
    player_id     BIGINT      NOT NULL,
    team_id       INT         NOT NULL,
    status        VARCHAR(8)  NOT NULL,   -- source `status`: ACT, INA, RES, DEV, CUT, RET, EXE, ...
    status_detail VARCHAR(8),             -- source `status_description_abbr`: A01, R01, R48, P07, ...
    -- When this run read the row. The source carries no timestamp of its own.
    ingested_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_roster_week PRIMARY KEY (season, week, player_id),
    CONSTRAINT fk_roster_week_player FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT fk_roster_week_team   FOREIGN KEY (team_id)   REFERENCES teams (id)
);


-- injuries/injuries_{season}.csv: the official report for one week's game, pregame by nature --
-- final statuses land Friday for a Sunday game. A designation belongs to its week and is never
-- read for another.
--
-- report_status for 2025's regular season: Out 1,342 rows, 0 of them with a stat line that week ·
-- Questionable · Doubtful · blank (listed for practice, no game status, stored NULL). 2024 also
-- shipped 'Note' (6 rows). The column list changed between seasons -- 2024 has date_modified and
-- no season_type, 2025-26 the reverse -- so neither is read.
CREATE TABLE injury_report (
    season          SMALLINT    NOT NULL,
    week            SMALLINT    NOT NULL CONSTRAINT ck_injury_report_week CHECK (week BETWEEN 1 AND 18),
    player_id       BIGINT      NOT NULL,
    team_id         INT         NOT NULL,
    report_status   VARCHAR(16),          -- source `report_status`; NULL when blank
    practice_status VARCHAR(48),          -- source `practice_status`
    -- TEXT, not VARCHAR(n): the longest is 154 characters, and CsvValues.text truncates silently.
    report_injury   TEXT,                 -- source `report_primary_injury`
    ingested_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_injury_report PRIMARY KEY (season, week, player_id),
    CONSTRAINT fk_injury_report_player FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT fk_injury_report_team   FOREIGN KEY (team_id)   REFERENCES teams (id)
);
