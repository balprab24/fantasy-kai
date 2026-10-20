#!/usr/bin/env node
/**
 * Re-captures the landing page's evidence from a running API, and writes it to
 * src/components/landing/heroData.ts. The landing is public and every read
 * needs an account (north-star §2), so the page shows captures rather than
 * fetching -- and this script is how they stay real and current.
 *
 *   FK_API=http://localhost:8080 FK_EMAIL=... FK_PASSWORD=... node scripts/capture-landing.mjs
 *
 * Use a throwaway local account: the rule mixer needs three rulesets beyond
 * the presets (0, 0.5 and 1 a catch with 6-point passing touchdowns), and the
 * script saves them to that account if they are not there yet -- through the
 * same POST the Scoring page uses, validated by the same server.
 *
 * What it reads, every one a plain API request:
 *   GET /api/v1/rankings?profileId=<each of six>&season=<S>&scope=season&size=200
 *   GET /api/v1/players/<P>                      the followed player's identity
 *   GET /api/v1/players/<P>/career?profileId=<Half PPR>
 *   GET /api/v1/players/<P>/gamelog?season=<S>&profileId=<0 PPR, Half PPR, PPR>
 * <S> is the current season; <P> is the Half PPR board's best wide receiver,
 * or FK_PLAYER=<id>.
 */
import { writeFileSync } from "node:fs";

const API = process.env.FK_API ?? "http://localhost:8080";
const SEASON = Number(process.env.FK_SEASON ?? new Date().getFullYear());
const TOP = 60; // tiers and positional ranks are cut over exactly the top 60

