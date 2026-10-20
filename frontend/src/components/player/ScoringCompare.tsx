"use client";

import { formatPoints } from "@/lib/board";
import { matchup } from "@/lib/player";
import { profileCaption, profileLabel } from "@/lib/profiles";
import type { CareerResponse, ScoringProfile } from "@/lib/types";
import { PositionChip } from "../ui/PositionChip";
import { Skeleton, StatusMessage } from "../ui/StatusMessage";

interface Scored {
  profile: ScoringProfile;
  data: CareerResponse | undefined;
  loading: boolean;
  error: boolean;
}

/**
 * The product's argument, for one player: the same season's stat lines, scored
 * by every ruleset the member can use, side by side. Each row is the career
 * endpoint's answer for that ruleset -- the scoring engine's, the same the
 * board would show -- so the differences are real, not re-derived here. The
 * difference column is a subtraction of two of those answers, nothing more.
 *
 * Choosing a row makes it the page's scoring.
 */
export function ScoringCompare({
  season,
  position,
  scored,
  selectedId,
  onSelect,
}: {
  season: number;
  position: string;
  scored: Scored[];
  selectedId: number | null;
  onSelect: (id: number) => void;
}) {
  const rows = scored.map((s) => ({ ...s, season: s.data?.seasons.find((x) => x.season === season) ?? null }));
  const ready = rows.filter((r) => r.season);
  const base = rows.find((r) => r.profile.id === selectedId)?.season ?? null;
  const max = Math.max(1, ...ready.map((r) => r.season!.points));
  const failed = rows.some((r) => r.error);
  const anyLoading = rows.some((r) => r.loading);

  if (!anyLoading && ready.length === 0 && !failed) {
    return (
      <StatusMessage tone="info" title={`No regular-season games in ${season}.`}>
        Pick another season above to compare rulesets.
      </StatusMessage>
    );
  }

  // Every week any ruleset has, from the selected ruleset's own list (all rulesets
  // score the same games; only the points differ).
  const weeks = (base ?? ready[0]?.season)?.weeks ?? [];

  return (
    <div className="space-y-5">
      {failed && (
        <StatusMessage tone="error" title="Some rulesets could not be scored.">
          The rows below are the ones that answered. Reload to try the rest.
        </StatusMessage>
      )}

      <section aria-labelledby="ladder-title" className="rounded-panel border border-line bg-surface">
        <header className="px-5 pt-5 sm:px-6">
          <h3 id="ladder-title" className="type-heading text-ink">
            One season, every ruleset
          </h3>
          <p className="mt-2 max-w-[60ch] text-[14px] text-mute">
            The same {season} stat lines, scored by each ruleset you can use. Choose one to score the
            whole page with it.
          </p>
        </header>
        <ul className="mt-4 border-t border-line">
          {rows.map(({ profile, season: s, loading }) => {
            const chosen = profile.id === selectedId;
            const diff = s && base && !chosen ? s.points - base.points : null;
            return (
              <li key={profile.id} className="border-b border-line last:border-b-0">
                <button
                  type="button"
                  onClick={() => onSelect(profile.id)}
                  aria-pressed={chosen}
                  className={`grid w-full grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-2 px-5 py-4 text-left transition-colors sm:grid-cols-[minmax(0,12rem)_minmax(0,1fr)_5.5rem_5.5rem_4.5rem] sm:px-6 ${
                    chosen ? "bg-lift shadow-[inset_3px_0_0_var(--color-energy)]" : "hover:bg-well"
                  } last:rounded-b-panel`}
                >
                  <span className="min-w-0 sm:col-start-1 sm:row-start-1">
                    <span className={`block truncate text-[15px] ${chosen ? "font-semibold text-ink" : "font-medium text-ink"}`}>
                      {profileLabel(profile)}
                    </span>
                    <span className={`block truncate text-[12px] ${chosen ? "text-mute" : "text-faint"}`}>
                      {profileCaption(profile)}
                    </span>
                  </span>
                  <span aria-hidden className="col-span-2 row-start-2 h-2 overflow-hidden rounded-full bg-track sm:col-span-1 sm:col-start-2 sm:row-start-1">
                    {s && (
                      <span
                        className={`block h-full rounded-full transition-[width] duration-500 ${chosen ? "bg-energy" : "bg-mute/50"}`}
                        style={{ width: `${(Math.max(0, s.points) / max) * 100}%` }}
                      />
                    )}
                  </span>
                  <span className="text-right sm:col-start-3 sm:row-start-1">
                    {loading ? (
                      <Skeleton className="ml-auto h-5 w-14" />
                    ) : s && scored ? (
                      <span className="tabular text-[17px] font-semibold text-ink">{formatPoints(s.points)}</span>
                    ) : (
                      <span className="text-faint">—</span>
                    )}
                    <span className="flex justify-end gap-2 text-[12px] text-mute sm:hidden">
                      {s?.posRank != null && <span>{`${position}${s.posRank}`}</span>}
                      {diff !== null && <span className="tabular">{signed(diff)}</span>}
                    </span>
                  </span>
                  <span className="hidden text-right text-[13px] tabular sm:col-start-4 sm:row-start-1 sm:block">
                    {chosen ? (
                      <span className="text-mute">Chosen</span>
                    ) : diff !== null ? (
                      <span className={diff > 0 ? "text-ink" : "text-mute"} title={`Points against ${profileLabel(rows.find((r) => r.profile.id === selectedId)!.profile)}`}>
                        {signed(diff)}
                      </span>
                    ) : (
                      <span className="text-faint">—</span>
                    )}
                  </span>
                  <span className="hidden justify-end sm:col-start-5 sm:row-start-1 sm:flex">
                    {s?.posRank != null ? <PositionChip position={position} rank={s.posRank} basis={`by ${season} season points`} size="md" /> : <span className="text-faint">—</span>}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      </section>

      {weeks.length > 0 && ready.length > 1 && (
        <section aria-labelledby="weekly-compare-title" className="rounded-panel border border-line bg-surface">
          <header className="px-5 pt-5 sm:px-6">
            <h3 id="weekly-compare-title" className="type-heading text-ink">
              Week by week
            </h3>
            <p className="mt-2 text-[14px] text-mute">Each game, scored every way. The chosen ruleset is lit.</p>
          </header>
          <div className="mt-4 overflow-x-auto rounded-b-panel">
            <table className="w-full border-separate border-spacing-0 text-[14px]">
              <caption className="sr-only">Fantasy points by week under each ruleset, {season} regular season</caption>
              <thead>
                <tr className="font-label text-[12px] text-mute [&>th]:h-10 [&>th]:border-y [&>th]:border-line [&>th]:px-3 [&>th]:font-semibold [&>th]:whitespace-nowrap">
                  <th scope="col" className="sticky left-0 z-[1] bg-surface pl-5 text-left sm:pl-6">
                    Wk
                  </th>
                  <th scope="col" className="text-left">Opp</th>
                  {ready.map((r) => (
                    <th
                      key={r.profile.id}
                      scope="col"
                      className={`text-right last:pr-5 sm:last:pr-6 ${r.profile.id === selectedId ? "text-ink" : ""}`}
                    >
                      {profileLabel(r.profile)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {weeks.map((w) => (
                  <tr key={w.week} className="group [&>td]:h-10 [&>td]:border-b [&>td]:border-line [&>td]:px-3 hover:[&>td]:bg-well">
                    <td className="tabular sticky left-0 z-[1] bg-surface pl-5 text-mute group-hover:bg-well sm:pl-6">{w.week}</td>
                    <td className="whitespace-nowrap text-mute">{matchup(w.opponent, w.home)}</td>
                    {ready.map((r) => {
                      const p = r.season!.weeks.find((x) => x.week === w.week)?.points;
                      const chosen = r.profile.id === selectedId;
                      return (
                        <td
                          key={r.profile.id}
                          className={`tabular text-right last:pr-5 sm:last:pr-6 ${chosen ? "bg-energy/[0.08] font-semibold text-ink" : "text-ink"}`}
                        >
                          {p === undefined ? <span className="text-faint">—</span> : formatPoints(p)}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

function signed(n: number) {
  if (Math.abs(n) < 0.05) return "±0.0";
  return `${n > 0 ? "+" : "−"}${formatPoints(Math.abs(n))}`;
}
