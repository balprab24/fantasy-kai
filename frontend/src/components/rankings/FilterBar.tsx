"use client";

import type { BoardView } from "@/lib/boardParams";
import { seasons } from "@/lib/season";
import type { Position, Scope, ScoringProfile } from "@/lib/types";
import { RulesetSwitch } from "../RulesetSwitch";
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

const VIEWS: { value: BoardView; label: string; title: string }[] = [
  { value: "performance", label: "Performance", title: "What happened: recorded games, scored" },
  {
    value: "projections",
    label: "Projections",
    title: "The stored week's projected stat lines, scored",
  },
];

const POSITIONS: { value: string; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "QB", label: "QB" },
  { value: "RB", label: "RB" },
  { value: "WR", label: "WR" },
  { value: "TE", label: "TE" },
];

/**
 * A seam between control groups: structure, so a hairline, not a box. Only
 * from 2xl, where the console fits on one line -- beside the sidebar it needs
 * about 1,250px of strip, and a seam left at the end of a wrapped line divides
 * nothing from nothing.
 */
function Seam() {
  return <span aria-hidden className="mx-1 hidden h-5 w-px bg-line 2xl:block" />;
}

/**
 * The console: every control on one strip of `surface`, the one separation
 * layer, instead of five separately bordered boxes. Scoring comes first -- it
 * is the product's argument -- then the board's view, season, window,
 * position and find, with a hairline seam between groups. Season and window
 * describe recorded games only, so the projections view does not draw them:
 * the view switch stands where they were.
 *
 * Every control here maps to a parameter the API actually takes. Format
 * (redraft/dynasty), single weeks and rest-of-season are absent because the
 * backend has no such thing yet -- a control that does nothing is worse than
 * no control. The group labels stay for screen readers and are dropped from
 * the screen, because every option names itself ("Half PPR", "Last 4", "WR").
 * It wraps rather than scrolls; search takes whatever the row has left, from
 * 11rem up, and on a phone gets a row of its own -- squeezed in beside the
 * positions it clipped its own placeholder, and "Jaxon Smith-Njigba" would
 * have scrolled inside it (the critique's 390px capture, 2026-09-29).
 */
export function FilterBar({
  profiles,
  profileId,
  onProfile,
  view,
  onView,
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
  view: BoardView;
  onView: (view: BoardView) => void;
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
    <div className="flex flex-wrap items-center gap-x-1 gap-y-2 rounded-control bg-surface p-1.5">
      {profiles ? (
        <RulesetSwitch
          profiles={profiles}
          selected={profileId}
          onSelect={onProfile}
          hideLegend
          variant="bare"
        />
      ) : (
        <div aria-hidden className="h-10 w-80 max-w-full md:h-8" />
      )}

      <Seam />

      <SegmentedControl
        legend="Board"
        options={VIEWS}
        value={view}
        onChange={(v) => onView(v as BoardView)}
        hideLegend
        variant="bare"
      />

      <Seam />

      {view === "performance" && (
        <>
          <SeasonSelect value={season} options={seasons()} onChange={onSeason} />

          <Seam />

          <SegmentedControl
            legend="Window"
            options={SCOPES}
            value={scope}
            onChange={(v) => onScope(v as Scope)}
            hideLegend
            variant="bare"
          />

          <Seam />
        </>
      )}

      <SegmentedControl
        legend="Position"
        options={POSITIONS}
        value={position ?? "ALL"}
        onChange={(v) => onPosition(v === "ALL" ? null : (v as Position))}
        hideLegend
        variant="bare"
      />

      <SearchField
        label="Find a player on this board"
        placeholder="Find a player"
        value={find}
        onChange={onFind}
        // The URL keeps 60 characters of it (lib/boardParams.ts); the box
        // should not accept more than a refresh would give back.
        maxLength={60}
        className="w-full sm:w-auto sm:min-w-44 sm:flex-1 lg:ml-auto lg:max-w-72"
      />
    </div>
  );
}
