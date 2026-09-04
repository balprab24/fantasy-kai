-- V3: the four system scoring presets.
--
-- Data only. player_game_stats still carries nothing but its primary key, so
-- the section 9 baseline is untouched.
--
-- These four rows are the argument for the whole design. Standard, half PPR,
-- full PPR and TE premium are not four code paths -- they are one dot-product
-- over four different rate tables, and the only thing that differs between the
-- first three is the value of `rec`.
--
-- No yardage bonuses here on purpose: ESPN and Yahoo default scoring has none,
-- and it keeps the presets checkable against nflverse's own precomputed
-- fantasy_points column, which also has none. Bonuses are a custom-ruleset
-- feature (section 6) and are exercised by the tests, not seeded.
--
-- user_id NULL marks a system preset; is_preset carries the same fact for
-- queries that filter on it. Both are set so neither has to be inferred.

INSERT INTO scoring_profiles (user_id, name, is_preset, rules) VALUES

(NULL, 'Standard', TRUE, '{
  "version": 1,
  "base": {
    "pass_yd": 0.04, "pass_td": 4, "pass_int": -2, "pass_2pt": 2,
    "rush_yd": 0.1, "rush_td": 6, "rush_2pt": 2,
    "rec": 0, "rec_yd": 0.1, "rec_td": 6, "rec_2pt": 2,
    "fum_lost": -2, "ret_td": 6
  }
}'::jsonb),

(NULL, 'Half PPR', TRUE, '{
  "version": 1,
  "base": {
    "pass_yd": 0.04, "pass_td": 4, "pass_int": -2, "pass_2pt": 2,
    "rush_yd": 0.1, "rush_td": 6, "rush_2pt": 2,
    "rec": 0.5, "rec_yd": 0.1, "rec_td": 6, "rec_2pt": 2,
    "fum_lost": -2, "ret_td": 6
  }
}'::jsonb),

(NULL, 'Full PPR', TRUE, '{
  "version": 1,
  "base": {
    "pass_yd": 0.04, "pass_td": 4, "pass_int": -2, "pass_2pt": 2,
    "rush_yd": 0.1, "rush_td": 6, "rush_2pt": 2,
    "rec": 1.0, "rec_yd": 0.1, "rec_td": 6, "rec_2pt": 2,
    "fum_lost": -2, "ret_td": 6
  }
}'::jsonb),

-- Full PPR plus the one override. This row is the design in miniature: a TE
-- premium league is not a variant of the scoring code, it is a nested object.
(NULL, 'TE Premium', TRUE, '{
  "version": 1,
  "base": {
    "pass_yd": 0.04, "pass_td": 4, "pass_int": -2, "pass_2pt": 2,
    "rush_yd": 0.1, "rush_td": 6, "rush_2pt": 2,
    "rec": 1.0, "rec_yd": 0.1, "rec_td": 6, "rec_2pt": 2,
    "fum_lost": -2, "ret_td": 6
  },
  "position_overrides": { "TE": { "rec": 1.5 } }
}'::jsonb);
