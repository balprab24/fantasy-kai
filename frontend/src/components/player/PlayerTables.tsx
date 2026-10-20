"use client";

import { ApiError } from "@/lib/api";
import { formatPoints } from "@/lib/board";
import { matchup, playoffRound } from "@/lib/player";
import { columnsFor, type StatSource } from "@/lib/playerStats";
import { HEAT_INVERTED, HEAT_PER_GAME, HEAT_SEASONS, HEAT_WEEK } from "@/lib/heatBands";
import { finishStep, statStep, type HeatStep } from "@/lib/playerView";
import type { CareerSeason, GamelogWeek, StatKey, Usage } from "@/lib/types";
import { Skeleton, StatusMessage } from "../ui/StatusMessage";
import { NEUTRAL_TILE, StatTable, TILE, type LeadColumn } from "./StatTable";

/**
 * A fantasy tile's ground, by where the figure finished at his position: 1st
 * to 12th green, 13th to 24th yellow, 25th to 36th orange, below that red --
 * a 12-team league's first, second and third string, and the rest.
 */
export function finishTone(rank: number | null): string {
  const step = finishStep(rank);
  return step === null ? NEUTRAL_TILE : TILE[step];
}

/** A box-score figure's step against the league's games, or null where the position has no cut-off for it. */
function leagueStep(table: typeof HEAT_WEEK, position: string, key: string, value: number): HeatStep | null {
  const cuts = table[position]?.[key];
  return cuts ? statStep(value, cuts, HEAT_INVERTED.includes(key)) : null;
}

/** What the colours in a heat table mean: shown above the game log and the career. */
export function HeatLegend({ position, per }: { position: string; per: "game" | "season" }) {
  const relevant = position === "QB" ? 24 : position === "RB" ? 48 : position === "WR" ? 72 : 24;
  const swatch = (step: HeatStep, label: string) => (
    <span className="inline-flex items-center gap-1.5">
      <span aria-hidden className={`size-2.5 rounded-[2px] ${TILE[step]} ring-1 ring-inset ring-ink/10`} />
      {label}
    </span>
  );
  return (
    <div className="flex flex-col gap-1.5 text-[12px] text-mute">
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="text-ink">{per === "game" ? "Points and rank, where the week finished" : "Rank and points, where the season finished"} among {position}s:</span>
        {swatch(0, "1–12")}
        {swatch(1, "13–24")}
        {swatch(2, "25–36")}
        {swatch(3, "37+")}
      </p>
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="text-ink">
          {per === "game" ? "Box score, against every game" : "Box score per game, against every season"} by the top {relevant} {position}s, {HEAT_SEASONS.replace("-", "–")}:
        </span>
        {swatch(0, "top quarter")}
        {swatch(1, "second")}
        {swatch(2, "third")}
        {swatch(3, "bottom")}
      </p>
    </div>
  );
}

/**
 * The player view's two long tables -- the game log and the career -- and the
 * pieces they share. One `StatTable` underneath both, so their box-score
 * columns line up the same way (`columnsFor`).
 */

function games(n: number) {
  return `${n} ${n === 1 ? "game" : "games"}`;
}

export function CareerError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <StatusMessage
      tone="error"
      title="This player's seasons could not be scored."
      action={{ label: "Try again", onClick: onRetry }}
    >
      {error instanceof ApiError ? error.message : "The API did not answer. Check that it is running."}
    </StatusMessage>
  );
}

export function TableSkeleton({ rows }: { rows: number }) {
  return (
    <div role="status" aria-label="Loading" className="space-y-1">
      <Skeleton className="h-12" />
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-8" />
      ))}
    </div>
  );
}

/** A week of the log, or the regular-season line beneath it. */
type LogRow = { kind: "week"; week: GamelogWeek } | { kind: "season"; season: CareerSeason };

