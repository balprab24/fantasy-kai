import { positionHue } from "@/components/rankings/PositionBadge";
import { formatPoints, toBoardRows } from "@/lib/board";
import { PPR_2025 } from "./previewData";

/** Rows shown: enough to be the board, few enough to sit beside a form. */
const ROWS = 5;

/**
 * The product, still, beside the sign-in and register forms: the top of the
 * real 2025 PPR board in its own dark skin (`.primetime`), the same rows the
 * landing's hero lands on. Nothing moves and nothing is pressable -- it says
 * what the form opens, so the page is not a form alone on an empty field.
 * Decorative (`aria-hidden`): the form beside it is the page.
 */
export function StillPlate() {
  const rows = toBoardRows(PPR_2025).slice(0, ROWS);
  return (
    <div aria-hidden className="primetime overflow-hidden rounded-plate bg-canvas text-ink select-none">
      <p className="bg-surface px-5 py-3.5 text-[13px] text-mute">
        Scored under <span className="font-semibold text-ink">PPR</span> for the 2025 season, by season total.
      </p>
      <ol className="px-3 pb-2">
        {rows.map((row) => (
          <li key={row.playerId} className="flex h-11 items-center border-b border-line last:border-b-0">
            <span className="type-rank w-9 shrink-0 pr-3 text-right text-[15px] text-mute">{row.rank}</span>
            <span className="min-w-0 flex-1 truncate pl-2 text-[15px] font-semibold text-ink">
              {row.name}
            </span>
            <span className="w-12 shrink-0 text-[12px] text-mute">{row.team}</span>
            <span className="tabular w-11 shrink-0 text-[13px] font-semibold">
              <span className={positionHue(row.position).text}>{row.position}</span>
              <span className="text-ink">{row.posRank}</span>
            </span>
            <span className="tabular w-16 shrink-0 pr-2 text-right text-[15px] font-semibold text-ink">
              {formatPoints(row.points)}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}
