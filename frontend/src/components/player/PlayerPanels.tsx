"use client";

import { bandFor, formatPoints } from "@/lib/board";
import { matchup, playoffRound } from "@/lib/player";
import { columnsFor, formatCount, GROUP_LABELS } from "@/lib/playerStats";
import { recentGames } from "@/lib/playerView";
import type { CareerSeason, GamelogWeek } from "@/lib/types";
import { QUALITY_TEXT } from "../rankings/PlayerRow";
import { PositionChip } from "../ui/PositionChip";
import { Skeleton } from "../ui/StatusMessage";

/**
 * A section of the player view: a rounded panel with its name in the display
 * voice, an optional note on what it covers, and its content straight on the
 * panel -- never a box inside the box.
 */
export function Panel({
  title,
  meta,
  children,
  className = "",
  bodyClassName = "px-5 pb-5 sm:px-6 sm:pb-6",
}: {
  title: string;
  meta?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  bodyClassName?: string;
}) {
  return (
    <section aria-label={title} className={`min-w-0 rounded-panel border border-line bg-surface ${className}`}>
      <header className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 px-5 pt-5 pb-4 sm:px-6">
        <h2 className="type-heading text-ink">{title}</h2>
        {meta && <p className="text-[13px] text-mute">{meta}</p>}
      </header>
      <div className={bodyClassName}>{children}</div>
    </section>
  );
}

/** One line of what a game was, in the counts that matter for his position. */
function gameSummary(position: string, w: GamelogWeek): string {
  const s = w.stats;
  const u = w.usage;
  const parts: string[] = [];
  const td = (n: number, what: string) => (n ? `${n} ${what}` : null);
  if (position === "QB") {
    parts.push(`${u.passCmp}/${u.passAtt}`, `${formatCount(s.pass_yd)} yd`);
    for (const p of [td(s.pass_td, s.pass_td === 1 ? "TD" : "TD"), td(s.pass_int, "INT"), s.rush_yd ? `${formatCount(s.rush_yd)} rush yd` : null]) if (p) parts.push(p);
  } else if (position === "RB") {
    parts.push(`${u.rushAtt} car`, `${formatCount(s.rush_yd)} yd`);
    for (const p of [td(s.rush_td + s.rec_td, "TD"), s.rec ? `${s.rec} rec` : null]) if (p) parts.push(p);
  } else {
    parts.push(`${s.rec} rec`, `${formatCount(s.rec_yd)} yd`);
    for (const p of [td(s.rec_td + s.rush_td, "TD"), u.targets ? `${u.targets} tgt` : null]) if (p) parts.push(p);
  }
  return parts.join(", ");
}

/**
 * His last five games, newest first: opponent, the points under the page's
 * scoring, where that ranked among his position that week, and the counts
 * behind it. Playoff games are listed (the log is a record) but carry no
 * weekly rank -- the API ranks the regular season only.
 */
