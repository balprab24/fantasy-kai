import type { RosterSpot, UnmodeledItem } from "./types";

/**
 * A member's imported league, put into words: lineup slots, what the league
 * scores that Fantasy Kai does not, records and freshness. Pure, so `node
 * --test` checks the wording without a browser.
 */

/** `RosterSlot` in the API, in lineup order, with what a member calls each. */
export const SLOT_LABELS: Record<string, string> = {
  QB: "QB",
  RB: "RB",
  WR: "WR",
  TE: "TE",
  FLEX_RWT: "Flex",
  FLEX_WR: "W/R flex",
  FLEX_WT: "W/T flex",
  SUPERFLEX: "Superflex",
  K: "K",
  DST: "D/ST",
  BENCH: "Bench",
  IR: "IR",
  TAXI: "Taxi",
  UNMODELED: "Other",
};

const STARTING = ["QB", "RB", "WR", "TE", "FLEX_RWT", "FLEX_WR", "FLEX_WT", "SUPERFLEX", "K", "DST"];

export function slotLabel(slot: string): string {
  return SLOT_LABELS[slot] ?? slot;
}

/** Starters first in lineup order, then an unmodelled starting slot, bench, IR, taxi. */
export function slotOrder(slot: string): number {
  const at = STARTING.indexOf(slot);
  if (at >= 0) return at;
  return STARTING.length + ["UNMODELED", "BENCH", "IR", "TAXI"].indexOf(slot) + 1;
}

export function starts(slot: string): boolean {
  return STARTING.includes(slot) || slot === "UNMODELED";
}

/** "1 QB, 2 RB, 2 WR, 1 TE, 1 flex, 1 K, 1 D/ST -- 6 on the bench, 1 IR". */
export function lineupSentence(slots: Record<string, number>): string {
  const starting = Object.entries(slots)
    .filter(([slot]) => STARTING.includes(slot))
    .sort(([a], [b]) => slotOrder(a) - slotOrder(b))
    .map(([slot, n]) => `${n} ${slot.startsWith("FLEX") || slot === "SUPERFLEX" ? slotLabel(slot).toLowerCase() : slotLabel(slot)}`);
  const rest: string[] = [];
  if (slots.BENCH) rest.push(`${slots.BENCH} on the bench`);
  if (slots.IR) rest.push(`${slots.IR} IR`);
  return rest.length ? `${starting.join(", ")} · ${rest.join(", ")}` : starting.join(", ");
}

/**
 * What the league scores or starts that Fantasy Kai does not, one sentence per
 * kind, so a member reads "kicking isn't modelled yet" instead of 30 keys.
 * Nothing is dropped: every item lands in exactly one sentence.
 */
export function unmodeledNotices(items: UnmodeledItem[]): string[] {
  const by = (group: UnmodeledItem["group"]) => items.filter((item) => item.group === group);
  const notices: string[] = [];
  const offense = by("OFFENSE");
  if (offense.length) {
    notices.push(`This league also scores ${list(offense.map((i) => lowerFirst(i.label)))}, which Fantasy Kai doesn't model yet.`);
  }
  if (by("KICKING").length) notices.push("Kickers are scored here, and Fantasy Kai doesn't model kicking yet.");
  if (by("TEAM_DEFENSE").length) {
    notices.push("Team defenses are scored here, and Fantasy Kai doesn't model defense and special teams yet.");
  }
  if (by("DEFENSIVE_PLAYERS").length) {
    notices.push("Individual defensive players are scored here, and Fantasy Kai doesn't model them yet.");
  }
  const roster = by("ROSTER");
  if (roster.length) {
    notices.push(`This league starts ${list(roster.map((i) => `${i.value ?? 1} ${i.key}`))}, a slot Fantasy Kai doesn't model yet.`);
  }
  return notices;
}

/** 5–2, or 3–3–1 once a tie exists. An en dash, as a record is written. */
export function record(wins: number, losses: number, ties: number): string {
  return ties > 0 ? `${wins}–${losses}–${ties}` : `${wins}–${losses}`;
}

/** "just now", "12 minutes ago", "3 hours ago", "2 days ago". */
export function syncedAgo(iso: string, now: Date = new Date()): string {
  const minutes = Math.floor((now.getTime() - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? "" : "s"} ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? "" : "s"} ago`;
  const days = Math.floor(hours / 24);
  return `${days} day${days === 1 ? "" : "s"} ago`;
}

/** A roster spot's name as shown: the player, the defense, or plainly unmatched. */
export function spotName(spot: RosterSpot): string {
  if (spot.kind === "UNMATCHED") return `Not matched (Sleeper player ${spot.externalPlayerId})`;
  return spot.name ?? spot.externalPlayerId;
}

function list(parts: string[]): string {
  if (parts.length <= 1) return parts.join("");
  return `${parts.slice(0, -1).join(", ")} and ${parts[parts.length - 1]}`;
}

/** "Pass attempts" reads "pass attempts" mid-sentence; a raw key stays as it is. */
function lowerFirst(label: string): string {
  return /^[A-Z][a-z]/.test(label) ? label[0].toLowerCase() + label.slice(1) : label;
}
