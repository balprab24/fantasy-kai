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
  HERO_BOARDS_2026,
  HERO_LEAGUE_DETAIL,
  HERO_PROFILES,
  HERO_RULESETS,
  HERO_SEASON,
  HERO_THROUGH_WEEK,
  HERO_TOTAL,
} from "../src/components/landing/heroData.ts";
import {
  HALF_PPR_TOP20_2025,
  MY_LEAGUE_RATES,
  NACUA_2025_BY_RULESET,
  NACUA_2025_LOG,
  NACUA_2025_LOG_RECEIPTS,
  NACUA_LIT_WEEK,
  ONE_BOARD_2025,
  PPR_2025,
  PPR_FROM_ZERO_2025,
  PRESET_RATES,
  RULESETS,
  ZERO_PPR_TOP20_2025,
  type Ruleset,
} from "../src/components/landing/previewData.ts";
import { assignTiers, band, bandFor, formatPoints, starterWeeks, toBoardRows } from "../src/lib/board.ts";
import { boardSearch, parseBoardParams } from "../src/lib/boardParams.ts";
import { apiOriginOf, contentSecurityPolicy } from "../src/lib/csp.ts";
import { lastPlace, movedBetween, skipsTo, slotOf } from "../src/lib/oneBoard.ts";
import { HERO_BEATS, HERO_LOOP_MS, HERO_MAX_LOOPS, HERO_POSTER, boardLayout, castOf, rulesetAfter } from "../src/lib/heroDemo.ts";
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

describe("text colours are used at full strength", () => {
  // Every text pair in globals.css and DESIGN.md is measured at the token's own
  // value. An opacity modifier on a text colour (`text-faint/50`) is a new,
  // unmeasured colour: the weekly chart's no-game weeks were 2.08:1 that way.
  const css = readFileSync(new URL("../src/app/globals.css", import.meta.url), "utf8");
  const colours = [...new Set([...css.matchAll(/--color-([\w-]+):/g)].map((m) => m[1]))];
  const src = new URL("../src/", import.meta.url);
  const files = readdirSync(src, { recursive: true, encoding: "utf8" }).filter((f) => /\.tsx?$/.test(f));

  it("finds the colours and the sources it checks", () => {
    assert.ok(colours.includes("faint") && colours.includes("ink"), "no colour tokens parsed from globals.css");
    assert.ok(files.some((f) => f.endsWith("WeeklyChart.tsx")), "no component sources found");
  });

  it("never fades a text colour with an opacity modifier", () => {
    const faded = new RegExp(`(?<![\\w-])text-(?:${colours.join("|")})/\\d+`, "g");
    const hits = files.flatMap((f) =>
      [...readFileSync(new URL(f, src), "utf8").matchAll(faded)].map((m) => `${f}: ${m[0]}`),
    );
    assert.deepEqual(hits, []);
  });
});

describe("the landing's captured boards", () => {
  it("draws the 2025 PPR board from rank 1 with no gaps, the order tiers need", () => {
    assert.equal(PPR_2025.length, 60);
    PPR_2025.forEach((r, i) => assert.equal(r.rank, i + 1, r.name));
  });

  it("gives the top of the PPR board its real 0 PPR places", () => {
    for (const r of PPR_2025.slice(0, 16)) assert.ok(PPR_FROM_ZERO_2025[r.playerId] > 0, r.name);
  });

  it("shows no likeness: every captured row's ESPN id is blanked", () => {
    // The board would draw a headshot from it; the landing draws none (owner decision 2026-09-28).
    const rows = [
      ...PPR_2025,
      ...ZERO_PPR_TOP20_2025,
      ...HALF_PPR_TOP20_2025,
      ...HERO_RULESETS.flatMap((r) => HERO_BOARDS_2026[r]),
    ];
    for (const r of rows) assert.equal(r.espnId, null, r.name);
  });
});

