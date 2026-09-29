/**
 * The pure modules behind the board and the player page, checked without a
 * browser or a framework: `node --test` runs TypeScript directly on Node 24, so
 * this costs no dependency. Only modules whose imports are all type-only can
 * be loaded this way -- which is also a fair test of what "pure" means.
 */
import { strict as assert } from "node:assert";
import { describe, it } from "node:test";
import { band, bandFor, formatPoints } from "../src/lib/board.ts";
import { boardSearch, parseBoardParams } from "../src/lib/boardParams.ts";
import { headshotUrl } from "../src/lib/headshot.ts";
import { LANDING_SECTIONS, safeNext } from "../src/lib/landing.ts";
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