export function GameLog({
  position,
  scored,
  weeks,
  loading,
  error,
  onRetry,
  season,
  stale,
}: {
  position: string;
  /** False for a position v1 does not score: points read "—", not 0.0. */
  scored: boolean;
  weeks: GamelogWeek[] | undefined;
  loading: boolean;
  error: unknown;
  onRetry: () => void;
  /** The career's regular season: weekly ranks, and the line under the log. */
  season: CareerSeason | null;
  stale: boolean;
}) {
  if (error) {
    return (
      <StatusMessage tone="error" title="The game log could not be loaded." action={{ label: "Try again", onClick: onRetry }}>
        {error instanceof ApiError ? error.message : "The API did not answer. Check that it is running."}
      </StatusMessage>
    );
  }
  if (loading || !weeks) return <TableSkeleton rows={6} />;
  if (weeks.length === 0) {
    return <p className="py-6 text-sm text-mute">No games recorded this season.</p>;
  }

  const weeklyRank = new Map(season?.weeks.map((w) => [w.week, w.posRank]) ?? []);
  // The API ranks the regular season only: a playoff game has no weekly rank.
  const rankOf = (w: GamelogWeek) => (w.seasonType === "REG" ? (weeklyRank.get(w.week) ?? null) : null);
  const traded = new Set(weeks.map((w) => w.team)).size > 1;
  const rows: LogRow[] = weeks.map((week) => ({ kind: "week", week }));

  const lead: LeadColumn<LogRow>[] = [
    {
      key: "wk",
      label: "Wk",
      title: "Week",
      group: "Game",
      align: "left",
      width: "w-16",
      cell: (r) => {
        if (r.kind !== "week") return null;
        const round = playoffRound(r.week.seasonType);
        return round ? (
          <span title={`${round} playoff game`}>
            {r.week.week} <span className="text-[11px] text-q-mid">{r.week.seasonType}</span>
          </span>
        ) : (
          r.week.week
        );
      },
    },
    {
      key: "opp",
      label: "Opp",
      title: "Opponent",
      group: "Game",
      align: "left",
      width: "w-24",
      cell: (r) =>
        r.kind === "week" ? (
          matchup(r.week.opponent, r.week.home)
        ) : (
          <span className="text-mute">{games(r.season.gamesPlayed)}</span>
        ),
    },
    ...(traded
      ? [
          {
            key: "team",
            label: "Team",
            title: "His team that week",
            group: "Game",
            align: "left" as const,
            cell: (r: LogRow) => (r.kind === "week" ? r.week.team : r.season.teams.join("/")),
          },
        ]
      : []),
    {
      key: "pts",
      label: "Pts",
      title: "Fantasy points under the selected scoring",
      group: "Fantasy",
      tone: (r) => (scored && r.kind === "week" ? finishTone(rankOf(r.week)) : undefined),
      cell: (r) =>
        scored ? (
          <span className="tabular font-semibold">
            {formatPoints(r.kind === "week" ? r.week.points : r.season.points)}
          </span>
        ) : (
          <span className="text-faint">—</span>
        ),
    },
    {
      key: "rank",
      label: "Rank",
      title: `Place among every ${position} that week; the season row is by season points. Playoff games are not ranked.`,
      group: "Fantasy",
      tone: (r) => (r.kind === "week" ? finishTone(rankOf(r.week)) : undefined),
      cell: (r) =>
        r.kind === "week" ? (
          <RankCell position={position} rank={rankOf(r.week)} />
        ) : (
          <RankCell position={position} rank={r.season.posRank} />
        ),
    },
    {
      key: "snap",
      label: "Snap%",
      title: "Share of the offense's snaps he was on the field for",
      group: "Fantasy",
      tone: (r) => {
        if (r.kind !== "week" || r.week.snapPct === null) return undefined;
        const step = leagueStep(HEAT_WEEK, position, "snap", r.week.snapPct);
        return step === null ? undefined : TILE[step];
      },
      cell: (r) =>
        r.kind === "week" && r.week.snapPct !== null ? (
          <span className="tabular">{Math.round(r.week.snapPct)}%</span>
        ) : (
          <span className="text-faint">—</span>
        ),
    },
  ];

  return (
    <div className={`transition-opacity ${stale ? "opacity-60" : ""}`}>
      <StatTable
        caption="Game log: every game this season, with fantasy points under the selected scoring and the box score"
        lead={lead}
        groups={columnsFor(position, weeks)}
        rows={rows}
        source={(r) => (r.kind === "week" ? r.week : r.season)}
        rowKey={(r) => (r.kind === "week" ? `${r.week.season}-${r.week.week}` : "season")}
        shade={(key, value, r) => (r.kind === "week" ? leagueStep(HEAT_WEEK, position, key, value) : null)}
        rowProps={(r) =>
          r.kind === "week" && r.week.seasonType !== "REG" ? { className: "[&>td]:text-mute" } : {}
        }
        // Not the sum of the rows above when playoff rows are there, so it says
        // which season it is -- short, because it sits in the pinned column.
        foot={season ? { row: { kind: "season", season }, label: "Reg. season" } : undefined}
      />
    </div>
  );
}

/** A season's box score divided by its games: per-game counts for the career's "Per game" view. */
function perGame(s: CareerSeason): StatSource {
  const g = Math.max(1, s.gamesPlayed);
  const stats = Object.fromEntries(Object.entries(s.stats).map(([k, v]) => [k, v / g])) as Record<StatKey, number>;
  const usage = Object.fromEntries(Object.entries(s.usage).map(([k, v]) => [k, v / g])) as unknown as Usage;
  return { stats, usage };
}

