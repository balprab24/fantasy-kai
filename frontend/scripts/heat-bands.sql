-- Weekly regular-season lines of each season's fantasy-relevant players by volume:
-- QB top 24 by pass attempts, RB top 48 by carries + catches, WR top 72 by targets,
-- TE top 24 by targets. Seasons 2023-2025.
WITH reg AS (
  SELECT s.*, p.position
  FROM player_game_stats s
  JOIN games g ON g.id = s.game_id AND g.season_type = 'REG'
  JOIN players p ON p.id = s.player_id
  WHERE s.season BETWEEN 2023 AND 2025 AND p.position IN ('QB','RB','WR','TE')
),
vol AS (
  SELECT season, player_id, position,
         CASE position WHEN 'QB' THEN sum(pass_att) WHEN 'RB' THEN sum(rush_att) + sum(rec) ELSE sum(targets) END AS volume
  FROM reg GROUP BY season, player_id, position
),
ranked AS (
  SELECT *, row_number() OVER (PARTITION BY season, position ORDER BY volume DESC, player_id) AS r FROM vol
),
pop AS (
  SELECT season, player_id, position FROM ranked
  WHERE r <= CASE position WHEN 'QB' THEN 24 WHEN 'RB' THEN 48 WHEN 'WR' THEN 72 ELSE 24 END
)
SELECT reg.position, reg.season, reg.player_id, reg.pass_cmp, reg.pass_att, reg.pass_yd, reg.pass_td, reg.pass_int,
       reg.rush_att, reg.rush_yd, reg.rush_td, reg.targets, reg.rec, reg.rec_yd, reg.rec_td
FROM reg JOIN pop USING (season, player_id, position);
