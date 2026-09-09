-- V4: the Vegas layer (north star section 10, Phase 4).
--
-- games carried 8 of the 46 columns in schedules/games.csv. GameIngestor already
-- downloaded the whole file on every run and discarded the rest, so this adds ten
-- columns and no HTTP source. They are what Phase 6 projects from: the implied
-- team total (total_line/2 +/- spread_line/2) is the team_volume term in the
-- projection model, the factor that makes a projection more than an average of
-- past weeks.
--
-- Every type below was chosen from a probe of the real file -- 7,548 rows,
-- seasons 1999-2026, read on 2026-09-08 -- rather than from reasoning about what
-- the type ought to be. The measured range is in the comment beside each column.
--
-- No index. The section 9 baseline invariant is scoped to player_game_stats, so
-- games is not frozen -- but nothing queries these columns yet, and
-- uq_games_matchup already leads with (season, week), which is how a projection
-- reaches a game. An index here would be the reflex, not the measurement.

ALTER TABLE games
    -- Result. NULL until the game is played: 272 of the 1,965 rows in the
    -- 2020-2026 window are unplayed, all of them 2026. Measured 0..70 and 0..59.
    -- Zero is a real score -- 32 shutouts since 2020 -- so a blank field has to
    -- land as NULL rather than 0, which is why the mapper cannot use
    -- CsvValues.shortValue.
    ADD COLUMN home_score     SMALLINT,
    ADD COLUMN away_score     SMALLINT,

    -- Betting. Lines land roughly a week ahead of kickoff, so the game row exists
    -- long before these are populated. That is why the upsert refreshes them
    -- rather than only inserting them.
    --
    -- NUMERIC(4,1), not SMALLINT: 3,321 of 7,388 spreads and 3,681 total lines
    -- carry a half point, and nothing in the file carries more than one decimal
    -- place. SMALLINT would round 3.5 to 4 and silently corrupt every other line
    -- -- the def_sacks lesson exactly. Measured spread -19..27, total 28.5..63.5.
    ADD COLUMN spread_line    NUMERIC(4,1),
    ADD COLUMN total_line     NUMERIC(4,1),

    -- INTEGER, but not for the reason the north star gave. It said moneylines
    -- "exceed +/-32,767 on heavy favourites"; they do not. The most extreme value
    -- in 27 seasons is -5,000, which SMALLINT holds with 6.5x to spare. INT is
    -- kept for headroom against a feed we do not control, at a cost of 2 bytes on
    -- a 1,965-row table. Measured -5,000..1,100 and -1,800..2,173.
    ADD COLUMN home_moneyline INTEGER,
    ADD COLUMN away_moneyline INTEGER,

    -- Conditions. Four distinct roof values, longest 'outdoors' at 8 characters;
    -- eight surfaces, longest 'matrixturf' and 'dessograss' at 10. Both are
    -- sometimes blank -- 43 of the 272 2026 rows have no roof recorded yet. Sized
    -- with headroom on purpose: CsvValues.text truncates rather than failing, so
    -- an undersized column stores a wrong value instead of raising a loud one.
    ADD COLUMN roof           VARCHAR(12),
    ADD COLUMN surface        VARCHAR(16),

    -- temp goes negative (measured -6..109) and wind is legitimately 0 in 29 of
    -- the 2020-2026 rows, so neither column can treat a blank as zero. Blank means
    -- "not recorded", which is 2,342 rows -- and note that blank is not the same
    -- as indoors: 297 outdoor games have no temperature either.
    ADD COLUMN temp           SMALLINT,
    ADD COLUMN wind           SMALLINT;

-- Deliberately NOT stored, part 1: the four odds columns. The source also ships
-- away_spread_odds, home_spread_odds, over_odds and under_odds -- all measured in
-- the -146..138 range, all integers, all cheap to carry. They are the vig: the
-- price you pay to take a side, not the line itself. The projection model reads
-- implied_team_total = total_line/2 +/- spread_line/2, which uses the line and
-- never the price, so these ten columns are the Vegas layer and those four are
-- not. Add them the day something actually consumes them.
--
-- Deliberately NOT stored, part 2: result and total, which the source also ships
-- and the Phase 4 brief originally listed. Checked against all 7,276 played games
-- with zero exceptions:
--
--     total  = home_score + away_score
--     result = home_score - away_score
--
-- They are computed values, and a corrected score would leave them stale. That is
-- the same rule that keeps implied_team_total out of this table and fantasy_points
-- out of player_game_stats: derive on read, never persist.
