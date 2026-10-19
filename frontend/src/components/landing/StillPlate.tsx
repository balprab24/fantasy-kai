import { formatPoints, toBoardRows } from "@/lib/board";
import { PositionChip } from "../ui/PositionChip";
import { HERO_SEASON, HERO_THROUGH_WEEK, MIXER_BOARDS } from "./heroData";

/** Rows shown: enough to be the board, few enough to sit beside a form. */
const ROWS = 6;

/**
 * The product, still, beside the sign-in and register forms: the top of the
 * season's real Half PPR board, the one the landing's hero opens on. Nothing
 * moves and nothing is pressable -- it says what the form opens, so the page
 * is not a form alone in an empty field. Decorative (`aria-hidden`): the form
 * beside it is the page.
 */
export function StillPlate() {
  const rows = toBoardRows(MIXER_BOARDS["0.5|4"]).slice(0, ROWS);
  return (
    <div aria-hidden className="overflow-hidden rounded-plate border border-line bg-surface text-ink shadow-float select-none">
      <div className="flex items-end justify-between gap-4 border-b border-line px-6 py-5">
        <span className="type-heading text-[1.75rem]">Rankings</span>
        <span className="text-[13px] text-mute">
          Half PPR, {HERO_SEASON} through week {HERO_THROUGH_WEEK}
        </span>
      </div>
      <ol>
        {rows.map((row) => (
          <li key={row.playerId} className="flex h-[3.25rem] items-center gap-3 border-b border-line px-4 last:border-b-0">
            <span className="type-figure w-8 shrink-0 text-right text-[16px] text-mute">{row.rank}</span>
            <span className="min-w-0 flex-1 truncate pl-1 text-[15px] font-semibold">{row.name}</span>
            <PositionChip position={row.position} rank={row.posRank} />
            <span className="w-11 shrink-0 text-[13px] text-mute">{row.team}</span>
            <span className="tabular w-16 shrink-0 text-right text-[15px] font-semibold">{formatPoints(row.points)}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}
