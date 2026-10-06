/**
 * The pure modules behind the board and the player page, checked without a
 * browser or a framework: `node --test` runs TypeScript directly on Node 24, so
 * this costs no dependency. Only modules whose imports are all type-only can
 * be loaded this way -- which is also a fair test of what "pure" means.
 */
import { strict as assert } from "node:assert";
import { readdirSync, readFileSync } from "node:fs";
import { describe, it } from "node:test";
import {
  HALF_PPR_TOP20_2025,
  HERO_BOARD_2025,
  MCCAFFREY_2025_BY_RULESET,
  MCCAFFREY_2025_PPR,
  MCCAFFREY_2025_PPR_RECEIPT,
  MCCAFFREY_2025_WEEK7,
  MCCAFFREY_2025_WEEK7_RECEIPTS,
  NACUA_2025_PPR,
  NACUA_SEASONS_PPR,
  PPR_2025,
  PPR_FROM_ZERO_2025,
  PPR_VS_ZERO_2025,
  PRESET_RATES,
  SF_2025_BYE_WEEK,
  WR_PPR_2025,
  WR_PPR_FROM_ZERO_2025,
  ZERO_PPR_TOP20_2025,
  type Ruleset,
} from "../src/components/landing/previewData.ts";
import { band, bandFor, formatPoints, starterWeeks, toBoardRows } from "../src/lib/board.ts";
import { boardSearch, parseBoardParams } from "../src/lib/boardParams.ts";
import { apiOriginOf, contentSecurityPolicy } from "../src/lib/csp.ts";
import {
  HERO_SLOTS,
  catchCaps,
  entranceOf,
  heroSkips,
  heroSlotOf,
  movedBetween,
  posRankOf,
} from "../src/lib/heroBoard.ts";
import { CUTOUT_ASPECT, headshotCutoutUrl, headshotUrl } from "../src/lib/headshot.ts";
import { LANDING_SECTIONS, safeNext } from "../src/lib/landing.ts";
import {
  MEMBER_HINT_MAX_AGE,
  clearedMemberHintCookie,
  hasMemberHint,
  isMemberRoute,
  memberHintCookie,
  refreshOutcome,
  routeFor,
} from "../src/lib/memberHint.ts";
import { ageOn, matchup, pickSeason, playoffRound } from "../src/lib/player.ts";
import { COLUMNS, columnsFor, formatCount, type StatSource } from "../src/lib/playerStats.ts";
import { profileLabel } from "../src/lib/profiles.ts";

const ZERO_STATS = {
  pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0,
  rush_yd: 0, rush_td: 0, rush_2pt: 0,
  rec: 0, rec_yd: 0, rec_td: 0, rec_2pt: 0,
  fum_lost: 0, ret_td: 0,
};
const NO_USAGE = { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 0 };
const line = (stats: Partial<typeof ZERO_STATS>, usage: Partial<typeof NO_USAGE> = {}): StatSource => ({
  stats: { ...ZERO_STATS, ...stats },
  usage: { ...NO_USAGE, ...usage },
});
const keys = (position: string, rows: StatSource[]) =>
  columnsFor(position, rows).flatMap((g) => g.columns.map((c) => c.key));

describe("profileLabel", () => {
  it("renames the two presets whose stored names mislead", () => {
    assert.equal(profileLabel({ name: "Standard", preset: true }), "0 PPR");
    assert.equal(profileLabel({ name: "Full PPR", preset: true }), "PPR");
    assert.equal(profileLabel({ name: "Half PPR", preset: true }), "Half PPR");
    assert.equal(profileLabel({ name: "TE Premium", preset: true }), "TE Premium");
  });
  it("leaves a user's own ruleset its own name, even one called Standard", () => {
    assert.equal(profileLabel({ name: "Standard", preset: false }), "Standard");
  });
});

describe("headshotUrl", () => {
  it("builds a resized ESPN crop from a digits-only id", () => {
    assert.equal(
      headshotUrl("4429795", 64),
      "https://a.espncdn.com/combiner/i?img=/i/headshots/nfl/players/full/4429795.png&w=64&h=64&scale=crop&cquality=80",
    );
  });
  it("gives no URL for a missing or malformed id, so the monogram shows", () => {
    for (const bad of [null, undefined, "", "4262921.0", "123abc", "../x", "1".repeat(13)]) {
      assert.equal(headshotUrl(bad, 64), null, String(bad));
    }
  });
  it("clamps the requested size", () => {
    assert.match(headshotUrl("1", 9999)!, /w=512&h=512/);
    assert.match(headshotUrl("1", 1)!, /w=16&h=16/);
  });
});

describe("ageOn", () => {
  const on = (iso: string) => new Date(`${iso}T12:00:00`);
  it("counts whole years, turning over on the birthday", () => {
    assert.equal(ageOn("2002-03-20", on("2026-03-19")), 23);
    assert.equal(ageOn("2002-03-20", on("2026-03-20")), 24);
  });
  it("says nothing without a well-formed date", () => {
    assert.equal(ageOn(null, on("2026-01-01")), null);
    assert.equal(ageOn("03/20/2002", on("2026-01-01")), null);
  });
});

describe("pickSeason", () => {
  it("honours an explicit request, even a season he did not play", () => {
    assert.equal(pickSeason(2021, [2025, 2024], 2026), 2021);
  });
  it("opens on the current season when he has games in it", () => {
    assert.equal(pickSeason(null, [2026, 2025], 2026), 2026);
  });
  it("otherwise opens on his latest season, and on nothing when there is none", () => {
    assert.equal(pickSeason(null, [2024, 2023], 2026), 2024);
    assert.equal(pickSeason(null, [], 2026), null);
  });
});

