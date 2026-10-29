import Link from "next/link";
import {
  STARTERS,
  formatPoints,
  usageLine,
  type BoardRow,
  type MemberBoardRow,
  type Metric,
  type Quality,
} from "@/lib/board";
import type { RecentWeek } from "@/lib/types";
import { Movement } from "../Movement";
import { PlayerAvatar } from "./PlayerAvatar";
import { PositionBadge } from "./PositionBadge";

/**
 * Where each column shows, defined once so a header cell and its body cells
 * cannot disagree. Columns step away in order of importance -- games below
 * `lg`, team and the gauge below `md`, position and movement below `sm` -- and
 * what steps away from a phone folds into the second line under the name, so
 * at 390px the board fits with no sideways scroll: rank, player, the number
 * the board is sorted by, and the other figure.
 *
 * The rank column's width is the tier letter's too (`TierHeader`), so the
 * letters stand over the ranks they head.
 *
 * These eight are the landing's contract (`HeroBoard` cuts its window from
 * them by position); the member board's extra columns are `MEMBER_COL`.
 */
export const COL = {
  rank: "w-10 pr-2 text-right sm:w-12 sm:pr-3",
  player: "pr-3 sm:w-64 lg:w-72",
  pos: "hidden w-14 pr-3 sm:table-cell",
  team: "hidden w-14 pr-3 md:table-cell",
  games: "hidden w-12 pr-3 text-right lg:table-cell",
  // The only column without a width from md up, so it absorbs a wide
  // screen's slack and its bar becomes a real gauge -- instead of a dead gap
  // opening between a player's name and their position.
  primary: "w-16 pr-2 text-right sm:pr-3 md:w-auto",
  secondary: "w-12 pr-1 text-right sm:w-16 sm:pr-3",
  move: "hidden w-16 pr-2 text-right sm:table-cell",
} as const;

export const COLUMN_COUNT = Object.keys(COL).length;

/**
 * The member board's columns: the same eight, with the player column
 * narrower from md to lg -- the sidebar takes 176px there -- and recent form
 * before the ruleset movement from xl, where there is room for four weeks.
 * Key order is column order.
 */
export const MEMBER_COL = {
  rank: COL.rank,
  player: "pr-3 sm:w-64 md:w-44 lg:w-56 xl:w-72",
  pos: COL.pos,
  team: COL.team,
  games: COL.games,
  primary: COL.primary,
  secondary: COL.secondary,
  recent: "hidden w-28 pr-4 text-right xl:table-cell",
  weeks: "hidden w-44 pr-3 xl:table-cell",
  move: COL.move,
} as const;

export const MEMBER_COLUMN_COUNT = Object.keys(MEMBER_COL).length;

const cell = "h-12 border-b border-line transition-colors group-hover:bg-well sm:h-11";

/**
 * What differs between the two boards a row sits on. The member board's rows
 * grow at xl, where a second line (the player's volume, recent form's
 * direction) joins the name and the recent figure; its gauge waits for lg,
 * because between md and lg the sidebar leaves the primary column too narrow
 * to draw one.
 */
const VARIANT = {
  landing: { row: "", gauge: "md:block", lead: "md:pl-8" },
  member: { row: "xl:[&>*]:h-14", gauge: "lg:block", lead: "lg:pl-8" },
} as const;

interface RowProps {
  /** Places moved since the previous ruleset; see `Movement`. */
  delta: number | null;
  /** How many rows the previous ruleset's board had loaded. */
  baseline: number;
  metric: Metric;
  href?: string;
  /** Called as the player link is followed, so the board can note its place. */
  onOpen?: () => void;
  /** Flash a row that moved (a ruleset switch); off where rows never move, like the landing's slice. */
  settle?: boolean;
  /**
   * The face beside the name. Off on the landing, which shows no likeness, and
   * where a monogram in a circle would say nothing the name does not.
   */
  avatar?: boolean;
  /**
   * The landing's product band follows one row into the player report beside
   * it, and lights that row with the "you are here" tint. Never set in the app.
   */
  lit?: boolean;
}

