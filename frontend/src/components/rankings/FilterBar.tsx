"use client";

import { seasons } from "@/lib/season";
import type { Position, Scope } from "@/lib/types";
import { SearchField } from "../ui/SearchField";
import { SeasonSelect } from "../ui/SeasonSelect";
import { SegmentedControl } from "../ui/SegmentedControl";

export const SCOPES: { value: Scope; label: string; title: string; phrase: string }[] = [
  { value: "season", label: "Season", title: "Season total", phrase: "by season total" },
  { value: "per_game", label: "Per game", title: "Points per game played", phrase: "by points per game" },
  {
    value: "last4",
    label: "Last 4",
    title: "The four most recent regular-season weeks",
    phrase: "over the last four weeks",
  },
];

/**
 * Position options carry their own letters in their own hue -- the same
 * letters every row's chip carries -- so the filter and the board read as one
 * vocabulary. "All" stays ink.
 */
const POSITIONS: { value: string; label: React.ReactNode; title?: string }[] = [
  { value: "ALL", label: "All", title: "Every position" },
  { value: "QB", label: <span className="font-label font-bold text-pos-qb">QB</span>, title: "Quarterbacks" },
  { value: "RB", label: <span className="font-label font-bold text-pos-rb">RB</span>, title: "Running backs" },
  { value: "WR", label: <span className="font-label font-bold text-pos-wr">WR</span>, title: "Wide receivers" },
  { value: "TE", label: <span className="font-label font-bold text-pos-te">TE</span>, title: "Tight ends" },
];

/**
 * The board's filters, under the scoring switch: position, window, season and
 * find. Scoring is not here -- it is the product's argument, so it has a row of
 * its own above (`FormatBar`).
 *
 * Every control maps to a parameter the API actually takes. Format
 * (redraft/dynasty), single weeks and rest-of-season are absent because the
 * backend has no such thing yet -- a control that does nothing is worse than
 * no control. On a phone the groups each take a full row, search last, so
 * nothing is clipped and every option is a 40px target.
 */
export function FilterBar({
  season,
  onSeason,
  scope,
  onScope,
  position,
  onPosition,
  find,
  onFind,
}: {
  season: number;
  onSeason: (season: number) => void;
  scope: Scope;
  onScope: (scope: Scope) => void;
  position: Position | null;
  onPosition: (position: Position | null) => void;
  find: string;
  onFind: (find: string) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2.5">
      <SegmentedControl
        legend="Position"
        options={POSITIONS}
        value={position ?? "ALL"}
        onChange={(v) => onPosition(v === "ALL" ? null : (v as Position))}
        hideLegend
        fill
      />
      <div className="flex min-w-0 items-center gap-3 max-sm:w-full">
        <div className="min-w-0 max-sm:flex-1">
          <SegmentedControl
            legend="Window"
            options={SCOPES}
            value={scope}
            onChange={(v) => onScope(v as Scope)}
            hideLegend
            fill
          />
        </div>
        <SeasonSelect value={season} options={seasons()} onChange={onSeason} variant="field" />
      </div>
      <SearchField
        label="Find a player on this board"
        placeholder="Find a player"
        value={find}
        onChange={onFind}
        // The URL keeps 60 characters of it (lib/boardParams.ts); the box
        // should not accept more than a refresh would give back.
        maxLength={60}
        className="w-full sm:w-auto sm:min-w-52 sm:flex-1 lg:ml-auto lg:max-w-80 lg:flex-none"
      />
    </div>
  );
}
