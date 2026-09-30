import { positionHue } from "@/components/rankings/PositionBadge";
import { PPR_VS_ZERO_2025 } from "./previewData";

/** Ranks on the chart, top to bottom: 1st down to the lowest 0 PPR place among the six. */
const DEPTH = Math.max(...PPR_VS_ZERO_2025.map((p) => p.zeroPprRank));
const ROW = 24;

/** The story the section tells: the one line in colour, the rest as context. */
const STORY = 16153; // Puka Nacua

/**
 * One rule, drawn: the top six of the 2025 PPR board, each joined to where
 * the same player finished under 0 PPR -- same season, same stat lines, a
 * point per catch added. Emphasis form: the section's sentence is about Puka
 * Nacua, 20th to 2nd, so his line is the one in colour and the other five are
 * context in grey. Position is said in letters beside each name, never by the
 * line's colour (the four position hues fail colour-blind separation as marks).
 *
 * Built from boxes and one stretched SVG for the lines, so the names are
 * ordinary text at any width. A table carries the same ranks for anyone who
 * cannot see the chart.
 */
export function RuleSwing() {
  const players = PPR_VS_ZERO_2025.map(({ row, zeroPprRank }) => ({
    id: row.playerId,
    name: row.name,
    last: row.name.split(" ").slice(1).join(" ") || row.name,
    position: row.position,
    ppr: row.rank,
    zero: zeroPprRank,
  }));
  const y = (rank: number) => (rank - 0.5) * ROW;
  const height = DEPTH * ROW;

  return (
    <figure>
      <figcaption className="flex items-baseline justify-between gap-4 pb-4 text-[13px] text-mute">
        <span className="font-semibold text-ink">Without the point per catch</span>
        <span className="font-semibold text-ink">With it</span>
      </figcaption>
      <div aria-hidden className="relative grid grid-cols-[minmax(0,1fr)_minmax(2.25rem,0.6fr)_minmax(0,1fr)] sm:grid-cols-[minmax(0,1fr)_minmax(3.5rem,1.1fr)_minmax(0,1fr)]" style={{ height }}>
        {/* Left: where each finished under 0 PPR. */}
        <div className="relative">
          {players.map((p) => (
            <Label key={p.id} top={y(p.zero)} align="right" rank={p.zero} player={p} story={p.id === STORY} />
          ))}
        </div>
        <div className="relative">
          <svg
            viewBox={`0 0 100 ${height}`}
            preserveAspectRatio="none"
            className="absolute inset-0 size-full overflow-visible"
            fill="none"
          >
            {players
              .slice()
              // The story's line last, so it is drawn over the others.
              .sort((a, b) => Number(a.id === STORY) - Number(b.id === STORY))
              .map((p) => (
                <line
                  key={p.id}
                  x1={0}
                  y1={y(p.zero)}
                  x2={100}
                  y2={y(p.ppr)}
                  stroke={p.id === STORY ? "var(--color-energy)" : "var(--color-chart-rest)"}
                  strokeWidth={p.id === STORY ? 2.5 : 1.5}
                  strokeLinecap="round"
                  vectorEffect="non-scaling-stroke"
                />
              ))}
          </svg>
          {players.map((p) => (
            <span key={`d${p.id}`}>
              <Dot top={y(p.zero)} side="left" story={p.id === STORY} />
              <Dot top={y(p.ppr)} side="right" story={p.id === STORY} />
            </span>
          ))}
        </div>
        {/* Right: the same players under PPR. */}
        <div className="relative">
          {players.map((p) => (
            <Label key={p.id} top={y(p.ppr)} align="left" rank={p.ppr} player={p} story={p.id === STORY} />
          ))}
        </div>
      </div>

      {/* sr-only on a wrapper, not the table: a table grows to fit its cells
          whatever its own width says, and this one widened a phone's page by
          126px (Impeccable detector pass, 2026-09-29). */}
      <div className="sr-only">
        <table>
          <caption>2025 finish of the top six under PPR, with and without the point per catch</caption>
          <thead>
            <tr>
              <th scope="col">Player</th>
              <th scope="col">0 PPR rank</th>
              <th scope="col">PPR rank</th>
            </tr>
          </thead>
          <tbody>
            {players.map((p) => (
              <tr key={p.id}>
                <th scope="row">
                  {p.name}, {p.position}
                </th>
                <td>{p.zero}</td>
                <td>{p.ppr}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </figure>
  );
}

function Label({
  top,
  align,
  rank,
  player,
  story,
}: {
  top: number;
  align: "left" | "right";
  rank: number;
  player: { name: string; last: string; position: string };
  story: boolean;
}) {
  const hue = positionHue(player.position).text;
  const figure = <span className={`tabular w-6 shrink-0 ${align === "right" ? "text-right" : ""} ${story ? "text-ink" : "text-mute"}`}>{rank}</span>;
  const name = (
    <span className={`min-w-0 truncate ${story ? "font-semibold text-ink" : "text-mute"}`}>
      <span className="sm:hidden">{player.last}</span>
      <span className="hidden sm:inline">{player.name}</span>{" "}
      <span className={`font-label text-[11px] font-semibold ${hue}`}>{player.position}</span>
    </span>
  );
  return (
    <div
      className={`absolute inset-x-0 flex -translate-y-1/2 items-center gap-2 text-[13px] ${align === "right" ? "justify-end pr-3" : "pl-3"}`}
      style={{ top }}
    >
      {align === "right" ? (
        <>
          {name}
          {figure}
        </>
      ) : (
        <>
          {figure}
          {name}
        </>
      )}
    </div>
  );
}

/**
 * An end marker: 10px, centred on the column's edge, with a 2px ring in the
 * section's own `surface` so crossing lines stay legible.
 */
function Dot({ top, side, story }: { top: number; side: "left" | "right"; story: boolean }) {
  return (
    <span
      className={`absolute size-2.5 -translate-y-1/2 rounded-full ring-2 ring-surface ${story ? "bg-energy" : "bg-chart-rest"} ${side === "left" ? "-left-[5px]" : "-right-[5px]"}`}
      style={{ top }}
    />
  );
}
