import { positionHue } from "@/components/rankings/PositionBadge";
import { formatPoints } from "@/lib/board";
import type { RankingRow } from "@/lib/types";
import {
  HALF_PPR_TOP20_2025,
  HERO_BOARD_2025,
  PPR_2025,
  PRESET_RATES,
  ZERO_PPR_TOP20_2025,
  type Ruleset,
} from "./previewData";

/** The three boards, left to right: what a catch is worth, low to high (V3's presets). */
const STOPS: Ruleset[] = ["0 PPR", "Half PPR", "PPR"];

/** The story the section tells: the one player followed across all three. */
const STORY = HERO_BOARD_2025.find((p) => p.playerId === 16153)!; // Puka Nacua

/** How far down each board runs: to the story's lowest place, so every place is at its real depth. */
const DEPTH = Math.max(...STOPS.map((r) => STORY.by[r].rank));

/** The places each board sets in ink; the rest of the way down to `DEPTH` is set quietly. */
const HEAD = 8;

/**
 * Below sm, the places a board names at all, besides the story's row: sixty names in three
 * 105px columns read as a report. The rest of the depth stays, as a rail ticked every fifth
 * place, so a row's height is still its rank and the climb is still drawn true.
 */
const PHONE_HEAD = 5;

/** Each board's rows, captured (`previewData.ts`); `npm test` holds every one to ranks 1 to `DEPTH`. */
const BOARDS: Record<Ruleset, RankingRow[]> = {
  "0 PPR": ZERO_PPR_TOP20_2025,
  "Half PPR": HALF_PPR_TOP20_2025,
  PPR: PPR_2025.slice(0, DEPTH),
};

/** "Christian McCaffrey" -> "McCaffrey". */
const surname = (name: string) => name.split(" ").slice(1).join(" ") || name;

/**
 * One rule, three boards, side by side: the same 2025 season and the same stat
 * lines, ranked by the three presets, which differ in one rate
 * (`V3__seed_scoring_presets.sql`: only `rec`). Each board runs down to 20th,
 * the depth the story starts from -- the top eight in ink, where the letters
 * say who leads (quarterbacks fill the 0 PPR board and drain out of the PPR
 * one), and the rest set quietly, so the reader sees who the story passes.
 * Every row is one place, the same height on all three, so how high a row
 * stands is its rank. A phone names only the top five and the story, and
 * keeps the depth as a rail (`PHONE_HEAD`).
 *
 * The story is one player: Puka Nacua, 20th, then 11th, then 2nd. His row is
 * lit on each board (Lift, so its letters are ink: no position hue reaches
 * 4.5:1 there) and one blue line carries him across the gaps between them, so
 * the climb is seen before anything is read. No other lines: the other
 * players' moves are in the boards themselves.
 *
 * Drawn as lists in the page's own Daylight skin, not as the product's table:
 * on the landing the product appears in its dark skin only in the hero and the
 * product band (DESIGN.md, the Island Rule). A table carries the same places
 * for anyone who cannot see the figure.
 */