export type CareerMode = "game" | "total";

export function CareerTable({
  position,
  scored,
  seasons,
  selected,
  onSelect,
  stale,
  mode = "total",
}: {
  position: string;
  scored: boolean;
  seasons: CareerSeason[];
  selected: number | null;
  onSelect: (season: number) => void;
  stale: boolean;
  /** Season totals, or every count divided by games played. Points are the API's either way. */
  mode?: CareerMode;
}) {
  const lead: LeadColumn<CareerSeason>[] = [
    {
      key: "season",
      label: "Season",
      group: "",
      align: "left",
      cell: (s) => (
        <button
          type="button"
          onClick={() => onSelect(s.season)}
          aria-pressed={s.season === selected}
          title={`Show ${s.season} above`}
          className={`tabular rounded-[2px] px-1 -mx-1 underline-offset-4 hover:text-energy-text hover:underline ${
            s.season === selected ? "font-semibold text-energy-text" : "text-ink"
          }`}
        >
          {s.season}
        </button>
      ),
    },
    {
      key: "team",
      label: "Team",
      group: "",
      align: "left",
      cell: (s) => <span className="text-mute">{s.teams.join("/")}</span>,
    },
    {
      key: "age",
      label: "Age",
      title: "Age on 1 September of the season",
      group: "",
      cell: (s) =>
        s.age !== null ? <span className="tabular text-mute">{s.age}</span> : <span className="text-faint">—</span>,
    },
    {
      key: "gp",
      label: "GP",
      title: "Regular-season games played",
      group: "",
      cell: (s) => <span className="tabular">{s.gamesPlayed}</span>,
    },
    {
      key: "rank",
      label: "Rank",
      title: `Place among every ${position} by season points`,
      group: "Fantasy",
      tone: (s) => finishTone(s.posRank),
      cell: (s) => <RankCell position={position} rank={s.posRank} />,
    },
    ...(mode === "total"
      ? [
          {
            key: "pts",
            label: "Pts",
            title: "Fantasy points under the selected scoring",
            group: "Fantasy",
            tone: (s: CareerSeason) => (scored ? finishTone(s.posRank) : undefined),
            cell: (s: CareerSeason) =>
              scored ? (
                <span className="tabular font-semibold">{formatPoints(s.points)}</span>
              ) : (
                <span className="text-faint">—</span>
              ),
          },
        ]
      : []),
    {
      key: "ppg",
      label: "PPG",
      title: "Fantasy points per game",
      group: "Fantasy",
      tone: (s) => (scored ? finishTone(s.posRank) : undefined),
      cell: (s) =>
        scored ? (
          <span className={`tabular ${mode === "game" ? "font-semibold" : ""}`}>{formatPoints(s.pointsPerGame)}</span>
        ) : (
          <span className="text-faint">—</span>
        ),
    },
  ];

  return (
    <div className={`transition-opacity ${stale ? "opacity-60" : ""}`}>
      <StatTable
        caption={`Career: every regular season on record, scored under the selected ruleset${mode === "game" ? ", box score per game" : ""}`}
        lead={lead}
        groups={columnsFor(position, seasons)}
        rows={seasons}
        source={mode === "game" ? perGame : (s) => s}
        // Coloured by the per-game rate in both views, so a short season's
        // totals are not marked down for the games it has not played yet.
        shade={(key, value, s) =>
          leagueStep(HEAT_PER_GAME, position, key, mode === "game" ? value : value / Math.max(1, s.gamesPlayed))
        }
        rowKey={(s) => String(s.season)}
        rowProps={(s) =>
          s.season === selected
            ? {
                // The season the page is on: an energy edge on its pinned cell.
                className: "[&>td:first-child]:shadow-[inset_3px_0_0_var(--color-energy)]",
                "aria-current": true,
              }
            : {}
        }
      />
    </div>
  );
}

/**
 * "RB3", in the tile's own text colour -- the tile carries the band -- with it in
 * words in the tooltip, so the colour is never the only cue.
 */
export function RankCell({ position, rank }: { position: string; rank: number | null }) {
  if (rank === null) return <span className="text-faint">—</span>;
  const step = finishStep(rank);
  const words = step === null ? "" : ["1st to 12th", "13th to 24th", "25th to 36th", "37th or lower"][step];
  return (
    <span title={`${position}${rank}${words ? ` — ${words} among ${position}s` : ""}`} className="tabular font-semibold">
      {position}
      {rank}
    </span>
  );
}