describe("the hero's 2026 boards", () => {
  const TOP = 10;
  const cast = castOf(
    HERO_RULESETS.map((r) => HERO_BOARDS_2026[r]),
    TOP,
  );
  const rankIn = (r: (typeof HERO_RULESETS)[number], id: number) => HERO_BOARDS_2026[r].find((x) => x.playerId === id)?.rank;
  const name = (n: string) => HERO_BOARDS_2026.PPR.find((r) => r.name === n)!.playerId;

  it("are four whole boards from rank 1, sixty deep, best first, the order tiers need", () => {
    for (const r of HERO_RULESETS) {
      const board = HERO_BOARDS_2026[r];
      assert.equal(board.length, 60, r);
      board.forEach((row, i) => assert.equal(row.rank, i + 1, `${r}: ${row.name}`));
      board.slice(1).forEach((row, i) => assert.ok(row.points <= board[i].points, `${r}: ${row.name}`));
      assert.ok(HERO_TOTAL >= board.length);
    }
  });

  it("place everyone the hero can show inside every board's sixty, so a move is always the real distance", () => {
    for (const id of cast) for (const r of HERO_RULESETS) assert.ok(rankIn(r, id)! <= 60, `${id} under ${r}`);
    assert.equal(cast.length, 14);
  });

  it("offer exactly the scorings captured, in the switch's order, named as the console names them", () => {
    assert.deepEqual(
      HERO_PROFILES.map((p) => p.ruleset),
      [...HERO_RULESETS],
    );
    assert.deepEqual(HERO_PROFILES.map(profileLabel), ["0 PPR", "Half PPR", "PPR", "My league"]);
  });

  it("score the same games four ways: the same games played, and a catch's rate the only preset difference", () => {
    for (const row of HERO_BOARDS_2026.PPR.slice(0, 20)) {
      const zero = HERO_BOARDS_2026["0 PPR"].find((z) => z.playerId === row.playerId);
      const half = HERO_BOARDS_2026["Half PPR"].find((h) => h.playerId === row.playerId);
      if (!zero || !half) continue;
      assert.equal(zero.gamesPlayed, row.gamesPlayed, row.name);
      // Half PPR sits exactly halfway between 0 PPR and PPR: half a point a catch.
      assert.ok(Math.abs(half.points - (zero.points + row.points) / 2) < 0.011, row.name);
    }
  });

  it("say the week they run through from the rows themselves", () => {
    assert.equal(HERO_SEASON, 2026);
    assert.equal(HERO_THROUGH_WEEK, Math.max(...HERO_BOARDS_2026.PPR.map((r) => r.gamesPlayed)));
    for (const r of HERO_RULESETS) for (const row of HERO_BOARDS_2026[r]) assert.ok(row.gamesPlayed <= HERO_THROUGH_WEEK);
    assert.equal(HERO_THROUGH_WEEK, 4);
  });

  it("carry Smith-Njigba from 9th under 0 PPR to 1st under PPR, and Lamb from 17th to 4th", () => {
    assert.deepEqual([rankIn("0 PPR", name("Jaxon Smith-Njigba")), rankIn("PPR", name("Jaxon Smith-Njigba"))], [9, 1]);
    assert.deepEqual([rankIn("0 PPR", name("CeeDee Lamb")), rankIn("PPR", name("CeeDee Lamb"))], [17, 4]);
  });
});

describe("the hero's board layout", () => {
  const TOP = 10;
  const cast = castOf(
    HERO_RULESETS.map((r) => HERO_BOARDS_2026[r]),
    TOP,
  );
  const tiersOf = (board: (typeof HERO_BOARDS_2026)["PPR"]) =>
    assignTiers(toBoardRows(board)).tiers.map((t) => ({ letter: t.letter, first: t.rows[0].rank }));
  const layout = Object.fromEntries(
    HERO_RULESETS.map((r) => [r, boardLayout(HERO_BOARDS_2026[r], tiersOf(HERO_BOARDS_2026[r]), cast, TOP)]),
  ) as Record<(typeof HERO_RULESETS)[number], ReturnType<typeof boardLayout>>;
  const id = (n: string) => HERO_BOARDS_2026.PPR.find((r) => r.name === n)!.playerId;

  it("opens tier S at the top, and moves the A divider with the S tier's size", () => {
    for (const r of HERO_RULESETS) assert.deepEqual(layout[r].dividers.get("S"), { rows: 0, dividers: 0, shown: true }, r);
    assert.deepEqual(layout["0 PPR"].dividers.get("A"), { rows: 6, dividers: 1, shown: true });
    assert.deepEqual(layout.PPR.dividers.get("A"), { rows: 7, dividers: 1, shown: true });
  });

  it("carries Smith-Njigba from 9th, under the A divider, to 1st", () => {
    assert.deepEqual(layout["0 PPR"].rows.get(id("Jaxon Smith-Njigba")), { rows: 8, dividers: 2, shown: true });
    assert.deepEqual(layout.PPR.rows.get(id("Jaxon Smith-Njigba")), { rows: 0, dividers: 1, shown: true });
  });

  it("fills every place in each board's top ten once, and parks the rest just below them", () => {
    for (const r of HERO_RULESETS) {
      const shown = [...layout[r].rows.values()].filter((p) => p.shown).map((p) => p.rows);
      assert.deepEqual(
        shown.sort((a, b) => a - b),
        Array.from({ length: TOP }, (_, i) => i),
        r,
      );
      for (const p of layout[r].rows.values()) if (!p.shown) assert.equal(p.rows, TOP, r);
    }
    for (const n of ["CeeDee Lamb", "Amon-Ra St. Brown"]) {
      assert.equal(layout["0 PPR"].rows.get(id(n))!.shown, false, n);
      assert.equal(layout.PPR.rows.get(id(n))!.shown, true, n);
    }
  });

  it("casts everyone in any board's top, down to its last place, and no one else", () => {
    // Synthetic, because on the real boards the top nines happen to cover the top tens.
    const a = [{ playerId: 1, rank: 1 }, { playerId: 2, rank: 2 }, { playerId: 3, rank: 3 }];
    const b = [{ playerId: 2, rank: 1 }, { playerId: 4, rank: 2 }, { playerId: 1, rank: 3 }];
    assert.deepEqual(castOf([a, b], 2).sort(), [1, 2, 4]);
    for (const r of HERO_RULESETS) {
      for (const row of HERO_BOARDS_2026[r].filter((x) => x.rank <= TOP)) assert.ok(cast.includes(row.playerId), row.name);
    }
  });
});