describe("playoffRound and matchup", () => {
  it("names the real nflverse rounds and returns null for the regular season", () => {
    assert.equal(playoffRound("REG"), null);
    assert.equal(playoffRound("WC"), "Wild card");
    assert.equal(playoffRound("SB"), "Super Bowl");
    assert.equal(playoffRound("XYZ"), "XYZ");
  });
  it("reads the venue off home, and never guesses when it is unknown", () => {
    assert.equal(matchup("NO", true), "vs NO");
    assert.equal(matchup("BUF", false), "@ BUF");
    assert.equal(matchup("NYJ", null), "NYJ");
    assert.equal(matchup(null, true), "—");
  });
});

describe("columnsFor", () => {
  it("always shows a position's primary groups, even in a quiet season", () => {
    assert.deepEqual(
      columnsFor("RB", [line({})]).map((g) => g.group),
      ["rushing", "receiving"],
    );
    assert.deepEqual(columnsFor("QB", [line({})]).map((g) => g.group), ["passing", "rushing"]);
  });
  it("adds another group only when some row used it, after the primary ones", () => {
    assert.deepEqual(columnsFor("WR", [line({})]).map((g) => g.group), ["receiving"]);
    assert.deepEqual(
      columnsFor("WR", [line({}), line({}, { rushAtt: 2 })]).map((g) => g.group),
      ["receiving", "rushing"],
    );
  });
  it("shows fumbles, conversions and return scores one at a time, only when they happened", () => {
    assert.ok(!keys("RB", [line({})]).includes("fum_lost"));
    const k = keys("RB", [line({ fum_lost: 1 }), line({ rec_2pt: 1 })]);
    assert.ok(k.includes("fum_lost") && k.includes("two_pt") && !k.includes("ret_td"));
  });
  it("folds the three two-point stats into one count", () => {
    const twoPt = COLUMNS.find((c) => c.key === "two_pt")!;
    assert.equal(twoPt.value(line({ pass_2pt: 1, rush_2pt: 1, rec_2pt: 1 })), 3);
  });
  it("gives an unranked position only what it recorded", () => {
    assert.deepEqual(columnsFor("K", [line({})]), []);
  });
  it("does not mistake a prototype key for a position", () => {
    assert.deepEqual(columnsFor("constructor", [line({})]), []);
    assert.deepEqual(columnsFor("toString", [line({})]), []);
  });
});

describe("formatting", () => {
  it("separates thousands and uses a real minus sign", () => {
    assert.equal(formatCount(1412), "1,412");
    assert.equal(formatCount(-3), "−3");
    assert.equal(formatPoints(-1.6), "−1.6");
  });
});

describe("board URL state", () => {
  const seasons = { first: 2020, current: 2026 };
  it("parses a full board", () => {
    const s = parseBoardParams(
      new URLSearchParams("season=2024&pos=RB&scope=per_game&profileId=3&q=gibbs"),
      seasons,
    );
    assert.deepEqual(s, { season: 2024, position: "RB", scope: "per_game", profileId: 3, q: "gibbs" });
  });
  it("drops anything off its whitelist back to the default", () => {
    const s = parseBoardParams(
      new URLSearchParams("season=1999&pos=K&scope=DROP+TABLE&profileId=-1"),
      seasons,
    );
    assert.deepEqual(s, { season: 2026, position: null, scope: "season", profileId: null, q: "" });
  });
  it("round-trips, and writes nothing for an untouched board", () => {
    const state = { season: 2024, position: "WR" as const, scope: "last4" as const, profileId: 2, q: "a" };
    assert.deepEqual(parseBoardParams(new URLSearchParams(boardSearch(state, 2026)), seasons), state);
    assert.equal(
      boardSearch({ season: 2026, position: null, scope: "season", profileId: null, q: "" }, 2026),
      "",
    );
  });
});

describe("quality bands", () => {
  it("draws the line at a 12-team league's starters", () => {
    assert.equal(band(24, "RB"), "good");
    assert.equal(band(25, "RB"), "mid");
    assert.equal(band(49, "RB"), "poor");
  });
  it("says nothing for a position v1 does not rank, or without a rank", () => {
    assert.equal(bandFor(1, "K"), null);
    assert.equal(bandFor(null, "RB"), null);
    assert.equal(bandFor(1, "constructor"), null);
  });
});

describe("safeNext", () => {
  const ORIGIN = "https://fantasykai.invalid";
  // Whatever comes back must stay on the site when the browser resolves it --
  // the property the function exists for, asked the way the browser asks it.
  const staysHome = (raw: string) => {
    const out = safeNext(raw);
    return out === null || new URL(out, ORIGIN).origin === ORIGIN;
  };

  it("follows a path on this site, query and all", () => {
    assert.equal(safeNext("/rankings?season=2025&position=WR"), "/rankings?season=2025&position=WR");
    assert.equal(safeNext("/players/344"), "/players/344");
  });
  it("refuses anything that leaves the site", () => {
    for (const raw of [
      "//evil.example",
      "//evil.example/rankings",
      "/\\evil.example",
      "/\t/evil.example",
      "/\n/evil.example",
      "https://evil.example",
      "javascript:alert(1)",
      "rankings",
      "",
    ]) {
      assert.equal(safeNext(raw), null, JSON.stringify(raw));
    }
    assert.equal(safeNext(null), null);
  });
  it("never returns a value that resolves off the site, even after normalising", () => {
    for (const raw of [
      "/%2F%2Fevil.example",
      "/%5Cevil.example",
      // Each of these resolves on-site to a path that is itself protocol-relative.
      "/..//evil.example",
      "/.//evil.example",
      "/a/..//evil.example",
      "/./\\evil.example",
    ]) {
      assert.ok(staysHome(raw), JSON.stringify(raw));
    }
  });
  it("does not send a member back to a sign-in page", () => {
    assert.equal(safeNext("/login"), null);
    assert.equal(safeNext("/register?next=/rankings"), null);
  });
});

