"use client";

import { useState } from "react";
import { RulesetSwitch } from "@/components/RulesetSwitch";
import { RankingsBoard } from "@/components/RankingsBoard";
import { useRankings, useSelectedProfile } from "@/lib/queries";
import type { Position, Scope } from "@/lib/types";

const SEASONS = [2026, 2025, 2024, 2023, 2022, 2021, 2020];
const POSITIONS: (Position | null)[] = [null, "QB", "RB", "WR", "TE"];
const SCOPES: { value: Scope; label: string }[] = [
  { value: "season", label: "Season total" },
  { value: "per_game", label: "Per game" },
  { value: "last4", label: "Last 4 weeks" },
];
const PAGE_SIZE = 50;

export default function RankingsPage() {
  const { profiles, profileId, setProfileId: choose } = useSelectedProfile();

  // Filters and page live in one object so that changing a filter resets the
  // page in the same update. Page 7 of quarterbacks is not page 7 of everyone,
  // and an effect that corrected it afterwards would render the wrong page
  // first and then fix it.
  const [filters, setFilters] = useState<{
    season: number;
    position: Position | null;
    scope: Scope;
    page: number;
  }>({ season: 2025, position: null, scope: "season", page: 0 });

  const { season, position, scope, page } = filters;

  function refine(patch: Partial<typeof filters>) {
    setFilters((prev) => ({ ...prev, ...patch, page: patch.page ?? 0 }));
  }

  function setProfileId(id: number) {
    choose(id);
    setFilters((prev) => ({ ...prev, page: 0 }));
  }

  const rankings = useRankings({ profileId, season, position, scope, page, size: PAGE_SIZE });
  const total = rankings.data?.total ?? 0;
  const totalPages = rankings.data?.totalPages ?? 0;

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      <h1 className="font-display text-2xl font-bold tracking-tight">Rankings</h1>

      <div className="mt-6 space-y-4">
        {profiles.data && (
          <RulesetSwitch profiles={profiles.data} selected={profileId} onSelect={setProfileId} />
        )}

        <div className="flex flex-wrap items-center gap-x-6 gap-y-3 text-sm">
          <label className="flex items-center gap-2">
            <span className="text-mute">Season</span>
            <select
              value={season}
              onChange={(e) => refine({ season: Number(e.target.value) })}
              className="rounded border border-line-strong bg-raised px-2 py-1.5"
            >
              {SEASONS.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </label>

          <label className="flex items-center gap-2">
            <span className="text-mute">Scoring window</span>
            <select
              value={scope}
              onChange={(e) => refine({ scope: e.target.value as Scope })}
              className="rounded border border-line-strong bg-raised px-2 py-1.5"
            >
              {SCOPES.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </label>

          <div className="flex items-center gap-1">
            {POSITIONS.map((p) => (
              <button
                key={p ?? "all"}
                type="button"
                onClick={() => refine({ position: p })}
                aria-pressed={position === p}
                className={
                  position === p
                    ? "rounded bg-ink px-2.5 py-1 text-white"
                    : "rounded px-2.5 py-1 text-mute hover:bg-field-soft hover:text-ink"
                }
              >
                {p ?? "All"}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="mt-8">
        <RankingsBoard
          rows={rankings.data?.content}
          comparisonKey={String(profileId)}
          season={season}
          profileId={profileId}
          loading={rankings.isLoading}
        />
      </div>

      {totalPages > 1 && (
        <nav className="mt-6 flex items-center justify-between text-sm" aria-label="Pagination">
          <button
            type="button"
            onClick={() => refine({ page: Math.max(0, page - 1) })}
            disabled={page === 0}
            className="rounded border border-line-strong px-3 py-1.5 disabled:opacity-40"
          >
            Previous
          </button>
          <span className="tabular text-mute">
            {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, total)} of {total}
          </span>
          <button
            type="button"
            onClick={() => refine({ page: Math.min(totalPages - 1, page + 1) })}
            disabled={page >= totalPages - 1}
            className="rounded border border-line-strong px-3 py-1.5 disabled:opacity-40"
          >
            Next
          </button>
        </nav>
      )}

      <p className="mt-8 max-w-[68ch] text-sm leading-relaxed text-mute">
        A smaller page does not mean less work. Sorting is by computed points, so every player-week
        in the season is scored before a page can be taken — the page size changes what you are
        sent, not what was calculated.
      </p>
    </div>
  );
}