describe("the hero's loop", () => {
  const rank = (r: (typeof HERO_RULESETS)[number], n: string) => HERO_BOARDS_2026[r].find((x) => x.name === n)!.rank;

  it("opens and rests on My league, the board the server renders and reduced motion keeps", () => {
    assert.equal(HERO_POSTER, "My league");
    assert.equal(HERO_BEATS[0].beat, "poster");
    assert.equal(HERO_BEATS[0].at, 0);
    assert.equal(HERO_BEATS[0].picks, null);
    assert.equal(rulesetAfter(HERO_BEATS.length - 1), "My league");
  });

  it("switches to PPR, the baseline, and back to My league -- and nothing else", () => {
    assert.deepEqual(
      HERO_BEATS.filter((s) => s.picks).map((s) => s.picks),
      ["PPR", "My league"],
    );
    assert.equal(rulesetAfter(HERO_BEATS.findIndex((s) => s.picks === "PPR")), "PPR");
  });

  it("holds the board it lands on long enough to read, and stops after a few loops", () => {
    HERO_BEATS.slice(1).forEach((b, i) => assert.ok(b.at > HERO_BEATS[i].at, b.beat));
    const last = Math.max(...HERO_BEATS.filter((s) => s.picks).map((s) => s.at));
    // The rows travel for 850ms; what is left of the loop is the hold.
    assert.ok(HERO_LOOP_MS - last - 850 >= 3000, "the hold on My league is under three seconds");
    assert.ok(HERO_MAX_LOOPS >= 1 && HERO_MAX_LOOPS <= 3);
  });

  it("moves the story's three rows the way the copy says, counted from PPR", () => {
    // Δ vs PPR under My league: positive is up the board.
    const delta = (n: string) => rank("PPR", n) - rank("My league", n);
    assert.deepEqual(
      ["Josh Allen", "Brock Purdy", "Jaxon Smith-Njigba"].map((n) => [rank("PPR", n), rank("My league", n), delta(n)]),
      [
        [3, 1, 2],
        [7, 2, 5],
        [1, 10, -9],
      ],
    );
  });

  it("says what My league is, and the words are the rules it was saved with", () => {
    // Half PPR (a catch at 0.5) with six points a passing touchdown, everything else V3's.
    assert.equal(HERO_LEAGUE_DETAIL, `Half PPR · ${MY_LEAGUE_RATES.pass_td}-pt passing TDs`);
    assert.equal(MY_LEAGUE_RATES.rec, PRESET_RATES["Half PPR"].rec);
    assert.equal(MY_LEAGUE_RATES.pass_td, 6);
  });
});

