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

const STATS = {
  // A quarterback is on the field for nearly every snap; his snap share says nothing, so it is not shaded.
  QB: ["pass_cmp", "pass_att", "pass_yd", "pass_td", "pass_int", "rush_att", "rush_yd", "rush_td"],
  RB: ["snap_pct", "rush_att", "rush_yd", "rush_td", "targets", "rec", "rec_yd", "rec_td"],
  WR: ["snap_pct", "targets", "rec", "rec_yd", "rec_td"],
  TE: ["snap_pct", "targets", "rec", "rec_yd", "rec_td"],
};
// Fewer is better.
const INVERT = new Set(["pass_int"]);
const KEY = { snap_pct: "snap" };

const [header, ...lines] = readFileSync(0, "utf8").trim().split("\n");
const cols = header.split(",");
const rows = lines.map((l) => Object.fromEntries(l.split(",").map((v, i) => [cols[i], v])));
if (rows.length < 1000) throw new Error(`only ${rows.length} rows: is the database loaded?`);

const lowerBound = (xs, v) => { let lo = 0, hi = xs.length; while (lo < hi) { const m = (lo + hi) >> 1; if (xs[m] < v) lo = m + 1; else hi = m; } return lo; };
const upperBound = (xs, v) => { let lo = 0, hi = xs.length; while (lo < hi) { const m = (lo + hi) >> 1; if (xs[m] <= v) lo = m + 1; else hi = m; } return lo; };

function weekCuts(values, invert) {
  const xs = [...values].sort((a, b) => a - b);
  const n = xs.length;
  const pr = (v) => (lowerBound(xs, v) + 0.5 * (upperBound(xs, v) - lowerBound(xs, v))) / n;
  const seen = [...new Set(xs)].map((v) => [v, pr(v)]);
  if (!invert) {
    const first = (t) => seen.find(([, p]) => p >= t)?.[0] ?? null;
    return [first(0.75), first(0.5), first(0.25)];
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
 * anything below is red. Interceptions run the other way (at or below).
 * \`week\` is one game; \`perGame\` is a season's per-game average.
 */
export const HEAT_SEASONS = ${JSON.stringify(`${seasons[0]}-${seasons.at(-1)}`)};

export const HEAT_WEEK: Record<string, Record<string, [number, number, number]>> = ${json(week)};

export const HEAT_PER_GAME: Record<string, Record<string, [number, number, number]>> = ${json(rate)};

/** Stats where fewer is better. */
export const HEAT_INVERTED: readonly string[] = ${json([...INVERT])};
`);
