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
  FOLLOWED,
  FOLLOWED_SEASON,
  HERO_SEASON,
  HERO_THROUGH_WEEK,
  MIXER_BOARDS,
  RECEIPT_GAME,
  type CatchRate,
  type MixKey,
} from "../src/components/landing/heroData.ts";
import { STARTERS, assignTiers, band, bandFor, formatPoints, starterWeeks, toBoardRows } from "../src/lib/board.ts";
import { boardSearch, parseBoardParams } from "../src/lib/boardParams.ts";
import { neighbours } from "../src/lib/boardSequence.ts";
import { biggestMovers } from "../src/lib/movers.ts";
import { apiOriginOf, contentSecurityPolicy } from "../src/lib/csp.ts";
import { CUTOUT_ASPECT, HEADSHOTS_ENABLED, headshotCutoutUrl, headshotUrl } from "../src/lib/headshot.ts";
import { LANDING_SECTIONS, safeNext } from "../src/lib/landing.ts";
import { CATCH_RATES, PASS_TDS, castOf, describeMix, formatRate, mixKey, slotOn } from "../src/lib/mixer.ts";
import { presetRates, priceLine } from "../src/lib/receipt.ts";
import { SHEET, sheetRows, sheetValue } from "../src/lib/rulesSheet.ts";
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
import { finishStep, lastGames, lastWeekPlayed, parseTab, recentGames, starterShare, statStep, weekSlots } from "../src/lib/playerView.ts";
import { HEAT_INVERTED, HEAT_PER_GAME, HEAT_RELEVANT, HEAT_WEEK } from "../src/lib/heatBands.ts";
import { COLUMNS, columnsFor, formatCount, printedCount, type StatSource } from "../src/lib/playerStats.ts";
import { profileCaption, profileLabel } from "../src/lib/profiles.ts";

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