describe("Nacua's 2025 season, scored three ways", () => {
  it("adds each preset's weeks up to its season total", () => {
    for (const r of RULESETS) {
      const s = NACUA_2025_BY_RULESET[r];
      const sum = s.weeks.reduce((n, w) => n + w.points, 0);
      assert.ok(Math.abs(sum - s.points) < 0.05, `${r}: weeks sum to ${sum}, season says ${s.points}`);
      assert.equal(s.weeks.length, s.gamesPlayed, r);
    }
  });

  it("agrees with the boards: the same points, and WR1 by the board's own count", () => {
    const nacua = ONE_BOARD_2025.find((p) => p.playerId === 16153)!;
    const boards = { "0 PPR": ZERO_PPR_TOP20_2025, "Half PPR": HALF_PPR_TOP20_2025, PPR: PPR_2025 } as const;
    for (const r of RULESETS) {
      const s = NACUA_2025_BY_RULESET[r];
      assert.equal(s.points, nacua.by[r].points, r);
      assert.equal(s.pointsPerGame, nacua.by[r].pointsPerGame, r);
      assert.equal(s.posRank, toBoardRows(boards[r]).find((row) => row.playerId === 16153)!.posRank, r);
      assert.equal(s.posRank, 1, r);
    }
  });

  it("differs only in what his catches are worth: 129 of them, half a point each step", () => {
    const [zero, half, ppr] = RULESETS.map((r) => NACUA_2025_BY_RULESET[r]);
    assert.equal(ppr.stats.rec, 129);
    assert.ok(Math.abs(half.points - zero.points - 64.5) < 0.05 && Math.abs(ppr.points - half.points - 64.5) < 0.05);
    assert.deepEqual(zero.stats, ppr.stats);
    assert.deepEqual(
      zero.weeks.map((w) => w.week),
      ppr.weeks.map((w) => w.week),
    );
  });
});

describe("the landing's game log, and the rates it is priced with", () => {
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

  it("holds every game to the career's own week, under every preset", () => {
    for (const r of RULESETS) {
      const weeks = NACUA_2025_BY_RULESET[r].weeks;
      assert.deepEqual(
        NACUA_2025_LOG.map((g) => [g.week, g.opponent, g.home, g.points[r]]),
        weeks.map((w) => [w.week, w.opponent, w.home, w.points]),
        r,
      );
    }
  });

  it("prices every game from the preset's rates into the API's points", () => {
    // previewData throws on import if one does not add up; this names the failure.
    NACUA_2025_LOG.forEach((game, i) => {
      for (const r of RULESETS) {
        const { total, lines } = NACUA_2025_LOG_RECEIPTS[i][r];
        assert.ok(Math.abs(total - game.points[r]) < 0.05, `week ${game.week} under ${r}`);
        const rec = lines.find((l) => l.stat === "rec");
        assert.equal(rec?.rate ?? PRESET_RATES[r].rec, PRESET_RATES[r].rec);
      }
      // One rate apart: each step adds half a point a catch.
      assert.ok(Math.abs(game.points["Half PPR"] - game.points["0 PPR"] - game.stats.rec / 2) < 0.05, `week ${game.week}`);
    });
  });

  it("lights his biggest game for catches, the one the band names: 13, at home to Indianapolis", () => {
    const lit = NACUA_2025_LOG.find((g) => g.week === NACUA_LIT_WEEK)!;
    assert.equal(lit.stats.rec, Math.max(...NACUA_2025_LOG.map((g) => g.stats.rec)));
    assert.equal(NACUA_2025_LOG.filter((g) => g.stats.rec === lit.stats.rec).length, 1, "a tie for most catches");
    assert.deepEqual([lit.stats.rec, lit.opponent, lit.home], [13, "IND", true]);
    assert.deepEqual(
      RULESETS.map((r) => lit.points[r]),
      [23.0, 29.5, 36.0],
    );
  });
});

describe("the landing's \"My league\"", () => {
  it("is Half PPR with six points a passing touchdown, and nothing else changed", () => {
    for (const stat of Object.keys(MY_LEAGUE_RATES) as (keyof typeof MY_LEAGUE_RATES)[]) {
      assert.equal(MY_LEAGUE_RATES[stat], stat === "pass_td" ? 6 : PRESET_RATES["Half PPR"][stat], stat);
    }
  });
});

