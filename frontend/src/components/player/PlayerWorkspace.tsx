"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect } from "react";
import { ApiError } from "@/lib/api";
import { bandFor, formatPoints, type Quality } from "@/lib/board";
import { clearBoardReturn, readBoardReturn } from "@/lib/boardReturn";
import { matchup, pickSeason, playoffRound } from "@/lib/player";
import { columnsFor } from "@/lib/playerStats";
import { profileLabel } from "@/lib/profiles";
import { useCareer, useGamelog, usePlayer, useSelectedProfile } from "@/lib/queries";
import { FIRST_SEASON, currentSeason } from "@/lib/season";
import type { CareerSeason, GamelogWeek } from "@/lib/types";
import { RulesetSwitch } from "../RulesetSwitch";
import { SegmentedControl } from "../ui/SegmentedControl";
import { Skeleton, StatusMessage } from "../ui/StatusMessage";
import { PlayerIdentity } from "./PlayerIdentity";
import { StatTable, type LeadColumn } from "./StatTable";
import { WeeklyChart } from "./WeeklyChart";

const RANKED = ["QB", "RB", "WR", "TE"];

const QUALITY_TEXT: Record<Quality, string> = {
  good: "text-q-good",
  mid: "text-q-mid",
  poor: "text-q-poor",
};

function games(n: number) {
  return `${n} ${n === 1 ? "game" : "games"}`;
}

function int(raw: string | null): number | null {
  return raw !== null && /^[1-9][0-9]{0,9}$/.test(raw) ? Number(raw) : null;
}

/**
 * A player, as a fantasy asset: who he is, what his season is worth under
 * your scoring, how it went week by week, and every season before it.
 *
 * Three requests. The player, a primary-key read. The career, one per
 * (player, profile), which feeds the identity tiles, the chart and the career
 * table and carries every rank on the page. The game log, one per season, a
 * primary-key read. Switching season refetches only the log; switching scoring
 * refetches the career and the log.
 *
 * Season and scoring live in the URL, written with `replaceState`: a refresh
 * or a shared link opens the same view, and back goes to wherever you came
 * from rather than through every season you looked at.
 */