describe("profileCaption", () => {
  // The scoring switch says what each preset pays a catch. The migration is the
  // source: parse its rule objects, and hold every caption's number to them.
  const sql = readFileSync(
    new URL("../../backend/src/main/resources/db/migration/V3__seed_scoring_presets.sql", import.meta.url),
    "utf8",
  );
  const seeded = new Map(
    [...sql.matchAll(/\(NULL, '([^']+)', TRUE, '(\{[\s\S]*?\})'::jsonb\)/g)].map((m) => [m[1], JSON.parse(m[2])]),
  );
  const number = (caption: string) => Number(caption.match(/(\d+(?:\.\d+)?|½)/)![1].replace("½", "0.5"));

  it("names every preset V3 seeds, by the rate a catch is paid", () => {
    assert.equal(seeded.size, 4, "V3 did not parse into four presets");
    for (const [name, rules] of seeded) {
      const caption = profileCaption({ name, preset: true });
      assert.notEqual(caption, "Your ruleset", `${name} has no caption`);
      const te = rules.position_overrides?.TE?.rec;
      assert.equal(number(caption), te ?? rules.base.rec, name);
      if (te !== undefined) assert.match(caption, /^TE /, name);
    }
  });

  it("claims nothing about a member's own ruleset", () => {
    assert.equal(profileCaption({ name: "Half PPR", preset: false }), "Your ruleset");
  });
});

describe("headshotUrl", () => {
  it("is on, by owner decision with the risk known -- the switch is the kill switch", () => {
    assert.equal(HEADSHOTS_ENABLED, true);
  });
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

  it("grades a heat tile on the figure it prints, not the unrounded value", () => {
    // 34.06 prints as 34.1: graded unrounded it fell short of a 34.07 cut-off.
    assert.equal(formatCount(34.06), "34.1");
    assert.equal(printedCount(34.06), 34.1);
    assert.equal(statStep(printedCount(34.06), [34.07, 30, 25]), 0);
    assert.equal(printedCount(1412.04), 1412, "no thousands separator to trip Number()");
    assert.equal(printedCount(-2.25), -2.3);
    assert.equal(printedCount(0), 0);
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

describe("biggestMovers", () => {
  const row = (playerId: number, rank: number) => ({ playerId, name: `P${playerId}`, position: "WR", rank });

  it("names the biggest gains and losses, best current rank first on a tie", () => {
    const rows = [row(1, 1), row(2, 2), row(3, 3), row(4, 4), row(5, 5)];
    const previous = new Map([[1, 9], [2, 2], [3, 1], [4, 12], [5, 4]]);
    const { up, down } = biggestMovers(rows, previous);
    assert.deepEqual(up.map((m) => [m.playerId, m.delta]), [[1, 8], [4, 8]]);
    assert.deepEqual(down.map((m) => [m.playerId, m.delta]), [[3, -2], [5, -1]]);
  });

  it("ignores moves wholly below the window, and anyone with no previous rank", () => {
    const rows = [row(1, 70), row(2, 59), row(3, 10)];
    const previous = new Map([[1, 120], [2, 90]]);
    const { up } = biggestMovers(rows, previous, { window: 60 });
    // 120 -> 70 is outside the window both times; 90 -> 59 ends inside it; 3 was not loaded before.
    assert.deepEqual(up.map((m) => m.playerId), [2]);
  });

  it("keeps at most `count` either way", () => {
    const rows = Array.from({ length: 10 }, (_, i) => row(i + 1, i + 1));
    const previous = new Map(rows.map((r) => [r.playerId, r.rank + 5]));
    assert.equal(biggestMovers(rows, previous, { count: 2 }).up.length, 2);
  });
});

describe("board sequence neighbours", () => {
  const seq = { search: "?season=2026", players: [1, 2, 3].map((id) => ({ playerId: id, name: `P${id}`, rank: id })) };
  it("finds the players either side, and nothing past the ends", () => {
    assert.deepEqual(
      [neighbours(seq, 2).previous?.playerId, neighbours(seq, 2).next?.playerId],
      [1, 3],
    );
    assert.equal(neighbours(seq, 1).previous, null);
    assert.equal(neighbours(seq, 3).next, null);
  });
  it("offers nothing for a player not on it, or with no sequence at all", () => {
    assert.deepEqual(neighbours(seq, 9), { previous: null, next: null });
    assert.deepEqual(neighbours(null, 1), { previous: null, next: null });
  });
});

describe("the player view's derived facts", () => {
  const wk = (week: number, posRank: number | null = null) => ({ week, opponent: "BUF", home: true, points: 10, posRank });

  it("opens the overview for any tab it does not know", () => {
    assert.equal(parseTab("scoring"), "scoring");
    assert.equal(parseTab("log"), "log");
    assert.equal(parseTab("performance"), "performance");
    assert.equal(parseTab("projections"), "overview");
    assert.equal(parseTab(null), "overview");
  });

  it("gives every week a slot up to the last game, and a gap where there was none", () => {
    const slots = weekSlots({ weeks: [wk(1), wk(2), wk(4)] });
    assert.deepEqual(slots.map((s) => [s.week, s.game?.week ?? null]), [[1, 1], [2, 2], [3, null], [4, 4]]);
    assert.deepEqual(weekSlots({ weeks: [] }), []);
    assert.equal(lastWeekPlayed({ weeks: [wk(3), wk(9)] }), 9);
    assert.equal(lastWeekPlayed({ weeks: [] }), null);
  });

  it("charts his last games across seasons, oldest first, games only", () => {
    const g = (week: number) => ({ week, opponent: "X", home: true, points: week, posRank: null });
    const seasons = [
      { season: 2026, weeks: [g(3), g(1), g(2)] },
      { season: 2025, weeks: [g(16), g(18), g(17)] },
      { season: 2024, weeks: [g(1)] },
    ];
    assert.deepEqual(
      lastGames(seasons, 5).map((x) => `${x.season}-${x.week}`),
      ["2025-17", "2025-18", "2026-1", "2026-2", "2026-3"],
    );
    assert.equal(lastGames(seasons, 17).length, 7, "fewer games than asked is all of them");
    assert.deepEqual(lastGames([], 17), []);
  });

  it("colours a finish by a 12-team league's strings: 1-12, 13-24, 25-36, below", () => {
    assert.deepEqual([1, 12, 13, 24, 25, 36, 37, 90].map(finishStep), [0, 0, 1, 1, 2, 2, 3, 3]);
    assert.equal(finishStep(null), null, "a bye or a playoff game has no rank to colour");
    assert.equal(finishStep(0), null);
  });

  it("places a box-score figure against the league's cut-offs, either way round", () => {
    const yards: [number, number, number] = [76, 47, 25];
    assert.deepEqual([134, 76, 75, 47, 25, 24, 0].map((v) => statStep(v, yards)), [0, 0, 1, 1, 2, 3, 3]);
    const picks: [number, number, number] = [0, 0, 1];
    assert.deepEqual([0, 1, 2].map((v) => statStep(v, picks, true)), [0, 2, 3], "no interceptions is the top band");
  });

  it("holds league cut-offs for every stat a position's tables lead with, in band order", () => {
    const primary: Record<string, string[]> = {
      QB: ["pass_cmp", "pass_att", "pass_yd", "pass_td", "pass_int", "rush_att", "rush_yd", "rush_td"],
      RB: ["rush_att", "rush_yd", "rush_td", "targets", "rec", "rec_yd", "rec_td"],
      WR: ["targets", "rec", "rec_yd", "rec_td"],
      TE: ["targets", "rec", "rec_yd", "rec_td"],
    };
    // Every key must be a box-score column the tables actually draw, or it would never be used.
    const keys = new Set(COLUMNS.map((c) => c.key).concat("snap"));
    for (const table of [HEAT_WEEK, HEAT_PER_GAME]) {
      for (const [pos, stats] of Object.entries(primary)) {
        for (const st of stats) assert.ok(table[pos]?.[st], `${pos} ${st} has no cut-off`);
        for (const [st, [g, y, o]] of Object.entries(table[pos])) {
          assert.ok(keys.has(st), `${pos} ${st} is not a column`);
          if (HEAT_INVERTED.includes(st)) assert.ok(g <= y && y <= o, `${pos} ${st} out of order`);
          else assert.ok(g >= y && y >= o, `${pos} ${st} out of order`);
        }
      }
    }
    for (const pos of Object.keys(HEAT_WEEK)) {
      const snap = HEAT_WEEK[pos].snap;
      assert.ok(snap && snap.every((c) => typeof c === "number"), `${pos} snap share has a full set of weekly cut-offs`);
      assert.equal(HEAT_PER_GAME[pos].snap, undefined, "the career has no snap column");
      assert.ok(HEAT_RELEVANT[pos] > 0, `${pos} has no comparison-set size for the legend`);
    }
    // A quarterback plays every snap in most games: every snap is the top band,
    // and anything less is not.
    assert.equal(statStep(100, HEAT_WEEK.QB.snap), 0);
    assert.notEqual(statStep(99, HEAT_WEEK.QB.snap), 0);
  });

  it("counts starter weeks against the position's 12-team line, and says nothing for an unranked position", () => {
    assert.deepEqual(starterShare([wk(1, 36), wk(2, 37), wk(3, null)], STARTERS.WR), { starts: 1, games: 3, line: 36 });
    assert.deepEqual(starterShare([wk(1, 12), wk(2, 13)], STARTERS.QB), { starts: 1, games: 2, line: 12 });
    assert.equal(starterShare([wk(1, 1)], undefined), null);
  });

  it("lists the recent games newest first, playoffs included", () => {
    const g = (week: number, seasonType = "REG") => ({ week, seasonType }) as unknown as Parameters<typeof recentGames>[0][number];
    assert.deepEqual(recentGames([g(1), g(19, "WC"), g(18), g(2)], 3).map((w) => w.week), [19, 18, 2]);
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
  it("names only the API and ESPN's two image paths off the site, never a whole image host", () => {
    const hosts = new Set(prod.split(/[ ;]+/).filter((token) => /^https?:\/\//.test(token)));
    assert.deepEqual([...hosts].sort(), [
      "https://a.espncdn.com/combiner/i",
      "https://a.espncdn.com/i/teamlogos/",
      "https://api.fantasykai.com",
    ]);
    assert.deepEqual(directive(prod, "connect-src"), ["'self'", "https://api.fantasykai.com"]);
    assert.deepEqual(directive(prod, "img-src"), [
      "'self'",
      "data:",
      "blob:",
      "https://a.espncdn.com/i/teamlogos/",
      "https://a.espncdn.com/combiner/i",
    ]);
  });
  it("admits exactly the URLs the app builds: a team logo and a headshot", () => {
    // CSP path matching: a source ending in "/" admits every path under it; one
    // without admits that exact path, whatever the query string.
    const sources = directive(prod, "img-src")!.filter((s) => s.includes("espncdn"));
    const admitted = (url: string) => {
      const u = new URL(url);
      return sources.some((s) => (s.endsWith("/") ? (u.origin + u.pathname).startsWith(s) : u.origin + u.pathname === s));
    };
    assert.ok(admitted("https://a.espncdn.com/i/teamlogos/nfl/500/sea.png"));
    assert.ok(admitted(headshotUrl("4429795", 64)!));
    assert.ok(admitted(headshotCutoutUrl("3117251", 600)!));
    assert.ok(!admitted("https://a.espncdn.com/i/headshots/nfl/players/full/1.png"), "the raw headshot path is not needed");
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
    // A parallel-route slot (`@modal`) is not a URL segment: what it renders
    // sits at the routes beside it, which this list already covers.
    const routes = readdirSync(new URL("../src/app/(app)/", import.meta.url), { withFileTypes: true })
      .filter((entry) => entry.isDirectory() && !entry.name.startsWith("@"))
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
    assert.ok(files.some((f) => f.endsWith("PerformanceChart.tsx")), "no component sources found");
  });

  it("never fades a text colour with an opacity modifier", () => {
    const faded = new RegExp(`(?<![\\w-])text-(?:${colours.join("|")})/\\d+`, "g");
    const hits = files.flatMap((f) =>
      [...readFileSync(new URL(f, src), "utf8").matchAll(faded)].map((m) => `${f}: ${m[0]}`),
    );
    assert.deepEqual(hits, []);
  });
});

describe("the landing's captures (heroData.ts)", () => {
  const keys = Object.keys(MIXER_BOARDS) as MixKey[];
  const byId = (k: MixKey) => new Map(MIXER_BOARDS[k].map((r) => [r.playerId, r]));

  it("holds all six boards, each ranks 1 to 60 with no gaps, and no likeness anywhere", () => {
    assert.deepEqual(
      keys.sort(),
      CATCH_RATES.flatMap((c) => PASS_TDS.map((t) => mixKey(c, t))).sort(),
    );
    for (const k of keys) {
      assert.deepEqual(MIXER_BOARDS[k].map((r) => r.rank), Array.from({ length: 60 }, (_, i) => i + 1), k);
      assert.ok(MIXER_BOARDS[k].every((r) => r.espnId === null), `${k} carries an espnId`);
    }
  });

  it("is one set of stat lines scored six ways: a catch's worth is linear, a passing TD's is 2 a touchdown", () => {
    // For anyone on all three boards of a touchdown rate, each half point a
    // catch adds the same amount: his receptions over two. A capture mixed up
    // with another season, or one board from a different day, breaks this.
    for (const td of PASS_TDS) {
      const [zero, half, full] = CATCH_RATES.map((c) => byId(mixKey(c, td)));
      let checked = 0;
      for (const [id, h] of half) {
        const z = zero.get(id);
        const f = full.get(id);
        if (!z || !f) continue;
        assert.ok(Math.abs(f.points - h.points - (h.points - z.points)) < 0.011, `${h.name} under ${td}-pt TDs`);
        assert.ok(f.points >= h.points && h.points >= z.points, h.name);
        checked++;
      }
      assert.ok(checked >= 30, `only ${checked} players on all three ${td}-pt boards`);
    }
    // A 6-point passing TD adds 2 per touchdown and changes nothing for a player who threw none.
    for (const c of CATCH_RATES) {
      const four = byId(mixKey(c, 4));
      for (const [id, six] of byId(mixKey(c, 6))) {
        const f = four.get(id);
        if (!f) continue;
        const gained = six.points - f.points;
        assert.ok(gained >= -0.001 && Math.abs(gained / 2 - Math.round(gained / 2)) < 0.006, `${six.name}: +${gained}`);
        if (six.position !== "QB") assert.ok(gained < 4.01, `${six.name} gained ${gained} without being a quarterback`);
      }
    }
  });

  it("says the week it runs through, read off the rows", () => {
    const most = Math.max(...keys.flatMap((k) => MIXER_BOARDS[k].map((r) => r.gamesPlayed)));
    assert.equal(HERO_THROUGH_WEEK, most);
    assert.ok(HERO_SEASON >= 2026);
  });

  it("follows a player who is on the Half PPR board, with the same season the board gives him", () => {
    const row = byId("0.5|4").get(FOLLOWED.id);
    assert.ok(row, `${FOLLOWED.name} is not on the Half PPR board`);
    assert.equal(FOLLOWED_SEASON.season, HERO_SEASON);
    assert.equal(FOLLOWED_SEASON.points, row.points);
    assert.equal(FOLLOWED_SEASON.gamesPlayed, row.gamesPlayed);
    assert.equal(FOLLOWED.position, row.position);
  });

  it("charts weeks that add up to the season the board ranks him by", () => {
    // The showcase's chart and its season figures are one capture: a week
    // missing or doubled would show a total the bars do not add up to.
    const weeks = FOLLOWED_SEASON.weeks;
    assert.equal(weeks.length, FOLLOWED_SEASON.gamesPlayed);
    assert.equal(new Set(weeks.map((w) => w.week)).size, weeks.length, "a week is repeated");
    const sum = weeks.reduce((t, w) => t + w.points, 0);
    // Each week is rounded to 0.1 at the API boundary; the season is rounded once.
    assert.ok(Math.abs(sum - FOLLOWED_SEASON.points) <= 0.05 * weeks.length, `${sum} vs ${FOLLOWED_SEASON.points}`);
  });

  it("prices the receipt's game into the API's own points under every preset", () => {
    for (const c of CATCH_RATES as readonly CatchRate[]) {
      const { total, lines } = priceLine(RECEIPT_GAME.stats, presetRates(c));
      assert.ok(Math.abs(total - RECEIPT_GAME.points[c]) < 0.05, `${c} a catch: ${total} vs ${RECEIPT_GAME.points[c]}`);
      assert.ok(lines.some((l) => l.stat === "rec"), "the receipt's game has no catch");
    }
    const week = FOLLOWED_SEASON.weeks.find((w) => w.week === RECEIPT_GAME.week);
    assert.equal(week?.points, RECEIPT_GAME.points[0.5], "the receipt's game is not the career's week");
  });

  it("copies V3's three presets exactly, and they differ in one rate: a catch", () => {
    const sql = readFileSync(
      new URL("../../backend/src/main/resources/db/migration/V3__seed_scoring_presets.sql", import.meta.url),
      "utf8",
    );
    const seeded = new Map(
      [...sql.matchAll(/\(NULL, '([^']+)', TRUE, '(\{[\s\S]*?\})'::jsonb\)/g)].map((m) => [m[1], JSON.parse(m[2])]),
    );
    const source = { 0: "Standard", 0.5: "Half PPR", 1: "Full PPR" } as const;
    for (const c of CATCH_RATES) {
      const rules = seeded.get(source[c]);
      assert.ok(rules, `${source[c]} is not in V3`);
      assert.equal(rules.position_overrides, undefined, `${source[c]} has overrides`);
      assert.deepEqual(presetRates(c), rules.base, source[c]);
    }
  });
});

describe("the board's derivations, on real boards", () => {
  for (const k of ["0|4", "0.5|4", "1|6"] as MixKey[]) {
    it(`tiers the ${k} board by natural breaks: contiguous, at most five, never through a tie`, () => {
      const rows = toBoardRows(MIXER_BOARDS[k]);
      const { tiers, rest } = assignTiers(rows);
      assert.ok(tiers.length >= 2 && tiers.length <= 5, `${tiers.length} tiers`);
      assert.deepEqual(tiers.flatMap((t) => t.rows.map((r) => r.rank)), Array.from({ length: 60 }, (_, i) => i + 1));
      assert.equal(rest.length, 0);
      for (let i = 1; i < tiers.length; i++) {
        assert.notEqual(tiers[i - 1].low, tiers[i].high, `a break through a tie between ${tiers[i - 1].letter} and ${tiers[i].letter}`);
        assert.ok(tiers[i - 1].low > tiers[i].high);
      }
    });
  }

  it("counts positional ranks from rank 1, and refuses to on a board that does not start there", () => {
    const rows = toBoardRows(MIXER_BOARDS["0.5|4"]);
    const firstWr = rows.find((r) => r.position === "WR")!;
    assert.equal(firstWr.posRank, 1);
    const qbs = rows.filter((r) => r.position === "QB").map((r) => r.posRank);
    assert.deepEqual(qbs, qbs.map((_, i) => i + 1));
    assert.ok(toBoardRows(MIXER_BOARDS["0.5|4"].slice(5)).every((r) => r.posRank === null));
  });
});

describe("the rule mixer", () => {
  const board = [1, 2, 3, 4].map((rank) => ({ playerId: rank * 10, rank }));

  it("stands a player in his slot inside the top, and parks him just under it outside", () => {
    assert.equal(slotOn(board, 10, 3), 0);
    assert.equal(slotOn(board, 30, 3), 2);
    assert.equal(slotOn(board, 40, 3), 3);
    assert.equal(slotOn(board, 99, 3), 3);
  });

  it("casts everyone in any board's top, once", () => {
    const other = [{ playerId: 40, rank: 1 }, { playerId: 10, rank: 2 }];
    assert.deepEqual(castOf([board, other], 2).sort((a, b) => a - b), [10, 20, 40]);
  });

  it("names a ruleset by its preset and the one rate changed", () => {
    assert.equal(describeMix(0.5, 4), "Half PPR");
    assert.equal(describeMix(0, 6), "0 PPR with 6-point passing touchdowns");
    assert.equal(formatRate(0.5), "½");
    assert.equal(formatRate(6), "6");
  });

  it("has a story worth showing: the followed player moves at least five places across the six boards", () => {
    // The page's copy does not name a rank, so this is a guard on the demo
    // being worth showing rather than on any sentence.
    const ranks = (Object.keys(MIXER_BOARDS) as MixKey[]).map(
      (k) => MIXER_BOARDS[k].find((r) => r.playerId === FOLLOWED.id)?.rank ?? 61,
    );
    assert.ok(Math.max(...ranks) - Math.min(...ranks) >= 5, `ranks ${ranks.join(", ")}`);
  });
});

describe("the landing page's sections", () => {
  it("are all on the page the header links into", () => {
    const page = readFileSync(new URL("../src/app/(site)/page.tsx", import.meta.url), "utf8");
    for (const { id } of LANDING_SECTIONS) assert.match(page, new RegExp(`id="${id}"`), id);
  });
});

describe("the landing's ruleset sheet", () => {
  // "Your league. Everywhere." shows a saved ruleset and says it is Half PPR
  // with 6-point passing touchdowns. Its values are worked out from rates, so
  // they are held here to V3's Half PPR row, with the passing TD changed.
  const sql = readFileSync(
    new URL("../../backend/src/main/resources/db/migration/V3__seed_scoring_presets.sql", import.meta.url),
    "utf8",
  );
  const half = new Map(
    [...sql.matchAll(/\(NULL, '([^']+)', TRUE, '(\{[\s\S]*?\})'::jsonb\)/g)].map((m) => [m[1], JSON.parse(m[2])]),
  ).get("Half PPR");

  it("names every scorable stat exactly once", () => {
    const named = SHEET.flatMap((g) => g.rows.flatMap((r) => r.stats));
    assert.equal(new Set(named).size, named.length, "a stat is on the sheet twice");
    assert.deepEqual([...named].sort(), Object.keys(half.base).sort());
  });

  it("says V3's Half PPR rates, with a passing touchdown at 6", () => {
    const rows = Object.fromEntries(sheetRows({ ...half.base, pass_td: 6 }).flatMap((g) => g.rows.map(([l, v]) => [`${g.heading} ${l}`, v])));
    assert.deepEqual(rows, {
      "Passing Yards": "1 per 25",
      "Passing Touchdown": "6",
      "Passing Interception": "−2",
      "Rushing Yards": "1 per 10",
      "Rushing Touchdown": "6",
      "Receiving Catch": "½",
      "Receiving Yards": "1 per 10",
      "Receiving Touchdown": "6",
      "Other Fumble lost": "−2",
      "Other Return touchdown": "6",
      "Other 2-pt conversion": "2",
    });
    // The page renders from presetRates, which must be the same rates.
    assert.deepEqual(sheetRows({ ...presetRates(0.5), pass_td: 6 }), sheetRows({ ...half.base, pass_td: 6 }));
  });

  it("refuses to merge stats into one row once their rates differ", () => {
    assert.throws(() => sheetRows({ ...half.base, rush_2pt: 1 }), /2-pt conversion/);
    assert.equal(sheetValue("rec", 1), "1");
    assert.equal(sheetValue("rec_yd", 0.04), "1 per 25");
  });
});

describe("the board's URL writes", () => {
  // Next 16 follows a bare history.replaceState by keeping the route tree it
  // rendered for the old query. A player opened from that board then fails the
  // overlay's interception and loads as a full page -- measured after every
  // filter and scoring change, 2026-10-20. This suite has no browser, so it
  // pins the cause: the board writes through the router.
  const source = readFileSync(new URL("../src/components/rankings/RankingsWorkspace.tsx", import.meta.url), "utf8");
  const code = source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");

  it("go through router.replace, never history.replaceState", () => {
    assert.doesNotMatch(code, /history\.replaceState\(/);
    assert.match(code, /router\.replace\(/);
  });
});