describe("the landing's one board", () => {
  const TOP = 8;
  const byId = (id: number) => ONE_BOARD_2025.find((p) => p.playerId === id)!;
  const surname = (p: { name: string }) => p.name.split(" ").slice(1).join(" ");
  const nacua = byId(16153);
  const slotted = (r: Ruleset) =>
    ONE_BOARD_2025.filter((p) => slotOf(p, r, nacua, TOP) !== null)
      .sort((a, b) => slotOf(a, r, nacua, TOP)! - slotOf(b, r, nacua, TOP)!)
      .map((p) => [surname(p), p.by[r].rank]);

  it("holds every ruleset's whole top 8, ranks 1 to 8 with no gaps or repeats", () => {
    for (const r of RULESETS) {
      const ranks = ONE_BOARD_2025.map((p) => p.by[r].rank);
      assert.equal(new Set(ranks).size, ranks.length, `${r}: a rank repeats`);
      for (let n = 1; n <= TOP; n++) assert.ok(ranks.includes(n), `${r}: nobody at ${n}`);
    }
  });

  it("agrees with every other capture of the same three boards", () => {
    const boards = { "0 PPR": ZERO_PPR_TOP20_2025, "Half PPR": HALF_PPR_TOP20_2025, PPR: PPR_2025 } as const;
    for (const p of ONE_BOARD_2025) {
      assert.equal(ONE_BOARD_2025.filter((q) => q.playerId === p.playerId).length, 1, p.name);
      for (const r of RULESETS) {
        const row = boards[r][p.by[r].rank - 1];
        if (!row) continue; // deeper than the capture: only Smith-Njigba, 22nd under 0 PPR
        assert.equal(row.playerId, p.playerId, `${r}: ${p.name}`);
        assert.equal(row.points, p.by[r].points, `${r}: ${p.name}`);
      }
      const zero = PPR_FROM_ZERO_2025[p.playerId];
      if (zero !== undefined) assert.equal(p.by["0 PPR"].rank, zero, p.name);
    }
    for (const r of RULESETS) {
      assert.deepEqual(
        boards[r].slice(0, 20).map((row) => row.rank),
        Array.from({ length: 20 }, (_, i) => i + 1),
        r,
      );
    }
  });

  it("follows Nacua from 20th to 11th to 2nd, and every place it names is captured", () => {
    assert.deepEqual(
      RULESETS.map((r) => nacua.by[r].rank),
      [20, 11, 2],
    );
    assert.deepEqual(
      RULESETS.map((r) => nacua.by[r].points),
      [246.0, 310.5, 375.0],
    );
    assert.equal(movedBetween(nacua, "0 PPR", "Half PPR"), 9);
    assert.equal(movedBetween(nacua, "0 PPR", "PPR"), 18);
    assert.equal(movedBetween(byId(344), "0 PPR", "PPR"), -4);
  });

  it("shows the top eight, and keeps Nacua on the board when he is outside them", () => {
    assert.deepEqual(slotted("0 PPR"), [
      ["Allen", 1],
      ["Maye", 2],
      ["Stafford", 3],
      ["Lawrence", 4],
      ["Williams", 5],
      ["Taylor", 6],
      ["McCaffrey", 7],
      ["Prescott", 8],
      ["Nacua", 20],
    ]);
    assert.deepEqual(slotted("Half PPR"), [
      ["McCaffrey", 1],
      ["Allen", 2],
      ["Maye", 3],
      ["Stafford", 4],
      ["Taylor", 5],
      ["Lawrence", 6],
      ["Robinson", 7],
      ["Gibbs", 8],
      ["Nacua", 11],
    ]);
    assert.deepEqual(slotted("PPR"), [
      ["McCaffrey", 1],
      ["Nacua", 2],
      ["Robinson", 3],
      ["Gibbs", 4],
      ["Allen", 5],
      ["Taylor", 6],
      ["Smith-Njigba", 7],
      ["Maye", 8],
      ["Stafford", 9],
    ]);
    for (const r of RULESETS) {
      const slots = ONE_BOARD_2025.map((p) => slotOf(p, r, nacua, TOP)).filter((x) => x !== null);
      assert.deepEqual(
        slots.sort((a, b) => a - b),
        Array.from({ length: TOP + 1 }, (_, i) => i),
        `${r}: every slot filled once`,
      );
    }
  });

  it("draws the dashed rule only where the last slot jumps places, and names the place it lands on", () => {
    assert.deepEqual(
      RULESETS.map((r) => skipsTo(nacua, r, TOP)),
      [true, true, false],
    );
    assert.deepEqual(
      RULESETS.map((r) => lastPlace(nacua, r, TOP)),
      [20, 11, 9],
    );
    // The boundaries: followed at exactly 9th is the next place, not a skip; at 10th it is.
    const williams = byId(23961);
    assert.equal(williams.by["Half PPR"].rank, 9);
    assert.equal(skipsTo(williams, "Half PPR", TOP), false);
    assert.equal(slotOf(williams, "Half PPR", williams, TOP), TOP);
    assert.equal(lastPlace(williams, "Half PPR", TOP), 9);
    const prescott = byId(17878);
    assert.equal(prescott.by["Half PPR"].rank, 10);
    assert.equal(skipsTo(prescott, "Half PPR", TOP), true);
    assert.equal(lastPlace(prescott, "Half PPR", TOP), 10);
  });

  it("tells the quarterbacks' story in its letters: all of the 0 PPR top five, one of the PPR's", () => {
    const qbs = (r: Ruleset) =>
      ONE_BOARD_2025.filter((p) => p.by[r].rank <= 5 && p.position === "QB").length;
    assert.equal(qbs("0 PPR"), 5);
    assert.equal(qbs("PPR"), 1);
  });
});
