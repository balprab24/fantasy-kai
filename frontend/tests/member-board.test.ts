import { strict as assert } from "node:assert";
import { describe, it } from "node:test";
import { toMemberBoardRows, toMemberOrLegacyRows, withoutBoardMetrics } from "../src/lib/board.ts";
import { boardSearch, parseBoardParams } from "../src/lib/boardParams.ts";
import type { MemberBoardRow } from "../src/lib/types.ts";

const row: MemberBoardRow = {
  rank: 201, playerId: 42, name: "Example Receiver", position: "WR", team: null,
  gamesPlayed: 1, points: 12, pointsPerGame: 12, espnId: null,
  posRank: 75, ppgPosRank: 40, recentPpg: 0, form: 0,
  recentWeeks: [{ week: 5, points: null }, { week: 6, points: 0 }],
  dataThrough: 6, usage: { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 4 },
};

describe("member board API metrics", () => {
  it("keeps legacy points usable without inventing any absent board metric", () => {
    const legacy = withoutBoardMetrics(row);
    assert.equal(legacy.points, 12);
    for (const key of ["posRank", "ppgPosRank", "recentPpg", "form", "dataThrough", "usage"] as const) {
      assert.equal(legacy[key], null);
    }
    assert.deepEqual(legacy.recentWeeks, []);
  });
  it("uses whole-population ranks even when only a later page is provided", () => {
    const [result] = toMemberBoardRows([row]);
    assert.equal(result.posRank, 75);
    assert.equal(result.ppgPosRank, 40);
    assert.equal(result.quality, "mid");
    assert.equal(result.recentPpg, 0);
    assert.equal(result.form, 0);
    assert.deepEqual(result.recentWeeks, row.recentWeeks);
  });
  it("preserves an unqualified rate and missing recent metrics rather than deriving substitutes", () => {
    const [result] = toMemberBoardRows([{ ...row, ppgPosRank: null, recentPpg: null, form: null }], "pointsPerGame");
    assert.equal(result.ppgPosRank, null);
    assert.equal(result.quality, null);
    assert.equal(result.recentPpg, null);
    assert.equal(result.form, null);
    assert.equal(result.value, 12);
  });
});

describe("legacy fallback rows", () => {
  it("derive position ranks from a board loaded from rank 1, as before the board route", () => {
    const legacy = [
      { ...row, rank: 1, playerId: 1, position: "RB" as const, points: 30, pointsPerGame: 15, gamesPlayed: 2 },
      { ...row, rank: 2, playerId: 2, position: "WR" as const, points: 20, pointsPerGame: 10, gamesPlayed: 2 },
      { ...row, rank: 3, playerId: 3, position: "RB" as const, points: 10, pointsPerGame: 5, gamesPlayed: 2 },
    ].map(withoutBoardMetrics);
    const result = toMemberOrLegacyRows(legacy);
    assert.deepEqual(result.map((r) => r.posRank), [1, 1, 2]);
    assert.equal(result[0].recentPpg, null);
  });
  it("leave board-route rows to the API's ranks", () => {
    assert.equal(toMemberOrLegacyRows([row])[0].posRank, 75);
  });
});

describe("board display URL", () => {
  it("round-trips usage with scoring, search and position; rejects unknown views", () => {
    const limits = { first: 2020, current: 2026 };
    const state = parseBoardParams(new URLSearchParams("view=usage&profileId=2&pos=WR&q=chase"), limits);
    assert.equal(state.view, "usage");
    assert.deepEqual(parseBoardParams(new URLSearchParams(boardSearch(state, 2026)), limits), state);
    assert.equal(parseBoardParams(new URLSearchParams("view=bogus"), limits).view, undefined);
  });
});