describe("landing sections", () => {
  it("have unique ids that are safe as a #fragment", () => {
    const ids = LANDING_SECTIONS.map((s) => s.id);
    assert.equal(new Set(ids).size, ids.length);
    for (const id of ids) assert.match(id, /^[a-z][a-z0-9-]*$/);
  });
});

describe("contentSecurityPolicy", () => {
  // One directive's sources, or null when the directive is absent.
  const directive = (policy: string, name: string) =>
    policy.split("; ").map((d) => d.split(" ")).find(([n]) => n === name)?.slice(1) ?? null;
  const prod = contentSecurityPolicy({ nonce: "bm9uY2U=", apiOrigin: "https://api.fantasykai.com", dev: false });
  const dev = contentSecurityPolicy({ nonce: "bm9uY2U=", apiOrigin: "http://localhost:8080", dev: true });

  it("runs a script only with the nonce, or when a nonced script loaded it", () => {
    assert.deepEqual(directive(prod, "script-src"), ["'self'", "'nonce-bm9uY2U='", "'strict-dynamic'"]);
    assert.ok(!prod.includes("'unsafe-eval'"), "production never evals");
  });
  it("keeps the nonce out of style-src, where it would switch 'unsafe-inline' off", () => {
    const style = directive(prod, "style-src")!;
    assert.ok(style.includes("'unsafe-inline'"));
    assert.ok(!style.some((source) => source.startsWith("'nonce-")));
  });
  it("names exactly two hosts off the site: the API and ESPN's images", () => {
    const hosts = new Set(prod.split(/[ ;]+/).filter((token) => /^https?:\/\//.test(token)));
    assert.deepEqual([...hosts].sort(), ["https://a.espncdn.com", "https://api.fantasykai.com"]);
    assert.deepEqual(directive(prod, "connect-src"), ["'self'", "https://api.fantasykai.com"]);
    assert.deepEqual(directive(prod, "img-src"), ["'self'", "data:", "blob:", "https://a.espncdn.com"]);
  });
  it("has no wildcard or scheme-only source anywhere, which would allow every host", () => {
    for (const source of prod.split("; ").flatMap((d) => d.split(" ").slice(1))) {
      assert.ok(!/^(\*|https?:|wss?:)$/.test(source) && !source.includes("*"), source);
    }
  });
  it("cannot be framed, rebased, or post a form off the site", () => {
    assert.deepEqual(directive(prod, "frame-ancestors"), ["'none'"]);
    assert.deepEqual(directive(prod, "base-uri"), ["'none'"]);
    assert.deepEqual(directive(prod, "form-action"), ["'self'"]);
    assert.deepEqual(directive(prod, "object-src"), ["'none'"]);
  });
  it("upgrades insecure requests only when the API itself is https", () => {
    assert.deepEqual(directive(prod, "upgrade-insecure-requests"), []);
    assert.equal(directive(dev, "upgrade-insecure-requests"), null);
  });
  it("gives dev eval and the reload socket, and production neither", () => {
    assert.ok(directive(dev, "script-src")!.includes("'unsafe-eval'"));
    assert.ok(directive(dev, "connect-src")!.includes("ws:"));
    assert.ok(!directive(prod, "connect-src")!.includes("ws:"));
  });
  it("allows only same-origin fetches when there is no API origin (F11's empty URL)", () => {
    const sameOrigin = contentSecurityPolicy({ nonce: "x", apiOrigin: null, dev: false });
    assert.deepEqual(directive(sameOrigin, "connect-src"), ["'self'"]);
  });
});

describe("apiOriginOf", () => {
  it("keeps the origin and drops any path", () => {
    assert.equal(apiOriginOf("https://api.fantasykai.com"), "https://api.fantasykai.com");
    assert.equal(apiOriginOf("https://api.fantasykai.com/some/prefix/"), "https://api.fantasykai.com");
    assert.equal(apiOriginOf("http://localhost:8080"), "http://localhost:8080");
  });
  it("reads an empty base as same-origin, the way lib/api.ts fetches with it", () => {
    assert.equal(apiOriginOf(""), null);
  });
});

describe("member hint", () => {
  it("is read only when it says exactly fk_member=1, wherever it sits", () => {
    assert.equal(hasMemberHint("fk_member=1"), true);
    assert.equal(hasMemberHint("theme=dark; fk_member=1; other=2"), true);
    for (const cookies of ["", "fk_member=0", "fk_member=", "fk_memberx=1", "xfk_member=1", "fk_member=11"]) {
      assert.equal(hasMemberHint(cookies), false, JSON.stringify(cookies));
    }
  });
  it("lives as long as the refresh token, site-wide, Secure over https", () => {
    assert.equal(MEMBER_HINT_MAX_AGE, 30 * 24 * 60 * 60, "refresh-token-ttl: 30d in application.yml");
    const secure = memberHintCookie(true);
    for (const part of ["fk_member=1", "Path=/", `Max-Age=${MEMBER_HINT_MAX_AGE}`, "SameSite=Lax", "Secure"]) {
      assert.ok(secure.split("; ").includes(part), part);
    }
    assert.ok(!memberHintCookie(false).includes("Secure"), "http localhost must still get the hint");
    assert.ok(clearedMemberHintCookie().split("; ").includes("Max-Age=0"));
  });
  it("is retired only by the server's 401, never by a refresh that did not finish", () => {
    assert.equal(refreshOutcome(200), "restored");
    assert.equal(refreshOutcome(401), "rejected");
    // A reload aborting the request, the session bucket's 429, a limiter or
    // server outage: none of them is news about the session.
    for (const status of [null, 429, 500, 502, 503]) assert.equal(refreshOutcome(status), "unavailable", String(status));
  });
  it("sends a member past the landing page, and nobody else", () => {
    assert.equal(routeFor("/", "", true), "/rankings");
    assert.equal(routeFor("/", "", false), null);
  });
  it("sends a never-signed-in visitor to sign in, carrying where they were going", () => {
    const target = routeFor("/rankings", "?season=2025&position=WR", false)!;
    const next = new URL(target, "https://fantasykai.invalid").searchParams.get("next");
    assert.equal(next, "/rankings?season=2025&position=WR");
    assert.equal(safeNext(next), next, "the login page must accept what the proxy hands it");
    assert.equal(routeFor("/players/344", "", false), "/login?next=%2Fplayers%2F344");
  });
  it("leaves members on member routes, and everyone on the site's own pages", () => {
    for (const [path, hinted] of [["/rankings", true], ["/players/344", true], ["/login", false], ["/login", true], ["/register", false], ["/rankingsx", false]] as const) {
      assert.equal(routeFor(path, "", hinted), null, `${path} hinted=${hinted}`);
    }
  });
  it("knows every route under app/(app) -- a new members-only page cannot be forgotten", () => {
    const routes = readdirSync(new URL("../src/app/(app)/", import.meta.url), { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => `/${entry.name}`);
    assert.ok(routes.length >= 3, `found ${routes.join(", ")}`);
    for (const route of routes) assert.ok(isMemberRoute(route), `${route} is behind RequireAccount but not in MEMBER_ROUTES`);
  });
});

describe("headshotCutoutUrl", () => {
  it("asks for the whole cut-out at its own aspect, not a square crop", () => {
    assert.equal(
      headshotCutoutUrl("3117251", 600),
      "https://a.espncdn.com/combiner/i?img=/i/headshots/nfl/players/full/3117251.png&w=600&h=436",
    );
    assert.doesNotMatch(headshotCutoutUrl("3117251", 300)!, /scale=crop/);
    assert.equal(Math.round(300 * CUTOUT_ASPECT), 218);
  });
  it("never asks past the native 600px, which would only upscale", () => {
    assert.match(headshotCutoutUrl("1", 5000)!, /w=600&h=436$/);
    assert.match(headshotCutoutUrl("1", 10)!, /w=120&h=87$/);
  });
  it("gives no URL for a missing or malformed id, so the initials show", () => {
    for (const bad of [null, undefined, "", "4262921.0", "123abc", "../x"]) {
      assert.equal(headshotCutoutUrl(bad, 600), null, String(bad));
    }
  });
});

describe("starterWeeks", () => {
  it("counts the weeks inside the position's 12-team starter line, and no byes", () => {
    assert.equal(starterWeeks([{ posRank: 24 }, { posRank: 25 }, { posRank: null }, { posRank: 1 }], "RB"), 2);
    assert.equal(starterWeeks([{ posRank: 12 }, { posRank: 13 }], "QB"), 1);
    assert.equal(starterWeeks([], "WR"), 0);
  });
});

describe("the landing hero's captured seasons", () => {
  it("each ruleset's weeks add up to its season total", () => {
    for (const s of MCCAFFREY_2025_BY_RULESET) {
      const sum = s.weeks.reduce((n, w) => n + w.points, 0);
      assert.ok(Math.abs(sum - s.points) < 0.05, `${s.ruleset}: weeks sum to ${sum}, season says ${s.points}`);
      assert.equal(s.weeks.length, s.gamesPlayed, s.ruleset);
    }
  });

  it("agrees with the other captures of the same season", () => {
    const [zero, half, ppr] = MCCAFFREY_2025_BY_RULESET;
    assert.deepEqual(
      ppr.weeks,
      MCCAFFREY_2025_PPR.weeks.map(({ week, points, posRank }) => ({ week, points, posRank })),
    );
    assert.equal(ppr.points, PPR_2025.find((r) => r.playerId === 14480)!.points);
    // The chip's "RB1" is the board's own derivation over the captured rows,
    // not a number restated by hand.
    assert.equal(ppr.posRank, toBoardRows(PPR_2025).find((r) => r.playerId === 14480)!.posRank);
    assert.equal(half.points, 365.6);
    assert.equal(zero.overallRank, PPR_VS_ZERO_2025.find((p) => p.row.playerId === 14480)!.zeroPprRank);
  });

  it("scores the same stat lines three ways: only the value of a catch differs", () => {
    // 102 catches: 0 PPR + 51 = Half PPR, + 51 again = PPR.
    const [zero, half, ppr] = MCCAFFREY_2025_BY_RULESET.map((s) => s.points);
    assert.ok(Math.abs(half - zero - 51) < 0.05 && Math.abs(ppr - half - 51) < 0.05);
    assert.deepEqual(
      MCCAFFREY_2025_BY_RULESET.map((s) => starterWeeks(s.weeks, "RB")),
      [15, 16, 16],
    );
  });
});

describe("the landing page's other captures", () => {
  it("draws the PPR board from rank 1 with no gaps, the order tiers need", () => {
    assert.equal(PPR_2025.length, 60);
    PPR_2025.forEach((r, i) => assert.equal(r.rank, i + 1, r.name));
    // The rule-change chart's six are the top of this same board.
    for (const { row } of PPR_VS_ZERO_2025) {
      assert.deepEqual(PPR_2025[row.rank - 1], row, row.name);
    }
  });

  it("gives every sliced row a 0 PPR place, and agrees with the rule-change chart", () => {
    // The product band shows ranks 1 to 12 (ProductBand.tsx); every one needs its real 0 PPR place.
    for (const r of PPR_2025.slice(0, 16)) assert.ok(PPR_FROM_ZERO_2025[r.playerId] > 0, r.name);
    for (const { row, zeroPprRank } of PPR_VS_ZERO_2025) {
      assert.equal(PPR_FROM_ZERO_2025[row.playerId], zeroPprRank, row.name);
    }
  });

  it("prices the receipt from the preset's rates, and it adds up to the API's total", () => {
    // previewData throws on import if it does not, so this file failing to
    // load is the same failure; this names it.
    const { lines, total } = MCCAFFREY_2025_PPR_RECEIPT;
    assert.ok(Math.abs(total - MCCAFFREY_2025_PPR.points) < 0.05);
    const rec = lines.find((l) => l.stat === "rec")!;
    assert.equal(rec.count * rec.rate, rec.points);
    assert.equal(rec.rate, 1, "PPR is a point per catch");
  });

  it("keeps Nacua's weeks and his season total in step, and the board's", () => {
    const sum = NACUA_2025_PPR.weeks.reduce((n, w) => n + w.points, 0);
    assert.ok(Math.abs(sum - NACUA_2025_PPR.points) < 0.05);
    assert.equal(NACUA_2025_PPR.weeks.length, NACUA_2025_PPR.gamesPlayed);
    assert.equal(NACUA_2025_PPR.points, PPR_2025.find((r) => r.playerId === 16153)!.points);
  });
});

describe("the landing's one game, and the rates it is priced with", () => {
  const RULESETS = Object.keys(PRESET_RATES) as Ruleset[];

  it("copies V3's three presets exactly, and they differ in one rate: a catch", () => {
    // The migration is the source; parse its rule objects rather than trusting the copy.
    const sql = readFileSync(
      new URL("../../backend/src/main/resources/db/migration/V3__seed_scoring_presets.sql", import.meta.url),
      "utf8",
    );
    const seeded = new Map(
      [...sql.matchAll(/\(NULL, '([^']+)', TRUE, '(\{[\s\S]*?\})'::jsonb\)/g)].map((m) => [m[1], JSON.parse(m[2])]),
    );
    const source: Record<Ruleset, string> = { "0 PPR": "Standard", "Half PPR": "Half PPR", PPR: "Full PPR" };
    for (const r of RULESETS) {
      const rules = seeded.get(source[r]);
      assert.ok(rules, `${source[r]} is not in V3`);
      assert.equal(rules.position_overrides, undefined, `${source[r]} has overrides`);
      assert.deepEqual(PRESET_RATES[r], rules.base, r);
    }
    for (const stat of Object.keys(PRESET_RATES.PPR) as (keyof (typeof PRESET_RATES)["PPR"])[]) {
      const values = new Set(RULESETS.map((r) => PRESET_RATES[r][stat]));
      assert.equal(values.size, stat === "rec" ? 3 : 1, stat);
    }
  });

  it("prices week 7 into each preset's captured points, which are the seasons' own week 7", () => {
    for (const s of MCCAFFREY_2025_BY_RULESET) {
      const week = s.weeks.find((w) => w.week === MCCAFFREY_2025_WEEK7.week)!;
      assert.equal(MCCAFFREY_2025_WEEK7.points[s.ruleset], week.points, s.ruleset);
      const { total } = MCCAFFREY_2025_WEEK7_RECEIPTS[s.ruleset];
      assert.ok(Math.abs(total - week.points) < 0.05, `${s.ruleset}: priced ${total}, captured ${week.points}`);
    }
    // Seven catches: 0 PPR + 3.5 = Half PPR, + 3.5 again = PPR.
    assert.equal(MCCAFFREY_2025_WEEK7.stats.rec, 7);
  });

  it("splits week 7 into what the rate does not touch, plus its catches times the rate", () => {
    const base = MCCAFFREY_2025_WEEK7.points["0 PPR"];
    for (const r of RULESETS) {
      const { lines } = MCCAFFREY_2025_WEEK7_RECEIPTS[r];
      const rec = lines.find((l) => l.stat === "rec")!;
      assert.equal(rec.count, MCCAFFREY_2025_WEEK7.stats.rec, r);
      assert.equal(rec.rate, PRESET_RATES[r].rec, r);
      // The equation the page prints: 32.1 + 7 x rate = this preset's captured week.
      assert.ok(Math.abs(base + rec.count * rec.rate - MCCAFFREY_2025_WEEK7.points[r]) < 1e-9, r);
      // ...and the 32.1 is the receipt's other lines, which no preset prices differently.
      const fixed = lines.filter((l) => l.stat !== "rec").reduce((n, l) => n + l.points, 0);
      assert.ok(Math.abs(fixed - base) < 1e-9, r);
    }
  });

  it("caps every week with what its catches add, and the caps add up to the season's difference", () => {
    const [zero, half, ppr] = MCCAFFREY_2025_BY_RULESET;
    const full = catchCaps(zero.weeks, ppr.weeks);
    const halves = catchCaps(zero.weeks, half.weeks);
    full.forEach((w, i) => {
      // A point a catch makes the cap a whole number of catches...
      assert.ok(w.cap >= 0 && Math.abs(w.cap - Math.round(w.cap)) < 1e-9, `week ${w.week}: ${w.cap}`);
      // ...and the captures agree that only a catch's value differs: half the rate, half the cap.
      assert.ok(Math.abs(w.cap - 2 * halves[i].cap) < 1e-9, `week ${w.week}`);
      assert.equal(w.base, zero.weeks[i].points);
    });
    assert.ok(Math.abs(full.find((w) => w.week === MCCAFFREY_2025_WEEK7.week)!.cap - 7) < 1e-9);
    const sum = (caps: { cap: number }[]) => caps.reduce((n, w) => n + w.cap, 0);
    assert.ok(Math.abs(sum(full) - (ppr.points - zero.points)) < 0.05, "PPR: 102.0 from catches");
    assert.ok(Math.abs(sum(halves) - (half.points - zero.points)) < 0.05, "Half PPR: 51.0");
    assert.ok(catchCaps(zero.weeks, zero.weeks).every((w) => w.cap === 0));
  });

  it("refuses two seasons that disagree on which weeks were played", () => {
    const [zero, , ppr] = MCCAFFREY_2025_BY_RULESET;
    assert.throws(() => catchCaps(zero.weeks, ppr.weeks.slice(1)));
    assert.throws(() =>
      catchCaps(zero.weeks, ppr.weeks.map((w) => (w.week === 18 ? { ...w, week: 14 } : w))),
    );
  });

  it("labels a week a bye only when it is the bye: McCaffrey's one missing week is the 49ers'", () => {
    // The week strip prints "bye" for this week alone and "no game" for any other gap,
    // so a game missed for another reason can never read as a bye.
    for (const s of [...MCCAFFREY_2025_BY_RULESET, MCCAFFREY_2025_PPR]) {
      const played = new Set(s.weeks.map((w) => w.week));
      const missing = Array.from({ length: 18 }, (_, i) => i + 1).filter((w) => !played.has(w));
      assert.deepEqual(missing, [SF_2025_BYE_WEEK], "season" in s ? "career" : s.ruleset);
    }
  });
});

describe("the landing's wide-receiver board", () => {
  it("is a whole WR board from rank 1, sixty deep, the order tiers need", () => {
    assert.equal(WR_PPR_2025.length, 60);
    WR_PPR_2025.forEach((r, i) => {
      assert.equal(r.rank, i + 1, r.name);
      assert.equal(r.position, "WR", r.name);
    });
  });

  it("agrees with the overall PPR board on every receiver both hold, in the same order", () => {
    const both = PPR_2025.filter((r) => r.position === "WR");
    assert.ok(both.length >= 10);
    let last = 0;
    for (const r of both) {
      const w = WR_PPR_2025.find((x) => x.playerId === r.playerId);
      assert.ok(w, `${r.name} is on the overall board but not the WR board`);
      assert.deepEqual([w.points, w.pointsPerGame, w.gamesPlayed], [r.points, r.pointsPerGame, r.gamesPlayed], r.name);
      assert.ok(w.rank > last, `${r.name} is out of order`);
      last = w.rank;
    }
  });

  it("gives every shown receiver his real 0 PPR place, in step with the overall 0 PPR board", () => {
    // The band shows ranks 1 to 10 (ProductBand.tsx); sixteen are captured.
    for (const r of WR_PPR_2025.slice(0, 16)) assert.ok(WR_PPR_FROM_ZERO_2025[r.playerId] > 0, r.name);
    const overall = Object.entries(PPR_FROM_ZERO_2025)
      .map(([id, rank]) => ({ id: Number(id), rank }))
      .filter((p) => WR_PPR_FROM_ZERO_2025[p.id] !== undefined)
      .sort((a, b) => a.rank - b.rank);
    assert.ok(overall.length >= 4);
    const wr = overall.map((p) => WR_PPR_FROM_ZERO_2025[p.id]);
    assert.deepEqual(wr, [...wr].sort((a, b) => a - b), "the two 0 PPR boards disagree on order");
  });
});

describe("the landing's three boards", () => {
  const BOARDS = {
    "0 PPR": ZERO_PPR_TOP20_2025,
    "Half PPR": HALF_PPR_TOP20_2025,
    PPR: PPR_2025.slice(0, 20),
  } as const;
  const RULESETS = Object.keys(BOARDS) as (keyof typeof BOARDS)[];
  const byId = (id: number) => HERO_BOARD_2025.find((p) => p.playerId === id)!;

  it("runs every board from 1st to 20th, no gaps, best first", () => {
    for (const r of RULESETS) {
      assert.deepEqual(
        BOARDS[r].map((row) => row.rank),
        Array.from({ length: 20 }, (_, i) => i + 1),
        r,
      );
      BOARDS[r].slice(1).forEach((row, i) => assert.ok(row.points <= BOARDS[r][i].points, `${r}: ${row.name}`));
      assert.equal(new Set(BOARDS[r].map((row) => row.playerId)).size, 20, `${r}: a player twice`);
    }
  });

  it("agrees with every other capture of the same three boards", () => {
    for (const r of RULESETS) {
      for (const p of HERO_BOARD_2025.filter((q) => q.by[r].rank <= 20)) {
        const row = BOARDS[r][p.by[r].rank - 1];
        assert.equal(row.playerId, p.playerId, `${r}: ${p.name}`);
        assert.equal(row.points, p.by[r].points, `${r}: ${p.name}`);
      }
    }
    for (const row of ZERO_PPR_TOP20_2025) {
      const zero = PPR_FROM_ZERO_2025[row.playerId];
      if (zero !== undefined) assert.equal(row.rank, zero, row.name);
    }
  });

  it("follows Nacua from 20th to 11th to 2nd -- the depth each board runs to", () => {
    const nacua = byId(16153);
    assert.deepEqual(
      RULESETS.map((r) => nacua.by[r].rank),
      [20, 11, 2],
    );
    assert.deepEqual(
      RULESETS.map((r) => nacua.by[r].points),
      [246.0, 310.5, 375.0],
    );
    assert.equal(Math.max(...RULESETS.map((r) => nacua.by[r].rank)), BOARDS["0 PPR"].length);
  });

  it("tells the quarterbacks' story in its letters: all of the 0 PPR top five, one of the PPR's", () => {
    const qbs = (r: (typeof RULESETS)[number]) => BOARDS[r].slice(0, 5).filter((p) => p.position === "QB").length;
    assert.equal(qbs("0 PPR"), 5);
    assert.equal(qbs("PPR"), 1);
  });
});

describe("the two modes' tokens (globals.css)", () => {
  const css = readFileSync(new URL("../src/app/globals.css", import.meta.url), "utf8");
  // The declarations inside the first block that opens with `selector {`.
  const block = (selector: string) => {
    const start = css.indexOf(`${selector} {`);
    assert.ok(start >= 0, `${selector} is missing from globals.css`);
    const body = css.slice(start, css.indexOf("\n}", start));
    const vars = new Map<string, string>();
    for (const m of body.matchAll(/^\s*(--(?:color|shadow)-[\w-]+):\s*([^;]+);/gm)) vars.set(m[1], m[2].trim());
    return vars;
  };
  const theme = block("@theme");
  const prime = block(".primetime");
  const day = block(".daylight");
  // Fills Daylight shares with Prime time on purpose: the orange button and the
  // ink on it read the same on both grounds (8.3:1).
  const SHARED = new Set(["--color-ki", "--color-on-ki"]);

  it("puts the product's own values back inside .primetime, every one of them", () => {
    assert.ok(theme.size > 20, "the @theme block did not parse");
    for (const [name, value] of theme) assert.equal(prime.get(name), value, name);
    for (const name of prime.keys()) assert.ok(theme.has(name), `${name} is in .primetime but not @theme`);
  });

  it("gives Daylight its own value for every colour except the shared fills", () => {
    for (const name of theme.keys()) {
      if (SHARED.has(name)) assert.ok(!day.has(name), `${name} is shared and should not be overridden`);
      else assert.ok(day.has(name), `${name} has no Daylight value`);
    }
  });

  it("paints what no component draws with Daylight's canvas", () => {
    const html = css.slice(css.indexOf("html:has(.daylight) {"));
    assert.match(html, new RegExp(`background: ${day.get("--color-canvas")};`));
  });
});

describe("the landing hero's board", () => {
  const RULESETS = ["0 PPR", "Half PPR", "PPR"] as const;
  const byId = (id: number) => HERO_BOARD_2025.find((p) => p.playerId === id)!;
  const surname = (p: { name: string }) => p.name.split(" ").slice(1).join(" ");
  const order = (r: (typeof RULESETS)[number]) =>
    HERO_BOARD_2025.filter((p) => p.by[r].rank <= 8)
      .sort((a, b) => a.by[r].rank - b.by[r].rank)
      .map(surname);
  const cmc = byId(14480);
  const slotted = (r: (typeof RULESETS)[number]) =>
    HERO_BOARD_2025.filter((p) => heroSlotOf(p, r, cmc) !== null)
      .sort((a, b) => heroSlotOf(a, r, cmc)! - heroSlotOf(b, r, cmc)!)
      .map((p) => [surname(p), p.by[r].rank]);

  it("holds every ruleset's whole top 8, ranks 1 to 8 with no gaps or repeats", () => {
    for (const r of RULESETS) {
      const ranks = HERO_BOARD_2025.map((p) => p.by[r].rank);
      assert.equal(new Set(ranks).size, ranks.length, `${r}: a rank repeats`);
      for (let n = 1; n <= 8; n++) assert.ok(ranks.includes(n), `${r}: nobody at ${n}`);
    }
    assert.deepEqual(order("0 PPR"), ["Allen", "Maye", "Stafford", "Lawrence", "Williams", "Taylor", "McCaffrey", "Prescott"]);
    assert.deepEqual(order("PPR"), ["McCaffrey", "Nacua", "Robinson", "Gibbs", "Allen", "Taylor", "Smith-Njigba", "Maye"]);
  });

  it("agrees with the other captures of the same three boards", () => {
    for (const p of HERO_BOARD_2025) {
      assert.equal(HERO_BOARD_2025.filter((q) => q.playerId === p.playerId).length, 1, p.name);
      const ppr = PPR_2025[p.by.PPR.rank - 1];
      assert.equal(ppr.playerId, p.playerId, p.name);
      assert.equal(ppr.points, p.by.PPR.points, p.name);
      assert.equal(ppr.pointsPerGame, p.by.PPR.pointsPerGame, p.name);
      assert.equal(ppr.gamesPlayed, p.gamesPlayed, p.name);
      const zero = PPR_FROM_ZERO_2025[p.playerId];
      if (zero !== undefined) assert.equal(p.by["0 PPR"].rank, zero, p.name);
    }
    const cmc = byId(14480);
    for (const s of MCCAFFREY_2025_BY_RULESET) {
      assert.equal(cmc.by[s.ruleset].rank, s.overallRank, s.ruleset);
      assert.equal(cmc.by[s.ruleset].points, s.points, s.ruleset);
      assert.equal(cmc.by[s.ruleset].pointsPerGame, s.pointsPerGame, s.ruleset);
    }
  });

  it("derives the positional ranks the board would, and McCaffrey's the career says", () => {
    const board = toBoardRows(PPR_2025);
    for (const p of HERO_BOARD_2025.filter((q) => q.by.PPR.rank <= 8)) {
      assert.equal(posRankOf(HERO_BOARD_2025, p, "PPR"), board[p.by.PPR.rank - 1].posRank, p.name);
    }
    for (const s of MCCAFFREY_2025_BY_RULESET) {
      assert.equal(posRankOf(HERO_BOARD_2025, byId(14480), s.ruleset), s.posRank, s.ruleset);
    }
  });

  it("says nothing, not a wrong number, when someone ranked above is missing", () => {
    const withoutAllen = HERO_BOARD_2025.filter((p) => p.playerId !== 344);
    assert.equal(posRankOf(withoutAllen, byId(14406), "PPR"), null);
    // Nacua is 20th under 0 PPR, and most of the nineteen above him are not in the set.
    assert.equal(posRankOf(HERO_BOARD_2025, byId(16153), "0 PPR"), null);
  });

  it("measures a move as the real distance, from off the board included", () => {
    assert.equal(movedBetween(byId(16153), "0 PPR", "PPR"), 18);
    assert.equal(movedBetween(byId(14480), "0 PPR", "PPR"), 6);
    assert.equal(movedBetween(byId(344), "0 PPR", "PPR"), -4);
    assert.equal(movedBetween(byId(21702), "0 PPR", "PPR"), 0);
  });

  it("shows the top five, and keeps McCaffrey on the board when he is outside them", () => {
    assert.deepEqual(slotted("0 PPR"), [
      ["Allen", 1],
      ["Maye", 2],
      ["Stafford", 3],
      ["Lawrence", 4],
      ["Williams", 5],
      ["McCaffrey", 7],
    ]);
    assert.deepEqual(slotted("PPR"), [
      ["McCaffrey", 1],
      ["Nacua", 2],
      ["Robinson", 3],
      ["Gibbs", 4],
      ["Allen", 5],
      ["Taylor", 6],
    ]);
    assert.deepEqual(
      slotted("Half PPR").map(([, rank]) => rank),
      [1, 2, 3, 4, 5, 6],
    );
    for (const r of RULESETS) {
      const slots = HERO_BOARD_2025.map((p) => heroSlotOf(p, r, cmc)).filter((x) => x !== null);
      assert.deepEqual(
        slots.sort((a, b) => a - b),
        Array.from({ length: HERO_SLOTS }, (_, i) => i),
        `${r}: every slot filled once`,
      );
    }
    // The dashed rule: only where the last slot jumps a place to reach him.
    assert.deepEqual(
      RULESETS.map((r) => heroSkips(cmc, r)),
      [true, false, false],
    );
    // The boundary: a followed player who is exactly sixth takes the last slot with nothing skipped.
    const taylor = byId(21702);
    assert.equal(taylor.by["0 PPR"].rank, 6);
    assert.equal(heroSkips(taylor, "0 PPR"), false);
    assert.equal(heroSlotOf(taylor, "0 PPR", taylor), HERO_SLOTS - 1);
  });

  it("starts the entrance from the 0 PPR board: two stay, four arrive, four leave", () => {
    const e = (id: number) =>
      entranceOf(heroSlotOf(byId(id), "0 PPR", cmc), heroSlotOf(byId(id), "PPR", cmc), HERO_SLOTS);
    assert.deepEqual(e(14480), { rows: 5, shown: true }); // the pinned slot -> 1st: starts five rows lower
    assert.deepEqual(e(344), { rows: -4, shown: true }); // 1st -> 5th
    assert.deepEqual(e(16153), { rows: 5, shown: false }); // off the board -> 2nd, rising from below
    assert.deepEqual(e(14406), { rows: -5, shown: true }); // 2nd -> off the board, sinking out
    const arrive = HERO_BOARD_2025.filter((p) => !e(p.playerId).shown && heroSlotOf(p, "PPR", cmc) !== null);
    const leave = HERO_BOARD_2025.filter((p) => e(p.playerId).shown && heroSlotOf(p, "PPR", cmc) === null);
    assert.deepEqual(arrive.map(surname), ["Taylor", "Robinson", "Gibbs", "Nacua"]);
    assert.deepEqual(leave.map(surname), ["Maye", "Stafford", "Lawrence", "Williams"]);
  });

  it("keeps Nacua's career line to his 2025 capture, and to finished seasons only", () => {
    const last = NACUA_SEASONS_PPR[NACUA_SEASONS_PPR.length - 1];
    assert.equal(last.season, NACUA_2025_PPR.season);
    assert.equal(last.points, NACUA_2025_PPR.points);
    assert.equal(last.pointsPerGame, NACUA_2025_PPR.pointsPerGame);
    assert.equal(last.gamesPlayed, NACUA_2025_PPR.gamesPlayed);
    assert.equal(last.posRank, NACUA_2025_PPR.posRank);
    assert.deepEqual(NACUA_SEASONS_PPR.map((s) => s.season), [2023, 2024, 2025]);
  });
});