export function PlayerWorkspace({ playerId }: { playerId: number }) {
  const params = useSearchParams();
  const pathname = usePathname();
  const now = currentSeason();

  const requestedSeason = int(params.get("season"));
  const validSeason =
    requestedSeason !== null && requestedSeason >= FIRST_SEASON && requestedSeason <= now
      ? requestedSeason
      : null;
  const { profiles, profileId } = useSelectedProfile(int(params.get("profileId")));

  const player = usePlayer(playerId);
  const career = useCareer(playerId, profileId);
  const seasons = career.data?.seasons ?? [];
  const regular = seasons.map((s) => s.season);
  // Every season with a game, so one reached only in January still has a
  // switch -- its log shows the playoff games, its tiles say "no regular season".
  const played = career.data?.seasonsPlayed ?? regular;
  const season = career.data
    ? pickSeason(validSeason, regular.length ? regular : played, now)
    : validSeason;
  const gamelog = useGamelog(playerId, profileId, season);

  const selected = seasons.find((s) => s.season === season) ?? null;
  const profile = profiles.data?.find((p) => p.id === profileId);
  const scoringLabel = profile ? profileLabel(profile) : "…";

  // Leaving the site from here (another origin, a closed tab, a reload) means
  // the board is no longer the page before this one, so its "come back to me"
  // note is dropped -- or "← Rankings" would go back to wherever that was.
  // In-app navigation never fires pagehide, so the board's own return survives.
  useEffect(() => {
    window.addEventListener("pagehide", clearBoardReturn);
    return () => window.removeEventListener("pagehide", clearBoardReturn);
  }, []);

  const write = (change: Record<string, string>) => {
    const next = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(change)) next.set(key, value);
    window.history.replaceState(null, "", `${pathname}?${next}`);
  };

  if (player.error || playerId <= 0) {
    const missing =
      playerId <= 0 || (player.error instanceof ApiError && player.error.status === 404);
    return (
      <div className="mx-auto max-w-[1320px] px-4 pt-5 pb-10 sm:px-6 lg:px-8">
        <BackToBoard season={validSeason} profileId={profileId} now={now} />
        <div className="mt-4">
          <StatusMessage
            tone={missing ? "info" : "error"}
            title={missing ? "No player with that id." : "The player could not be loaded."}
            action={missing ? undefined : { label: "Try again", onClick: () => void player.refetch() }}
          >
            {missing
              ? "The link may be out of date. Every player on the board links to their own page."
              : player.error instanceof ApiError
                ? player.error.message
                : "The API did not answer. Check that it is running."}
          </StatusMessage>
        </div>
      </div>
    );
  }

  // Without the profile list no ruleset resolves, and every scored panel would
  // wait on a request that is never sent. Say so, with a way out.
  if (profiles.error) {
    return (
      <div className="mx-auto max-w-[1320px] px-4 pt-5 pb-10 sm:px-6 lg:px-8">
        <BackToBoard season={validSeason} profileId={null} now={now} />
        <div className="mt-4">
          <StatusMessage
            tone="error"
            title="The scoring rulesets could not be loaded."
            action={{ label: "Try again", onClick: () => void profiles.refetch() }}
          >
            {profiles.error instanceof ApiError
              ? profiles.error.message
              : "The API did not answer. Check that it is running."}
          </StatusMessage>
        </div>
      </div>
    );
  }

  const position = player.data?.position ?? "";
  const ranked = RANKED.includes(position);
  const careerStale = career.isPlaceholderData;

  return (
    <div className="mx-auto max-w-[1320px] px-4 pt-4 pb-10 sm:px-6 lg:px-8">
      <BackToBoard season={validSeason} profileId={profileId} now={now} />

      <div className="mt-3 flex flex-wrap items-center gap-2">
        {played.length > 0 && season !== null && (
          <SegmentedControl
            legend="Season"
            hideLegend
            options={played.map((s) => ({ value: String(s), label: String(s) }))}
            value={String(season)}
            onChange={(v) => write({ season: v })}
          />
        )}
        {profiles.data && (
          <RulesetSwitch
            profiles={profiles.data}
            selected={profileId}
            onSelect={(id) => write({ profileId: String(id) })}
            hideLegend
          />
        )}
      </div>

      <div className="mt-3 grid items-start gap-4 xl:grid-cols-[320px_minmax(0,1fr)]">
        <aside className="min-w-0 xl:sticky xl:top-4">
          {player.data ? (
            <PlayerIdentity
              player={player.data}
              season={season}
              selected={selected}
              careerLoading={career.isLoading || (profileId === null && !career.data)}
              scoringLabel={scoringLabel}
              stale={careerStale}
              scored={ranked}
            />
          ) : (
            <Skeleton className="h-[248px] rounded-xl" />
          )}
        </aside>

        <div className="min-w-0 space-y-4">
          <Panel title="Performance" meta={selected ? games(selected.gamesPlayed) : undefined}>
            {!ranked && player.data && (
              <p className="mb-3 text-sm text-mute">
                fantasy-kai scores and ranks QB, RB, WR and TE. {position} lines are shown as recorded,
                without positional ranks.
              </p>
            )}
            {career.error ? (
              <CareerError error={career.error} onRetry={() => void career.refetch()} />
            ) : !career.data ? (
              <Skeleton className="h-[214px] rounded-lg" />
            ) : selected && !ranked ? null : selected ? (
              <WeeklyChart
                key={`${selected.season}-${profileId}`}
                season={selected}
                position={position}
                scoringLabel={scoringLabel}
                stale={careerStale}
              />
            ) : (
              <p className="py-8 text-center text-sm text-mute">
                {seasons.length === 0
                  ? "No regular-season games on record."
                  : `No regular-season games in ${season}. Pick another season above.`}
              </p>
            )}
          </Panel>

          <Panel
            title="Game log"
            meta={season !== null ? `${season} · every game, playoffs included` : undefined}
          >
            <GameLog
              position={position}
              scored={ranked}
              // No season to show once the career is back means no games at all:
              // an empty log, not a skeleton waiting on a request never sent.
              weeks={season === null && career.data ? [] : gamelog.data?.weeks}
              loading={gamelog.isLoading}
              error={gamelog.error}
              onRetry={() => void gamelog.refetch()}
              season={selected}
              stale={gamelog.isPlaceholderData}
            />
          </Panel>

          <Panel title="Career" meta="Regular season · newest first">
            {career.error ? (
              <CareerError error={career.error} onRetry={() => void career.refetch()} />
            ) : !career.data ? (
              <TableSkeleton rows={4} />
            ) : seasons.length === 0 ? (
              <p className="py-6 text-center text-sm text-mute">No regular-season games on record.</p>
            ) : (
              <CareerTable
                position={position}
                scored={ranked}
                seasons={seasons}
                selected={season}
                onSelect={(s) => write({ season: String(s) })}
                stale={careerStale}
              />
            )}
          </Panel>
        </div>
      </div>
    </div>
  );
}

/**
 * Back to the board. When the board is the page this one was opened from --
 * it noted itself on the way out -- this is the browser's back, so the board
 * comes back exactly as it was left, scroll included. Otherwise (a shared link,
 * a new tab) it is a plain link to the board for this season and scoring.
 */