/**
 * `landing`: the eight `COL` columns, from any `BoardRow`. `member`: the
 * board's ten `MEMBER_COL` columns, from a row of `/rankings/board`.
 */
type PlayerRowProps = RowProps &
  ({ variant?: "landing"; row: BoardRow } | { variant: "member"; row: MemberBoardRow });

/**
 * One player on the board: a line, not a card. The rows sit on the page
 * itself with a hairline between them, so a board reads as one list of
 * players rather than sixty separate boxes.
 *
 * The name is the only real anchor -- one Tab stop per row -- and its
 * `::after` stretches over the whole row, so the row is the click target.
 * Without `href` (the landing page's slice of a board) the name is text.
 */
export function PlayerRow(props: PlayerRowProps) {
  const {
    row,
    delta,
    baseline,
    metric,
    href,
    onOpen,
    settle = true,
    avatar = true,
    lit = false,
  } = props;
  const member = props.variant === "member" ? props.row : null;
  const look = VARIANT[member ? "member" : "landing"];
  const col = member ? MEMBER_COL : COL;
  const other = metric === "points" ? row.pointsPerGame : row.points;
  const moved = delta !== 0;
  const usage = member && usageLine(member.position, member.usage, member.gamesPlayed);
  return (
    <tr
      className={`group relative ${look.row} ${settle && delta ? "settled" : ""} ${lit ? "[&>*]:bg-energy/15" : ""}`}
    >
      <td className={`${cell} ${COL.rank} type-rank text-[15px] text-mute`}>{row.rank}</td>
      <th scope="row" className={`${cell} ${col.player} text-left font-normal`}>
        <div className="flex min-w-0 items-center gap-3">
          {avatar && <PlayerAvatar name={row.name} espnId={row.espnId} />}
          <span className="min-w-0">
            {href ? (
              <Link
                href={href}
                onClick={onOpen}
                className="block truncate text-[15px] font-semibold text-ink after:absolute after:inset-0 focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-energy"
              >
                {row.name}
              </Link>
            ) : (
              <span className="block truncate text-[15px] font-semibold text-ink">{row.name}</span>
            )}
            {/* What a phone's missing columns carry, on one quiet line. No
                team is left out, not called "FA": the data says only that
                none is on record, which is not the same as a free agent.
                On the member board the line stays up to xl for the recent
                figure, until its own column takes it. */}
            <span
              className={`flex items-center gap-1.5 text-[12px] text-mute ${member ? "xl:hidden" : "sm:hidden"}`}
            >
              <span className="contents sm:hidden">
                <PositionBadge position={row.position} rank={row.posRank} />
                {row.team && <span>{row.team}</span>}
                {moved && (
                  <span className="relative z-[2]">
                    <Movement delta={delta} baseline={baseline} />
                  </span>
                )}
              </span>
              {member && member.recentPpg !== null && (
                <span className="tabular whitespace-nowrap">
                  Last 4: {formatPoints(member.recentPpg)} a game
                </span>
              )}
            </span>
            {usage && <span className="hidden text-[12px] text-mute xl:block">{usage}</span>}
          </span>
        </div>
      </th>
      <td className={`${cell} ${COL.pos} text-[13px]`}>
        {/* Raised above the row's link overlay so its tooltip can be reached. */}
        <span className="relative z-[2]">
          <PositionBadge position={row.position} rank={row.posRank} />
        </span>
      </td>
      <td className={`${cell} ${COL.team} text-[13px] text-mute`}>{row.team ?? "—"}</td>
      <td className={`${cell} ${COL.games} tabular text-[13px] text-mute`}>{row.gamesPlayed}</td>
      <td className={`${cell} ${COL.primary}`}>
        <div className={`flex items-center justify-end gap-5 ${look.lead}`}>
          <span aria-hidden className={`hidden h-[3px] flex-1 bg-track ${look.gauge}`}>
            <span className="block h-full bg-mute/60" style={{ width: `${row.share * 100}%` }} />
          </span>
          {metric === "pointsPerGame" ? (
            <PerGame row={row} className="text-[15px] font-semibold" />
          ) : (
            <span className="tabular text-[15px] font-semibold text-ink">{formatPoints(row.value)}</span>
          )}
        </div>
      </td>
      <td className={`${cell} ${COL.secondary} text-[13px]`}>
        {metric === "points" ? (
          <PerGame row={row} />
        ) : (
          <span className="tabular text-mute">{formatPoints(other)}</span>
        )}
      </td>
      {member && (
        <>
          <td className={`${cell} ${MEMBER_COL.recent}`}>
            <RecentForm recentPpg={member.recentPpg} form={member.form} />
          </td>
          <td className={`${cell} ${MEMBER_COL.weeks}`}>
            <div className="flex gap-1">
              {member.recentWeeks.map((w) => (
                <WeekChip key={w.week} week={w} />
              ))}
            </div>
          </td>
        </>
      )}
      <td className={`${cell} ${COL.move} tabular text-[13px]`}>
        <span className="relative z-[2]">
          <Movement delta={delta} baseline={baseline} />
        </span>
      </td>
    </tr>
  );
}