async function call(path, { token, method = "GET", body } = {}) {
  const res = await fetch(API + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${method} ${path}: ${res.status} ${await res.text()}`);
  return res.status === 204 ? null : res.json();
}

const login = await call("/api/v1/auth/login", {
  method: "POST",
  body: { email: process.env.FK_EMAIL, password: process.env.FK_PASSWORD },
});
const token = login.accessToken;

// The presets, by their stored names; the mixer's 6-point rulesets, by name, created if missing.
const BASE = {
  pass_yd: 0.04, pass_td: 4, pass_int: -2, pass_2pt: 2,
  rush_yd: 0.1, rush_td: 6, rush_2pt: 2,
  rec: 0, rec_yd: 0.1, rec_td: 6, rec_2pt: 2,
  fum_lost: -2, ret_td: 6,
};
let profiles = await call("/api/v1/scoring-profiles", { token });
const preset = (name) => profiles.find((p) => p.preset && p.name === name).id;
async function mixer(rec) {
  const name = `Mixer ${rec} a catch, 6-pt pass TD`;
  const found = profiles.find((p) => !p.preset && p.name === name);
  if (found) return found.id;
  const made = await call("/api/v1/scoring-profiles", {
    token, method: "POST",
    body: { name, rules: JSON.stringify({ version: 1, base: { ...BASE, rec, pass_td: 6 } }) },
  });
  profiles = await call("/api/v1/scoring-profiles", { token });
  return made.id;
}
const IDS = {
  "0|4": preset("Standard"), "0.5|4": preset("Half PPR"), "1|4": preset("Full PPR"),
  "0|6": await mixer(0), "0.5|6": await mixer(0.5), "1|6": await mixer(1),
};

const strip = (r) => ({ ...r, espnId: null }); // the landing shows no player's likeness
const boards = {};
let total = null;
for (const [key, id] of Object.entries(IDS)) {
  const page = await call(`/api/v1/rankings?profileId=${id}&season=${SEASON}&scope=season&size=200`, { token });
  boards[key] = page.content.slice(0, TOP).map(strip);
  total = page.total;
}

const followed = Number(process.env.FK_PLAYER ?? boards["0.5|4"].find((r) => r.position === "WR").playerId);
const player = await call(`/api/v1/players/${followed}`, { token });
const career = await call(`/api/v1/players/${followed}/career?profileId=${IDS["0.5|4"]}`, { token });
const season = career.seasons.find((s) => s.season === SEASON);
const logs = {};
for (const key of ["0|4", "0.5|4", "1|4"]) {
  logs[key] = await call(`/api/v1/players/${followed}/gamelog?season=${SEASON}&profileId=${IDS[key]}`, { token });
}
// The receipt's game: his most catches this regular season, the latest on a tie.
const reg = logs["0.5|4"].weeks.filter((w) => w.seasonType === "REG");
const game = [...reg].sort((a, b) => b.stats.rec - a.stats.rec || b.week - a.week)[0];
const pointsOf = (key) => logs[key].weeks.find((w) => w.week === game.week).points;

const captured = new Date().toISOString().slice(0, 10);
const json = (v) => JSON.stringify(v, null, 2);
const out = `import type { CareerSeason, RankingRow, StatKey, Usage } from "@/lib/types";

/**
 * The landing page's evidence: real API responses, captured -- the page is
 * public and every read needs an account (north-star §2). Written by
 * \`scripts/capture-landing.mjs\` on ${captured} from ${API}, which lists every
 * request it makes; re-run it to bring the page up to date. Do not edit by hand.
 *
 * Unlike the 2025 captures before it, this goes stale as the season moves.
 * The page says which week it runs through, read off the rows (\`HERO_THROUGH_WEEK\`),
 * and \`scripts/session-check.sh\` warns once a newer week has finished.
 * \`espnId\` is null everywhere: the landing shows no player's likeness.
 */

/** The season every board here is for: the requests' own parameter. */
export const HERO_SEASON = ${SEASON};

/** The day of the capture. */
export const HERO_CAPTURED = "${captured}";

/**
 * The week the boards run through, read off the rows: the most games anyone on
 * them has played. \`scripts/session-check.sh\` reads the same thing.
 */
export const HERO_THROUGH_WEEK = ${Math.max(...Object.values(boards).flat().map((r) => r.gamesPlayed))};

/** Players on each whole board (only the top ${TOP} are kept). */
export const HERO_TOTAL = ${total};

/** What a catch is worth, and what a passing touchdown is worth: the mixer's two dials. */
export type CatchRate = 0 | 0.5 | 1;
export type PassTd = 4 | 6;
export type MixKey = \`\${CatchRate}|\${PassTd}\`;

/**
 * The top ${TOP} of the ${SEASON} season board under each combination. 0, 0.5 and 1 a
 * catch with 4-point passing touchdowns are the presets 0 PPR, Half PPR and PPR;
 * with 6-point touchdowns they are the same rules with that one rate changed,
 * saved as rulesets on the capturing account. Every other rate is V3's.
 */
export const MIXER_BOARDS: Record<MixKey, RankingRow[]> = ${json(boards)};

/** The player the page follows: the Half PPR board's best wide receiver when captured. */
export const FOLLOWED = ${json({
  id: player.id,
  name: player.name,
  position: player.position,
  team: player.team,
  teamName: player.teamName,
  birthDate: player.birthDate,
})} as const;

/** His ${SEASON} regular season under Half PPR, as \`/career\` returned it. */
export const FOLLOWED_SEASON: CareerSeason = ${json(season)};

/**
 * The receipt's game: his most catches this regular season. Its box score and
 * the points the API gave it under 0 PPR, Half PPR and PPR.
 */
export const RECEIPT_GAME: {
  week: number;
  opponent: string | null;
  home: boolean | null;
  stats: Record<StatKey, number>;
  usage: Usage;
  points: Record<CatchRate, number>;
} = ${json({
  week: game.week,
  opponent: game.opponent,
  home: game.home,
  stats: game.stats,
  usage: game.usage,
  points: { 0: pointsOf("0|4"), 0.5: pointsOf("0.5|4"), 1: pointsOf("1|4") },
})};
`;
const file = new URL("../src/components/landing/heroData.ts", import.meta.url);
writeFileSync(file, out);
console.log(`wrote ${file.pathname}: ${SEASON}, ${Object.keys(boards).length} boards, followed ${player.name}, receipt week ${game.week}`);
