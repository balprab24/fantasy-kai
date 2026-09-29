import Link from "next/link";
import { STARTERS, formatPoints, type BoardRow, type Metric, type Quality } from "@/lib/board";
import { Movement } from "../Movement";
import { PlayerAvatar } from "./PlayerAvatar";
import { PositionBadge } from "./PositionBadge";

/**
 * Where each column shows, defined once so a header cell and its body cells
 * cannot disagree. Columns step away in order of importance -- games below
 * `lg`, the secondary figure below `md`, team and movement below `sm` -- so a
 * phone still sees rank, player, position and the number the board is sorted
 * by without scrolling. Below `md` the table can still scroll sideways, with rank
 * and name pinned.
 */
export const COL = {
  // min-w, not just w: table layout will shrink a `w-12` cell to 46.66px, and
  // the pinned player column's `left-12` offset then opens a 1.3px seam.
  rank: "w-12 min-w-12 pr-3 text-right max-md:sticky max-md:left-0 max-md:z-[1]",
  // 13rem floor from sm: a 32px headshot and its gap take 44px of the cell.
  player: "w-60 min-w-40 pr-3 sm:min-w-52 max-md:sticky max-md:left-12 max-md:z-[1]",
  pos: "w-20 pr-3",
  team: "hidden w-16 pr-3 sm:table-cell",
  games: "hidden w-12 pr-3 text-right lg:table-cell",
  // The only column without a width, so it absorbs the slack on a wide screen
  // and the bar becomes a real gauge -- instead of a dead gap opening up
  // between a player's name and their position, which is where the eye travels.
  primary: "min-w-20 pr-3 text-right sm:min-w-32",
  // From md, not lg: on a season board this is points per game, the one
  // figure carrying a quality colour, so a tablet should see it.
  secondary: "hidden w-16 pr-3 text-right md:table-cell",
  move: "hidden w-20 pr-4 text-right sm:table-cell",
} as const;

export const COLUMN_COUNT = Object.keys(COL).length;

const cell = "h-11 bg-raised transition-colors group-hover:bg-surface-2";

/**
 * One player on the board: a single continuous strip rather than a row of
 * separate tiles. The name is the only real anchor -- one Tab stop per row --
 * and its `::after` stretches over the whole strip from `md` up. Below `md`
 * the name cell is pinned, and a sticky cell is a positioned ancestor, so
 * there the click target is the name cell alone.
 */
export function PlayerRow({
  row,
  delta,
  baseline,
  metric,
  href,
  onOpen,
}: {
  row: BoardRow;
  /** Places moved since the previous ruleset; see `Movement`. */
  delta: number | null;
  /** How many rows the previous ruleset's board had loaded. */
  baseline: number;
  metric: Metric;
  href: string;
  /** Called as the player link is followed, so the board can note its place. */
  onOpen?: () => void;
}) {
  const other = metric === "points" ? row.pointsPerGame : row.points;
  return (
    <tr className={`group relative ${delta ? "settled" : ""}`}>
      <td className={`${cell} ${COL.rank} tabular rounded-l-md pl-2 text-mute`}>{row.rank}</td>
      <th scope="row" className={`${cell} ${COL.player} text-left font-normal`}>
        <div className="flex items-center gap-3">
          <PlayerAvatar name={row.name} position={row.position} espnId={row.espnId} />
          <Link
            href={href}
            onClick={onOpen}
            className="truncate font-medium text-ink after:absolute after:inset-0 after:rounded-md focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-energy"
          >
            {row.name}
          </Link>
        </div>
      </th>
      <td className={`${cell} ${COL.pos}`}>
        {/* Raised above the row's link overlay so its tooltip can be reached. */}
        <span className="relative z-[2]">
          <PositionBadge position={row.position} rank={row.posRank} />
        </span>
      </td>
      <td className={`${cell} ${COL.team} text-mute`}>{row.team ?? "—"}</td>
      <td className={`${cell} ${COL.games} tabular text-mute`}>{row.gamesPlayed}</td>
      <td className={`${cell} ${COL.primary} max-sm:rounded-r-md`}>
        <div className="flex items-center justify-end gap-4 pl-4">
          <span
            aria-hidden
            className="hidden h-1 max-w-80 flex-1 overflow-hidden rounded-full bg-white/[0.06] sm:block"
          >
            <span
              className="block h-full rounded-full bg-ink/45"
              style={{ width: `${row.share * 100}%` }}
            />
          </span>
          {metric === "pointsPerGame" ? (
            <PerGame row={row} className="text-[15px] font-semibold" />
          ) : (
            <span className="tabular text-[15px] font-semibold text-ink">
              {formatPoints(row.value)}
            </span>
          )}
        </div>
      </td>
      <td className={`${cell} ${COL.secondary}`}>
        {metric === "points" ? (
          <PerGame row={row} />
        ) : (
          <span className="tabular text-mute">{formatPoints(other)}</span>
        )}
      </td>
      <td className={`${cell} ${COL.move} tabular rounded-r-md`}>
        <span className="relative z-[2]">
          <Movement delta={delta} baseline={baseline} />
        </span>
      </td>
    </tr>
  );
}

/** Full class names, so Tailwind can see them. */
const QUALITY_TEXT: Record<Quality, string> = {
  good: "text-q-good",
  mid: "text-q-mid",
  poor: "text-q-poor",
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
      ? `${row.position} ${row.ppgPosRank} by points per game \u2014 ${QUALITY_WORDS[q]} (a 12-team league starts ${STARTERS[row.position]})`
      : "Too few games on this board to judge the per-game figure";
  return (
    <span title={title} className={`tabular ${q ? QUALITY_TEXT[q] : "text-mute"} ${className}`}>
      {formatPoints(row.pointsPerGame)}
      {q && <span className="sr-only">, {QUALITY_WORDS[q]}</span>}
    </span>
  );
}
