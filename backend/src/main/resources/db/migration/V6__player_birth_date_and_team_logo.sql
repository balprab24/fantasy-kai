-- V6: two display facts the player workspace needs, from files ingestion
-- already downloads every day and was discarding.
--
-- Probed on 2026-09-28 rather than reasoned about:
--
--   players.csv birth_date   24,833 rows · 32 blank · 0 not YYYY-MM-DD
--                            QB/RB/WR/TE active since 2020: 1,900 of 1,903 carry one
--   teams_colors_logos.csv   36 rows · every team_logo_espn is https:// · longest 54 chars
--
-- Headshots are deliberately NOT a column. players.external_ids->>'espn' is
-- already stored (1,296 of the 1,298 QB/RB/WR/TE with a stat line carry one,
-- all digits) and the image URL is a pure function of it, so storing the URL
-- would be a second copy of a fact that can only drift from the first.
--
-- Team logos cannot be derived the same way: ESPN's slug is not the nflverse
-- abbreviation (LA and STL are both lar.png, WAS is wsh.png, SD is lac.png), and
-- a hand-kept mapping is exactly what the source file already is.
--
-- No index. Nothing filters on either column, and player_game_stats is
-- untouched, so the section 9 baseline is too.

-- Nullable: 32 players in the source have no recorded birth date, and a blank
-- is "not recorded", never a date to invent.
ALTER TABLE players ADD COLUMN birth_date DATE;

-- TEXT, not VARCHAR(n): CsvValues.text(record, column, max) truncates silently,
-- and a truncated URL is stored broken rather than rejected. The CHECK is the
-- guarantee the frontend relies on when it puts this in an <img src>.
ALTER TABLE teams ADD COLUMN logo_url TEXT
    CONSTRAINT ck_teams_logo_url_https CHECK (logo_url LIKE 'https://%');
