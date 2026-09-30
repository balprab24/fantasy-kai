/**
 * The pure modules behind the board and the player page, checked without a
 * browser or a framework: `node --test` runs TypeScript directly on Node 24, so
 * this costs no dependency. Only modules whose imports are all type-only can
 * be loaded this way -- which is also a fair test of what "pure" means.
 */
import { strict as assert } from "node:assert";
import { readdirSync } from "node:fs";
import { describe, it } from "node:test";
import {
  MCCAFFREY_2025_BY_RULESET,
  MCCAFFREY_2025_PPR,
  MCCAFFREY_2025_PPR_RECEIPT,
  NACUA_2025_PPR,
  PPR_2025,
  PPR_FROM_ZERO_2025,
  PPR_VS_ZERO_2025,
} from "../src/components/landing/previewData.ts";
import { band, bandFor, formatPoints, starterWeeks, toBoardRows } from "../src/lib/board.ts";
import { boardSearch, parseBoardParams } from "../src/lib/boardParams.ts";
import { apiOriginOf, contentSecurityPolicy } from "../src/lib/csp.ts";
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
import { routePath, runs, tangents, type Point } from "../src/lib/trace.ts";

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

/** Every coordinate pair in path data, in order. */
function coords(d: string): Point[] {
  const nums = d.match(/-?\d+(\.\d+)?/g)!.map(Number);
  const out: Point[] = [];
  for (let i = 0; i < nums.length; i += 2) out.push({ x: nums[i], y: nums[i + 1] });
  return out;
}

/** A cubic from p0 to p3 through controls p1, p2, at t. */
function cubic(p0: Point, p1: Point, p2: Point, p3: Point, t: number): Point {
  const u = 1 - t;
  return {
    x: u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
    y: u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y,
  };
}

describe("routePath", () => {
  // A real shape: McCaffrey's 2025 PPR weeks, a 39.1 peak beside a 9.8 week.
  const weeks = MCCAFFREY_2025_PPR.weeks;
  const pts: (Point | null)[] = Array.from({ length: 18 }, (_, i) => {
    const w = weeks.find((x) => x.week === i + 1);
    return w ? { x: i * 10 + 5, y: 100 - w.points } : null;
  });

  it("passes through every week it draws", () => {
    const d = routePath(pts);
    const ends = d.split(/(?=[MC])/).map((seg) => {
      const c = coords(seg);
      return c[c.length - 1];
    });
    const expected = pts.filter((p): p is Point => p !== null);
    assert.equal(ends.length, expected.length);
    ends.forEach((p, i) => {
      assert.ok(Math.abs(p.x - expected[i].x) < 0.01 && Math.abs(p.y - expected[i].y) < 0.01, `week point ${i}`);
    });
  });

  it("breaks at a bye instead of drawing a game nobody played", () => {
    const d = routePath(pts);
    assert.equal((d.match(/M/g) ?? []).length, 2, "one line before the week-14 bye, one after");
    assert.equal(runs(pts).length, 2);
  });

  it("draws nothing for a lone point or an empty season", () => {
    assert.equal(routePath([]), "");
    assert.equal(routePath([{ x: 0, y: 0 }]), "");
    assert.equal(routePath([{ x: 0, y: 0 }, null, { x: 2, y: 2 }]), "");
  });

  it("never overshoots between two weeks, so it shows no peak the data lacks", () => {
    for (const run of runs(pts).filter((r) => r.length >= 2)) {
      const t = tangents(run);
      for (let i = 0; i < run.length - 1; i++) {
        const [a, b] = [run[i], run[i + 1]];
        const h = (b.x - a.x) / 3;
        const c1 = { x: a.x + h, y: a.y + t[i] * h };
        const c2 = { x: b.x - h, y: b.y - t[i + 1] * h };
        const lo = Math.min(a.y, b.y) - 1e-9;
        const hi = Math.max(a.y, b.y) + 1e-9;
        for (let k = 1; k < 20; k++) {
          const y = cubic(a, c1, c2, b, k / 20).y;
          assert.ok(y >= lo && y <= hi, `segment ${i} overshoots at t=${k / 20}: ${y} outside ${lo}..${hi}`);
        }
      }
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
    for (const r of PPR_2025.slice(0, 12)) assert.ok(PPR_FROM_ZERO_2025[r.playerId] > 0, r.name);
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
