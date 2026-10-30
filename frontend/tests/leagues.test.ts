/**
 * `lib/leagues.ts`: how an imported league is put into words. The rule under
 * test is the plan's -- an unmodelled setting is reported, never dropped.
 */
import { strict as assert } from "node:assert";
import { describe, it } from "node:test";
import {
  lineupSentence,
  record,
  slotOrder,
  spotName,
  starts,
  syncedAgo,
  unmodeledNotices,
} from "../src/lib/leagues.ts";
import type { RosterSpot, UnmodeledItem } from "../src/lib/types.ts";

const item = (key: string, label: string, group: UnmodeledItem["group"], value = 1): UnmodeledItem => ({
  key,
  label,
  value,
  group,
});

describe("unmodeledNotices", () => {
  it("names offensive settings in plain words, in one sentence", () => {
    const notices = unmodeledNotices([
      item("pass_att", "Pass attempts", "OFFENSE"),
      item("rush_fd", "Rushing first downs", "OFFENSE"),
      item("brand_new_stat", "brand_new_stat", "OFFENSE"),
    ]);
    assert.deepEqual(notices, [
      "This league also scores pass attempts, rushing first downs and brand_new_stat, which Fantasy Kai doesn't model yet.",
    ]);
  });

  it("collapses kicking and team defense into one sentence each, however many keys", () => {
    const notices = unmodeledNotices([
      item("fgm_0_19", "fgm_0_19", "KICKING"),
      item("xpm", "xpm", "KICKING"),
      item("pts_allow_0", "pts_allow_0", "TEAM_DEFENSE"),
      item("sack", "sack", "TEAM_DEFENSE"),
    ]);
    assert.equal(notices.length, 2);
    assert.match(notices[0], /kicking/);
    assert.match(notices[1], /defense and special teams/);
  });

  it("drops nothing: every group present yields a sentence", () => {
    const groups: UnmodeledItem["group"][] = ["OFFENSE", "KICKING", "TEAM_DEFENSE", "DEFENSIVE_PLAYERS", "ROSTER"];
    const notices = unmodeledNotices(groups.map((g) => item(`k_${g}`, "Label", g)));
    assert.equal(notices.length, groups.length);
  });

  it("says which lineup slot is not modelled, with its count", () => {
    assert.deepEqual(unmodeledNotices([item("DL", "1 × DL lineup slot", "ROSTER", 2)]), [
      "This league starts 2 DL, a slot Fantasy Kai doesn't model yet.",
    ]);
  });

  it("is silent when there is nothing to say", () => {
    assert.deepEqual(unmodeledNotices([]), []);
  });
});

describe("lineupSentence", () => {
  it("lists starters in lineup order, then the bench and IR", () => {
    assert.equal(
      lineupSentence({ BENCH: 6, WR: 2, QB: 1, TE: 1, RB: 2, FLEX_RWT: 1, DST: 1, K: 1, IR: 1 }),
      "1 QB, 2 RB, 2 WR, 1 TE, 1 flex, 1 K, 1 D/ST · 6 on the bench, 1 IR",
    );
  });

  it("names the overlapping flexes apart", () => {
    assert.equal(lineupSentence({ FLEX_WR: 1, FLEX_WT: 1, SUPERFLEX: 1 }), "1 w/r flex, 1 w/t flex, 1 superflex");
  });
});

describe("roster order", () => {
  it("puts starters before an unmodelled slot, then bench, IR and taxi", () => {
    const slots = ["TAXI", "BENCH", "UNMODELED", "IR", "DST", "QB", "FLEX_RWT"];
    assert.deepEqual(
      [...slots].sort((a, b) => slotOrder(a) - slotOrder(b)),
      ["QB", "FLEX_RWT", "DST", "UNMODELED", "BENCH", "IR", "TAXI"],
    );
    assert.equal(starts("UNMODELED"), true);
    assert.equal(starts("BENCH"), false);
  });
});

describe("words", () => {
  it("writes a record with an en dash, and a tie only when there is one", () => {
    assert.equal(record(5, 2, 0), "5–2");
    assert.equal(record(3, 3, 1), "3–3–1");
  });

  it("says how long ago a sync was", () => {
    const now = new Date("2026-10-30T18:00:00Z");
    assert.equal(syncedAgo("2026-10-30T17:59:40Z", now), "just now");
    assert.equal(syncedAgo("2026-10-30T17:59:00Z", now), "1 minute ago");
    assert.equal(syncedAgo("2026-10-30T17:48:00Z", now), "12 minutes ago");
    assert.equal(syncedAgo("2026-10-30T15:00:00Z", now), "3 hours ago");
    assert.equal(syncedAgo("2026-10-28T18:00:00Z", now), "2 days ago");
  });

  it("says plainly when a player is not matched", () => {
    const spot: RosterSpot = {
      externalPlayerId: "9999",
      slot: "BENCH",
      kind: "UNMATCHED",
      playerId: null,
      name: null,
      position: null,
      team: null,
      espnId: null,
    };
    assert.equal(spotName(spot), "Not matched (Sleeper player 9999)");
    assert.equal(spotName({ ...spot, kind: "TEAM_DEFENSE", name: "CLE D/ST" }), "CLE D/ST");
  });
});
