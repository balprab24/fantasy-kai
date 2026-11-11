"use client";

import { profileLabel } from "@/lib/profiles";
import { seasons } from "@/lib/season";
import type { Position, Scope, ScoringProfile } from "@/lib/types";
import { Icon } from "../ui/Icon";

export const SCOPES: { value: Scope; label: string; phrase: string }[] = [
  { value: "season", label: "Season", phrase: "by season total" },
  { value: "per_game", label: "Per game", phrase: "by points per game" },
  { value: "last4", label: "Last 4", phrase: "over the last four weeks" },
];

const select = "h-8 min-w-0 rounded-control border border-line-strong bg-well px-1.5 text-[13px] text-ink";

/** One strip; compact native menus keep every control available in two phone rows. */
export function FilterBar({ profiles, profileId, onProfile, view, onView, season, onSeason,
  scope, onScope, position, onPosition, find, onFind, filtersOpen, onFilters }: {
  profiles: ScoringProfile[] | undefined; profileId: number | null; onProfile: (id: number) => void;
  view: "overview" | "usage"; onView: (view: "overview" | "usage") => void;
  season: number; onSeason: (season: number) => void;
  scope: Scope; onScope: (scope: Scope) => void;
  position: Position | null; onPosition: (position: Position | null) => void;
  find: string; onFind: (find: string) => void; filtersOpen: boolean; onFilters: () => void;
}) {
  return (
    <div aria-label="Board controls" className="board-controls flex flex-wrap items-center gap-1.5 border-y border-line bg-surface px-1.5 py-1 sm:py-1.5">
      <div className="grid min-w-0 flex-1 grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_64px_minmax(0,.9fr)] gap-1.5 sm:flex sm:flex-none">
        <select aria-label="Scoring" className={`${select} sm:w-32`} value={profileId ?? ""} disabled={!profiles} onChange={(e) => onProfile(Number(e.target.value))}>
          {!profiles && <option value="">Scoring…</option>}
          {profiles?.map((p) => <option key={p.id} value={p.id}>{profileLabel(p)}</option>)}
        </select>
        <select aria-label="View" className={select} value={view} onChange={(e) => onView(e.target.value as "overview" | "usage")}>
          <option value="overview">Overview</option><option value="usage">Usage</option>
        </select>
        <select aria-label="Season" className={select} value={season} onChange={(e) => onSeason(Number(e.target.value))}>
          {seasons().map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
        <select aria-label="Window" className={select} value={scope} onChange={(e) => onScope(e.target.value as Scope)}>
          {SCOPES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
      </div>
      <div className="flex min-w-0 flex-1 items-center gap-1.5 max-sm:basis-full">
        <select aria-label="Position" className={`${select} w-16 shrink-0 xl:hidden`} value={position ?? "ALL"} onChange={(e) => onPosition(e.target.value === "ALL" ? null : e.target.value as Position)}>
          {["ALL", "QB", "RB", "WR", "TE"].map((p) => <option key={p} value={p}>{p === "ALL" ? "All" : p}</option>)}
        </select>
        <div role="group" aria-label="Position" className="hidden items-center xl:flex">
          {["ALL", "QB", "RB", "WR", "TE"].map((p) => <button key={p} type="button" aria-pressed={(position ?? "ALL") === p} onClick={() => onPosition(p === "ALL" ? null : p as Position)} className={`h-8 rounded-control px-3 text-[13px] ${(position ?? "ALL") === p ? "bg-lift font-semibold text-ink" : "text-mute hover:bg-well"}`}>{p === "ALL" ? "All" : p}</button>)}
        </div>
        <input aria-label="Find a player on this board" placeholder="Find a player" value={find} onChange={(e) => onFind(e.target.value)} maxLength={60} type="search" className="h-8 min-w-0 flex-1 rounded-control border border-line-strong bg-well px-2 text-[13px] text-ink placeholder:text-faint" />
        <button type="button" aria-expanded={filtersOpen} aria-controls="board-options" onClick={onFilters} className="flex h-8 shrink-0 items-center gap-1 rounded-control border border-line-strong bg-well px-2 text-[13px] text-ink">
          <Icon name="scoring" size={14} />Filters
        </button>
      </div>
    </div>
  );
}
