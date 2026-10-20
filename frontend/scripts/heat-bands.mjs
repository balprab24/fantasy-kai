#!/usr/bin/env node
/**
 * Writes src/lib/heatBands.ts: the cut-offs the player view's game log and
 * career shade box-score cells by (green, yellow, orange, red: top quarter of
 * games down to bottom quarter). The comparison set is every regular-season
 * game played by each season's fantasy-relevant players by volume -- see
 * heat-bands.sql -- from the loaded database, never an estimate:
 *
 *   docker compose exec -T postgres psql -U fantasykai -d fantasykai -A -F, -P footer=off \
 *     < frontend/scripts/heat-bands.sql | node frontend/scripts/heat-bands.mjs > frontend/src/lib/heatBands.ts
 *
 * Weekly cut-offs use each value's mid-rank (games below it, plus half the
 * games level with it), so a value most games share -- a receiver's zero
 * touchdowns -- lands in the middle of the ties, not at either end. Season
 * cut-offs are the quartiles of per-game averages, one per player-season.
 */
import { readFileSync } from "node:fs";

// Snap share is graded at every position (owner decision, 2026-10-20): it is
// how a rookie's role grows and how a backfield splits. It is graded per game
// only -- the career has no snap column.
const STATS = {
  QB: ["snap_pct", "pass_cmp", "pass_att", "pass_yd", "pass_td", "pass_int", "rush_att", "rush_yd", "rush_td"],
  RB: ["snap_pct", "rush_att", "rush_yd", "rush_td", "targets", "rec", "rec_yd", "rec_td"],
  WR: ["snap_pct", "targets", "rec", "rec_yd", "rec_td"],
  TE: ["snap_pct", "targets", "rec", "rec_yd", "rec_td"],
};
// Players per position per season in the comparison set: must match the
// LIMITs in heat-bands.sql, and is checked against the rows below.
const RELEVANT = { QB: 24, RB: 48, WR: 72, TE: 24 };
// Fewer is better.
const INVERT = new Set(["pass_int"]);
// The tables' key for a column whose name differs from the query's.
const KEY = { snap_pct: "snap" };

const [header, ...lines] = readFileSync(0, "utf8").trim().split("\n");
const cols = header.split(",");
const rows = lines.map((l) => Object.fromEntries(l.split(",").map((v, i) => [cols[i], v])));
if (rows.length < 1000) throw new Error(`only ${rows.length} rows: is the database loaded?`);

const lowerBound = (xs, v) => { let lo = 0, hi = xs.length; while (lo < hi) { const m = (lo + hi) >> 1; if (xs[m] < v) lo = m + 1; else hi = m; } return lo; };
const upperBound = (xs, v) => { let lo = 0, hi = xs.length; while (lo < hi) { const m = (lo + hi) >> 1; if (xs[m] <= v) lo = m + 1; else hi = m; } return lo; };

// Each distinct value with its mid-rank among `xs` (sorted).
function midRanks(xs) {
  const pr = (v) => (lowerBound(xs, v) + 0.5 * (upperBound(xs, v) - lowerBound(xs, v))) / xs.length;
  return [...new Set(xs)].map((v) => [v, pr(v)]);
}

function weekCuts(values, invert) {
  const xs = [...values].sort((a, b) => a - b);
  const seen = midRanks(xs);
  if (!invert) {
    const first = (t, set = seen) => set.find(([, p]) => p >= t)?.[0] ?? null;
    const green = first(0.75);
    if (green !== null) return [green, first(0.5), first(0.25)];
    // More than half the games share the best value, so no value reaches the
    // top quarter and quartiles cannot split them -- a quarterback is on the
    // field for every snap in 77% of games (measured 2026-10-20). Nothing beats
    // the best value, so it is the top band, and the games below it split into
    // the other three by thirds.
    const best = xs.at(-1);
    const below = midRanks(xs.filter((v) => v < best));
    return [best, first(2 / 3, below), first(1 / 3, below)];
  }
  const last = (t) => seen.filter(([, p]) => p <= t).at(-1)?.[0] ?? null;
  // Nothing beats none: the best possible count is always the top band.
  return [Math.max(last(0.25) ?? 0, 0), last(0.5), last(0.75)];
}

