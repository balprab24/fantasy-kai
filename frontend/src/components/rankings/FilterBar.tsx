"use client";

import { seasons } from "@/lib/season";
import type { Position, Scope, ScoringProfile } from "@/lib/types";
import { RulesetSwitch } from "../RulesetSwitch";
import { SearchField } from "../ui/SearchField";
import { SegmentedControl } from "../ui/SegmentedControl";

export const SCOPES: { value: Scope; label: string; title: string }[] = [
  { value: "season", label: "Season", title: "Season total" },
  { value: "per_game", label: "Per game", title: "Points per game played" },
  { value: "last4", label: "Last 4", title: "The four most recent regular-season weeks" },
];

const POSITIONS: { value: string; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "QB", label: "QB" },
  { value: "RB", label: "RB" },
  { value: "WR", label: "WR" },
  { value: "TE", label: "TE" },
];

/**
 * Every control here maps to a parameter the API actually takes. Format
 * (redraft/dynasty), single weeks and rest-of-season are absent because the
 * backend has no such thing yet -- a control that does nothing is worse than
 * no control.
 *
 * One wrapping row, not a form: every option names itself ("Half PPR", "Last
 * 4", "WR"), so the group labels are kept for screen readers and dropped from
 * the screen, and the board starts a whole control-row higher. Search takes
 * whatever the row has left, from 10rem up: at 1397px -- a common laptop --
 * a fixed 16rem box was the one thing that wrapped, and a second row for a
 * search box is exactly the vertical space this bar exists to save. It wraps
 * to a full-width row of its own only when even 10rem will not fit.
 */
export function FilterBar({
  profiles,
  profileId,
  onProfile,
  season,
  onSeason,
  scope,
  onScope,
  position,
  onPosition,
  find,
  onFind,
}: {
  profiles: ScoringProfile[] | undefined;
  profileId: number | null;
  onProfile: (id: number) => void;
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
    <div className="flex flex-wrap items-center gap-x-2 gap-y-2">
      {profiles ? (
        <RulesetSwitch profiles={profiles} selected={profileId} onSelect={onProfile} hideLegend />
      ) : (
        <div aria-hidden className="h-[46px] w-80 max-w-full md:h-[38px]" />
      )}

      <label className="flex">
        <span className="sr-only">Season</span>
        <select
          value={season}
          onChange={(e) => onSeason(Number(e.target.value))}
          className="tabular h-[46px] rounded-lg border border-line-strong bg-raised px-3 text-sm text-ink hover:border-mute md:h-[38px]"
        >
          {seasons().map((y) => (
            <option key={y} value={y}>
              {y}
            </option>
          ))}
        </select>
      </label>

      <SegmentedControl
        legend="Window"
        options={SCOPES}
        value={scope}
        onChange={(v) => onScope(v as Scope)}
        hideLegend
      />

      <SegmentedControl
        legend="Position"
        options={POSITIONS}
        value={position ?? "ALL"}
        onChange={(v) => onPosition(v === "ALL" ? null : (v as Position))}
        hideLegend
      />

      <SearchField
        label="Find a player on this board"
        placeholder="Find a player…"
        value={find}
        onChange={onFind}
        className="min-w-40 flex-1 lg:ml-auto lg:max-w-72"
      />
    </div>
  );
}