/**
 * Points per game over the last four league weeks, and which way that runs
 * against the whole season's. Direction in words and arrows, never colour:
 * green is taken by quality, and a hot streak is not the same claim.
 */
function RecentForm({ recentPpg, form }: { recentPpg: number | null; form: number | null }) {
  if (recentPpg === null) return <span className="tabular text-mute">—</span>;
  const arrow = form === null ? null : form > 0 ? "↑" : form < 0 ? "↓" : "=";
  return (
    <>
      <span className="tabular text-[15px] font-semibold text-ink">{formatPoints(recentPpg)}</span>
      {form !== null && (
        <span className="block text-[12px] whitespace-nowrap text-mute">
          {arrow} {formatPoints(Math.abs(form))} vs season
        </span>
      )}
    </>
  );
}

/** One league week: its number over the points, or a dash for no recorded game -- never a zero. */
function WeekChip({ week }: { week: RecentWeek }) {
  const points = week.points === null ? null : formatPoints(week.points);
  return (
    <span
      title={`Week ${week.week}: ${points === null ? "no recorded game" : `${points} points`}`}
      className="flex min-w-9 flex-col items-center rounded-[2px] bg-surface px-1 py-1 leading-none"
    >
      <span className="font-label text-[10px] text-faint">W{week.week}</span>
      <span className={`tabular mt-1 text-[12px] ${points === null ? "text-mute" : "text-ink"}`}>
        {points ?? "—"}
      </span>
    </span>
  );
}

/**
 * One hue, not three: green is the only colour a quality gets, and it means
 * "inside a 12-team league's starters at his position" everywhere -- here, in
 * the player's tables and in the weekly chart. The bench range is plain ink
 * and beyond it steps down to faint, so red never means both "RB" and "bad".
 * Full class names, so Tailwind can see them.
 */
export const QUALITY_TEXT: Record<Quality, string> = {
  good: "text-q-good",
  mid: "text-ink",
  poor: "text-faint",
};

const QUALITY_WORDS: Record<Quality, string> = {
  good: "starter range",
  mid: "bench range",
  poor: "below bench range",
};

/**
 * Points per game, coloured by where it stands among players at the same
 * position (`lib/board.ts` `Quality`). The only figure in a row that is
 * coloured by how good it is: rank and total are already said by the rank,
 * the tier and the bar, and movement is deliberately neutral (see
 * `Movement`). The words travel with the colour, as a tooltip and for screen
 * readers; a figure too thin to judge stays plain and says why.
 */
function PerGame({ row, className = "" }: { row: BoardRow; className?: string }) {
  const q = row.quality;
  const title =
    q && row.ppgPosRank !== null
      ? `${row.position} ${row.ppgPosRank} by points per game — ${QUALITY_WORDS[q]} (a 12-team league starts ${STARTERS[row.position]})`
      : "Too few games on this board to judge the per-game figure";
  return (
    <span title={title} className={`tabular ${q ? QUALITY_TEXT[q] : "text-mute"} ${className}`}>
      {formatPoints(row.pointsPerGame)}
      {q && <span className="sr-only">, {QUALITY_WORDS[q]}</span>}
    </span>
  );
}