function quartile(sorted, q) {
  const pos = (sorted.length - 1) * q;
  const lo = Math.floor(pos), hi = Math.ceil(pos);
  return +(sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)).toFixed(2);
}

const week = {}, rate = {}, counts = {};
for (const [pos, stats] of Object.entries(STATS)) {
  const mine = rows.filter((r) => r.position === pos);
  const bySeason = new Map();
  for (const r of mine) {
    const k = `${r.season}-${r.player_id}`;
    if (!bySeason.has(k)) bySeason.set(k, []);
    bySeason.get(k).push(r);
  }
  counts[pos] = { games: mine.length, playerSeasons: bySeason.size };
  const seasonCount = new Set(mine.map((r) => r.season)).size;
  if (bySeason.size !== RELEVANT[pos] * seasonCount) {
    throw new Error(`${pos}: ${bySeason.size} player-seasons, expected ${RELEVANT[pos]} x ${seasonCount} -- RELEVANT and heat-bands.sql disagree`);
  }
  week[pos] = {}; rate[pos] = {};
  for (const st of stats) {
    const values = mine.filter((r) => r[st] !== "").map((r) => Number(r[st]));
    week[pos][KEY[st] ?? st] = weekCuts(values, INVERT.has(st));
    if (st === "snap_pct") continue; // the career has no snap column
    const perGame = [...bySeason.values()]
      .map((g) => g.filter((r) => r[st] !== "").map((r) => Number(r[st])))
      .filter((v) => v.length)
      .map((v) => v.reduce((a, b) => a + b, 0) / v.length)
      .sort((a, b) => a - b);
    const [p25, p50, p75] = [0.25, 0.5, 0.75].map((q) => quartile(perGame, q));
    rate[pos][st] = INVERT.has(st) ? [p25, p50, p75] : [p75, p50, p25];
  }
}

const seasons = [...new Set(rows.map((r) => r.season))].sort();
const json = (o) => JSON.stringify(o, null, 2).replace(/\[\s+([^\]]+?)\s+\]/g, (_, inner) => `[${inner.replace(/\s+/g, " ")}]`);
process.stdout.write(`/**
 * Where a box-score figure falls among the league's: the cut-offs the player
 * view's heat tables shade by -- green for the top quarter, then yellow,
 * orange, and red for the bottom quarter. Written by scripts/heat-bands.mjs
 * from scripts/heat-bands.sql; do not edit by hand.
 *
 * Measured over every regular-season game, ${seasons[0]}-${seasons.at(-1)}, by each season's
 * fantasy-relevant players by volume: the top 24 quarterbacks by pass
 * attempts, 48 running backs by carries plus catches, 72 receivers and 24
 * tight ends by targets. ${Object.entries(counts).map(([p, c]) => `${p} ${c.games} games / ${c.playerSeasons} player-seasons`).join("; ")}.
 *
 * Each entry is [green, yellow, orange]: a figure at or above the first is
 * green, at or above the second yellow, at or above the third orange, and
 * anything below is red. Interceptions run the other way (at or below). Where
 * more than half the games share the best value -- a quarterback's every snap
 * -- that value alone is green and the games below it split by thirds.
 * \`week\` is one game; \`perGame\` is a season's per-game average.
 */
export const HEAT_SEASONS = ${JSON.stringify(`${seasons[0]}-${seasons.at(-1)}`)};

/** Players per position per season in the comparison set -- the legend names it. */
export const HEAT_RELEVANT: Record<string, number> = ${json(RELEVANT)};

export const HEAT_WEEK: Record<string, Record<string, [number, number, number]>> = ${json(week)};

export const HEAT_PER_GAME: Record<string, Record<string, [number, number, number]>> = ${json(rate)};

/** Stats where fewer is better. */
export const HEAT_INVERTED: readonly string[] = ${json([...INVERT])};
`);