function BackToBoard({
  season,
  profileId,
  now,
}: {
  season: number | null;
  profileId: number | null;
  now: number;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const search = new URLSearchParams();
  if (season !== null && season !== now) search.set("season", String(season));
  if (profileId !== null) search.set("profileId", String(profileId));
  const query = search.toString();
  const href = `/rankings${query ? `?${query}` : ""}`;

  return (
    <Link
      href={href}
      onClick={(e) => {
        const saved = readBoardReturn();
        if (saved && saved.player === pathname && window.history.length > 1) {
          e.preventDefault();
          router.back();
        }
      }}
      className="inline-flex items-center gap-1.5 text-sm text-mute hover:text-ink"
    >
      <span aria-hidden>←</span> Rankings
    </Link>
  );
}

function Panel({
  title,
  meta,
  children,
}: {
  title: string;
  meta?: string;
  children: React.ReactNode;
}) {
  return (
    <section aria-label={title} className="rounded-xl border border-line bg-raised">
      <header className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5 border-b border-line px-4 py-2.5">
        <h2 className="font-display text-[15px] font-bold tracking-[-0.01em]">{title}</h2>
        {/* Not tabular: this is prose, and the tabular comma reads as a space
            before it (see globals.css). */}
        {meta && <p className="text-xs text-faint">{meta}</p>}
      </header>
      <div className="p-4">{children}</div>
    </section>
  );
}

function CareerError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
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

function TableSkeleton({ rows }: { rows: number }) {
  return (
    <div role="status" aria-label="Loading" className="space-y-1">
      <Skeleton className="h-12 rounded" />
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-8 rounded" />
      ))}
    </div>
  );
}

/** A week of the log, or the regular-season line beneath it. */
type LogRow = { kind: "week"; week: GamelogWeek } | { kind: "season"; season: CareerSeason };

function GameLog({
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
    return <p className="py-6 text-center text-sm text-mute">No games recorded this season.</p>;
  }

  const weeklyRank = new Map(season?.weeks.map((w) => [w.week, w.posRank]) ?? []);
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
      cell: (r) =>
        scored ? (
          <span className="tabular font-semibold text-ink">
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
      cell: (r) =>
        r.kind === "week" ? (
          <RankCell
            position={position}
            rank={r.week.seasonType === "REG" ? (weeklyRank.get(r.week.week) ?? null) : null}
          />
        ) : (
          <RankCell position={position} rank={r.season.posRank} />
        ),
    },
    {
      key: "snap",
      label: "Snap%",
      title: "Share of the offense's snaps he was on the field for",
      group: "Fantasy",
      cell: (r) =>
        r.kind === "week" && r.week.snapPct !== null ? (
          <span className="tabular text-mute">{Math.round(r.week.snapPct)}%</span>
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

function CareerTable({
  position,
  scored,
  seasons,
  selected,
  onSelect,
  stale,
}: {
  position: string;
  scored: boolean;
  seasons: CareerSeason[];
  selected: number | null;
  onSelect: (season: number) => void;
  stale: boolean;
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
          className={`tabular rounded px-1 -mx-1 underline-offset-4 hover:text-energy-text hover:underline ${
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
      cell: (s) => <RankCell position={position} rank={s.posRank} />,
    },
    {
      key: "pts",
      label: "Pts",
      title: "Fantasy points under the selected scoring",
      group: "Fantasy",
      cell: (s) =>
        scored ? (
          <span className="tabular font-semibold text-ink">{formatPoints(s.points)}</span>
        ) : (
          <span className="text-faint">—</span>
        ),
    },
    {
      key: "ppg",
      label: "PPG",
      title: "Fantasy points per game",
      group: "Fantasy",
      cell: (s) =>
        scored ? (
          <span className="tabular">{formatPoints(s.pointsPerGame)}</span>
        ) : (
          <span className="text-faint">—</span>
        ),
    },
  ];

  return (
    <div className={`transition-opacity ${stale ? "opacity-60" : ""}`}>
      <StatTable
        caption="Career: every regular season on record, scored under the selected ruleset"
        lead={lead}
        groups={columnsFor(position, seasons)}
        rows={seasons}
        source={(s) => s}
        rowKey={(s) => String(s.season)}
        rowProps={(s) =>
          s.season === selected
            ? {
                // An edge, not a fill: a translucent fill on the pinned first
                // cell would let the columns scrolling under it show through.
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
 * "RB3", coloured by where it falls against the position's starter line --
 * the board's quality colours, on the same 12-team assumption -- with the
 * words in the tooltip so the colour is never the only cue.
 */
function RankCell({ position, rank }: { position: string; rank: number | null }) {
  if (rank === null) return <span className="text-faint">—</span>;
  const quality = bandFor(rank, position);
  const words =
    quality === "good" ? "starter range" : quality === "mid" ? "bench range" : "below bench range";
  return (
    <span title={`${position}${rank} — ${words}`} className={`tabular ${quality ? QUALITY_TEXT[quality] : ""}`}>
      {position}
      {rank}
    </span>
  );
}