export function ThreeBoards() {
  // In rank units, so the line lands on its rows whatever height a row is given.
  const y = (rank: number) => rank - 0.5;

  return (
    <figure className="[--gut:18px] [--pitch:26px] sm:[--gut:40px] sm:[--pitch:28px] lg:[--gut:72px] lg:[--pitch:30px]">
      <div
        aria-hidden
        className="grid grid-cols-[minmax(0,1fr)_var(--gut)_minmax(0,1fr)_var(--gut)_minmax(0,1fr)]"
      >
        {STOPS.map((ruleset, i) => (
          <div key={ruleset} className="border-b-2 border-ink pb-2.5 sm:pb-3" style={{ gridColumn: i * 2 + 1 }}>
            <p className="type-heading text-[1.25rem] text-ink sm:text-[1.5rem]">{ruleset}</p>
            <p className="mt-1 text-[12px] text-mute sm:text-[13px]">{PRESET_RATES[ruleset].rec} per catch</p>
          </div>
        ))}

        {STOPS.map((ruleset, i) => (
          <Board key={ruleset} ruleset={ruleset} column={i * 2 + 1} />
        ))}
        {/* Between two boards, the story's line, from his row on the left to his row on the right. */}
        {[0, 1].map((i) => {
          const [from, to] = [STORY.by[STOPS[i]].rank, STORY.by[STOPS[i + 1]].rank];
          return (
            <div key={i} className="relative" style={{ gridColumn: i * 2 + 2, gridRow: 2 }}>
              <svg
                viewBox={`0 0 100 ${DEPTH}`}
                preserveAspectRatio="none"
                className="absolute inset-0 size-full overflow-visible"
                fill="none"
              >
                <path
                  d={`M0 ${y(from)} C50 ${y(from)} 50 ${y(to)} 100 ${y(to)}`}
                  stroke="var(--color-energy)"
                  strokeWidth={3}
                  strokeLinecap="round"
                  vectorEffect="non-scaling-stroke"
                />
              </svg>
            </div>
          );
        })}
      </div>

      {/* sr-only on a wrapper, not the table: a table grows to fit its cells
          whatever its own width says, and one widened a phone's page by 126px
          (Impeccable detector pass, 2026-09-29). */}
      <div className="sr-only">
        <table>
          <caption>
            The top {DEPTH} of the 2025 season under each of the three presets. Puka Nacua is{" "}
            {STOPS.map((r) => `${STORY.by[r].rank} under ${r}`).join(", ")}.
          </caption>
          <thead>
            <tr>
              <th scope="col">Place</th>
              {STOPS.map((r) => (
                <th key={r} scope="col">
                  {r}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: DEPTH }, (_, i) => (
              <tr key={i}>
                <th scope="row">{i + 1}</th>
                {STOPS.map((r) => {
                  const row = BOARDS[r][i];
                  return (
                    <td key={r}>
                      {row.name}, {row.position}, {formatPoints(row.points)}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </figure>
  );
}

function Board({ ruleset, column }: { ruleset: Ruleset; column: number }) {
  return (
    <ol style={{ gridColumn: column, gridRow: 2 }}>
      {BOARDS[ruleset].map((row) => {
        const story = row.playerId === STORY.playerId;
        const head = row.rank <= HEAD;
        // The board's head in ink; below it, quiet -- unless it is the story's row.
        const loud = head || story;
        // A phone names less: the top few and the story. The rest of the depth is a rail.
        const named = story || row.rank <= PHONE_HEAD;
        return (
          <li
            key={row.rank}
            className={`h-[var(--pitch)] border-line ${
              row.rank <= PHONE_HEAD ? "border-b" : head ? "sm:border-b" : ""
            } ${story ? "bg-lift" : ""}`}
          >
            {/* Inset from the right edge until the points column carries it, so the line
                leaving a row never runs into its letters. */}
            <div
              className={`${named ? "flex" : "hidden sm:flex"} h-full items-center gap-1 pr-1.5 sm:gap-2.5 md:pr-0`}
            >
              <span
                className={`type-rank w-5 shrink-0 text-right sm:w-7 ${
                  story
                    ? "text-[13px] font-bold text-energy-text sm:text-[15px]"
                    : loud
                      ? "text-[13px] text-mute sm:text-[15px]"
                      : "text-[12px] text-faint sm:text-[13px]"
                }`}
              >
                {row.rank}
              </span>
              <span
                className={`min-w-0 flex-1 truncate font-label ${
                  loud
                    ? `text-[13px] text-ink sm:text-[15px] ${story ? "font-bold" : "font-semibold"}`
                    : "text-[12px] font-medium text-faint sm:text-[13px]"
                }`}
              >
                {/* Surnames until a column is wide enough for "Christian McCaffrey" whole. */}
                <span className="xl:hidden">{surname(row.name)}</span>
                <span className="hidden xl:inline">{row.name}</span>
              </span>
              <span
                className={`shrink-0 font-label font-semibold ${
                  story
                    ? "text-[11px] text-ink sm:text-[12px]"
                    : head
                      ? `text-[11px] sm:text-[12px] ${positionHue(row.position).text}`
                      : "text-[11px] text-faint"
                }`}
              >
                {row.position}
              </span>
              <span
                className={`tabular hidden w-12 shrink-0 pr-2 text-right md:inline ${
                  story ? "text-[14px] font-semibold text-ink" : loud ? "text-[14px] text-mute" : "text-[13px] text-faint"
                }`}
              >
                {formatPoints(row.points)}
              </span>
            </div>
            {!named && (
              // The phone's rail: a place on the board, unnamed, ticked every fifth place.
              <div className="relative h-full sm:hidden">
                <span className="absolute inset-y-0 left-[9px] w-px bg-line-strong" />
                {row.rank % 5 === 0 && (
                  <>
                    <span className="absolute top-1/2 left-[9px] h-px w-1.5 bg-line-strong" />
                    <span className="type-rank absolute top-1/2 left-[19px] -translate-y-1/2 text-[11px] text-faint">
                      {row.rank}
                    </span>
                  </>
                )}
              </div>
            )}
          </li>
        );
      })}
    </ol>
  );
}