export function RecentGames({
  weeks,
  position,
  season,
  scored,
  loading,
  stale,
}: {
  weeks: GamelogWeek[] | undefined;
  position: string;
  season: CareerSeason | null;
  scored: boolean;
  loading: boolean;
  stale: boolean;
}) {
  if (loading || !weeks) {
    return (
      <div className="space-y-2" aria-hidden>
        {Array.from({ length: 5 }, (_, i) => (
          <Skeleton key={i} className="h-14" />
        ))}
      </div>
    );
  }
  if (weeks.length === 0) return <p className="py-4 text-[15px] text-mute">No games recorded this season.</p>;
  const rank = new Map(season?.weeks.map((w) => [w.week, w.posRank]) ?? []);
  const games = recentGames(weeks);
  return (
    <ol className={`-mx-2 transition-opacity ${stale ? "opacity-60" : ""}`}>
      {games.map((w) => {
        const round = playoffRound(w.seasonType);
        const r = w.seasonType === "REG" ? (rank.get(w.week) ?? null) : null;
        const q = bandFor(r, position);
        return (
          <li key={`${w.season}-${w.week}`} className="flex items-center gap-3 rounded-control px-2 py-2.5 hover:bg-well">
            <span className="w-11 shrink-0 text-center">
              <span className="block font-label text-[11px] font-semibold text-faint">{round ? w.seasonType : "Week"}</span>
              <span className="type-figure block text-[18px] text-ink">{w.week}</span>
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-[14px] font-semibold text-ink">{matchup(w.opponent, w.home)}</span>
              <span className="block truncate text-[12px] text-mute">{gameSummary(position, w)}</span>
            </span>
            <span className="shrink-0 text-right">
              <span className="tabular block text-[17px] font-semibold text-ink">{scored ? formatPoints(w.points) : "—"}</span>
              {r !== null ? (
                <span className={`block text-[12px] font-semibold tabular ${q ? QUALITY_TEXT[q] : "text-mute"}`} title={`${position}${r} that week`}>
                  {position}
                  {r}
                </span>
              ) : (
                <span className="block text-[12px] text-faint">{round ?? "—"}</span>
              )}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

/** A column's name inside its group's heading, where "Passing" is already said. */
const SHORT: Record<string, string> = {
  Cmp: "Completions",
  Att: "Attempts",
  Yd: "Yards",
  TD: "Touchdowns",
  Int: "Interceptions",
  Car: "Carries",
  Tgt: "Targets",
  Rec: "Receptions",
  FL: "Fumbles lost",
  "2PT": "2-pt conversions",
  "Ret TD": "Return TDs",
};

/**
 * The season's box score, by group, in the order that matters for his
 * position (`columnsFor`: a quarterback's passing first, a receiver's
 * receiving). Counts as the API sent them; nothing here is a point.
 */
export function SeasonLine({ position, season }: { position: string; season: CareerSeason }) {
  const groups = columnsFor(position, [season]);
  return (
    <div className="grid gap-x-8 gap-y-6 sm:grid-cols-2">
      {groups.map((g) => (
        <div key={g.group} className="min-w-0">
          <h3 className="text-[13px] font-semibold text-mute">{GROUP_LABELS[g.group]}</h3>
          <dl className="mt-2 flex flex-wrap gap-x-6 gap-y-3 border-t border-line pt-3">
            {g.columns.map((c) => {
              const v = c.value(season);
              return (
                <div key={c.key} className="flex min-w-[4.5rem] flex-col-reverse" title={c.title}>
                  <dt className="text-[12px] text-mute">{SHORT[c.label] ?? c.title}</dt>
                  <dd className={`type-figure text-[24px] ${v === 0 ? "text-faint" : "text-ink"}`}>{formatCount(v)}</dd>
                </div>
              );
            })}
          </dl>
        </div>
      ))}
    </div>
  );
}

/**
 * Every regular season he has, as bars of points per game under the page's
 * scoring, newest first, each with its positional rank. The selected season is
 * lit; choosing another makes it the page's season.
 */
export function CareerSnapshot({
  seasons,
  position,
  selected,
  onSelect,
  scored,
  stale,
}: {
  seasons: CareerSeason[];
  position: string;
  selected: number | null;
  onSelect: (season: number) => void;
  scored: boolean;
  stale: boolean;
}) {
  const max = Math.max(1, ...seasons.map((s) => s.pointsPerGame));
  return (
    <ul className={`-mx-2 transition-opacity ${stale ? "opacity-60" : ""}`}>
      {seasons.map((s) => {
        const chosen = s.season === selected;
        return (
          <li key={s.season}>
            <button
              type="button"
              onClick={() => onSelect(s.season)}
              aria-pressed={chosen}
              className={`grid w-full grid-cols-[3rem_minmax(0,1fr)_3.25rem_3.5rem] items-center gap-3 rounded-control px-2 py-2.5 text-left transition-colors ${
                chosen ? "bg-lift" : "hover:bg-well"
              }`}
            >
              <span className={`type-figure text-[16px] ${chosen ? "text-energy-text" : "text-ink"}`}>{s.season}</span>
              <span aria-hidden className="h-2 overflow-hidden rounded-full bg-track">
                <span
                  className={`block h-full rounded-full ${chosen ? "bg-energy" : "bg-mute/50"}`}
                  style={{ width: `${scored ? (Math.max(0, s.pointsPerGame) / max) * 100 : 0}%` }}
                />
              </span>
              <span className="tabular text-right text-[14px] font-semibold text-ink" title="Points per game">
                {scored ? formatPoints(s.pointsPerGame) : "—"}
              </span>
              <span className="flex justify-end">
                {s.posRank !== null ? <PositionChip position={position} rank={s.posRank} basis={`by ${s.season} season points`} /> : <span className="text-faint">—</span>}
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
