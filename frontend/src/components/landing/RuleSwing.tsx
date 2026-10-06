import { bumpLines } from "@/lib/heroBoard";
import { HERO_BOARD_2025, type Ruleset } from "./previewData";

/** The three stops, left to right: what a catch is worth, low to high (V3's presets). */
const STOPS: { ruleset: Ruleset; perCatch: string }[] = [
  { ruleset: "0 PPR", perCatch: "0" },
  { ruleset: "Half PPR", perCatch: "0.5" },
  { ruleset: "PPR", perCatch: "1" },
];

/** One rank's height on the chart, px. */
const ROW = 21;

/** The story the section tells: the one line in colour, the rest as context. */
const STORY = 16153; // Puka Nacua

/** Names, the chart, names: one grid for the chart and its headings. The chart takes the room. */
const COLUMNS =
  "grid grid-cols-[minmax(0,1fr)_minmax(5.5rem,1fr)_minmax(0,1fr)] sm:grid-cols-[minmax(0,1fr)_minmax(9rem,1.6fr)_minmax(0,1fr)] lg:grid-cols-[minmax(0,1fr)_minmax(0,2.2fr)_minmax(0,1fr)]";

/**
 * One rule, drawn three ways: the top eight of the 2025 PPR board, each
 * followed from where he finished under 0 PPR, through Half PPR, to PPR --
 * the same season and the same stat lines, scored by the three presets, which
 * differ in one rate (`V3__seed_scoring_presets.sql`: only `rec`). A bump
 * chart, because the question is order, not points.
 *
 * Emphasis form: the section's sentence is about Puka Nacua, 20th to 11th to
 * 2nd, so his line is the one in colour and the other seven are context in
 * slate. Position is said in letters beside each name, in a neutral grey: on
 * the daylight page blue is the story and orange the ask, and three position
 * hues at 12px would only add noise to a chart about one line. (Inside the
 * product's own skin the letters keep their hues.)
 *
 * Built from boxes and one stretched SVG for the lines, so the names are
 * ordinary text at any width. A table carries the same ranks for anyone who
 * cannot see the chart.
 */
export function RuleSwing() {
  const rulesets = STOPS.map((s) => s.ruleset);
  const { lines, depth } = bumpLines(HERO_BOARD_2025, rulesets, "PPR");
  const players = lines.map((line) => {
    const p = HERO_BOARD_2025.find((q) => q.playerId === line.playerId)!;
    return {
      id: p.playerId,
      name: p.name,
      last: p.name.split(" ").slice(1).join(" ") || p.name,
      position: p.position,
      ranks: line.ranks,
      story: p.playerId === STORY,
    };
  });
  const y = (rank: number) => (rank - 0.5) * ROW;
  const height = depth * ROW;
  // The story's line last, so it is drawn over the others.
  const drawOrder = players.slice().sort((a, b) => Number(a.story) - Number(b.story));

  return (
    <figure>
      {/* On the same columns as the chart, so each heading stands over its own stop. */}
      <figcaption className={`${COLUMNS} items-end pb-5`}>
        <StopHeading stop={STOPS[0]} className="pr-4 text-right" />
        <div className="relative">
          <StopHeading stop={STOPS[1]} className="absolute bottom-0 left-1/2 w-max -translate-x-1/2 text-center" />
        </div>
        <StopHeading stop={STOPS[2]} className="pl-4" />
      </figcaption>

      <div aria-hidden className={`relative ${COLUMNS}`} style={{ height }}>
        <div className="relative">
          {players.map((p) => (
            <Label key={p.id} top={y(p.ranks[0])} align="right" rank={p.ranks[0]} player={p} />
          ))}
        </div>
        <div className="relative">
          {/* The middle stop's guide: a hairline the lines pass through. */}
          <span className="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-line" />
          <svg
            viewBox={`0 0 200 ${height}`}
            preserveAspectRatio="none"
            className="absolute inset-0 size-full overflow-visible"
            fill="none"
          >
            {drawOrder.map((p) => (
              <polyline
                key={p.id}
                points={p.ranks.map((r, i) => `${i * 100},${y(r)}`).join(" ")}
                stroke={p.story ? "var(--color-energy)" : "var(--color-chart-rest)"}
                strokeWidth={p.story ? 3 : 1.5}
                strokeLinecap="round"
                strokeLinejoin="round"
                vectorEffect="non-scaling-stroke"
              />
            ))}
          </svg>
          {drawOrder.map((p) =>
            p.ranks.map((r, i) => <Dot key={`${p.id}-${i}`} top={y(r)} stop={i} story={p.story} />),
          )}
          {/* The story's middle place, said: the line crosses the others there. */}
          {players
            .filter((p) => p.story)
            .map((p) => (
              <span
                key={p.id}
                className="tabular absolute left-1/2 ml-3 -translate-y-1/2 text-[12px] font-semibold text-energy-text"
                style={{ top: y(p.ranks[1]) }}
              >
                {p.ranks[1]}
              </span>
            ))}
        </div>
        <div className="relative">
          {players.map((p) => (
            <Label key={p.id} top={y(p.ranks[2])} align="left" rank={p.ranks[2]} player={p} />
          ))}
        </div>
      </div>

      {/* sr-only on a wrapper, not the table: a table grows to fit its cells
          whatever its own width says, and one widened a phone's page by 126px
          (Impeccable detector pass, 2026-09-29). */}
      <div className="sr-only">
        <table>
          <caption>2025 finish of the top eight under PPR, under each of the three presets</caption>
          <thead>
            <tr>
              <th scope="col">Player</th>
              {STOPS.map((s) => (
                <th key={s.ruleset} scope="col">
                  {s.ruleset} rank
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {players.map((p) => (
              <tr key={p.id}>
                <th scope="row">
                  {p.name}, {p.position}
                </th>
                {p.ranks.map((r, i) => (
                  <td key={i}>{r}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </figure>
  );
}

function StopHeading({ stop, className }: { stop: (typeof STOPS)[number]; className: string }) {
  return (
    <p className={`leading-tight ${className}`}>
      <span className="block text-[15px] font-semibold text-ink">{stop.ruleset}</span>
      <span className="block text-[13px] text-mute">{stop.perCatch} per catch</span>
    </p>
  );
}

function Label({
  top,
  align,
  rank,
  player,
}: {
  top: number;
  align: "left" | "right";
  rank: number;
  player: { name: string; last: string; position: string; story: boolean };
}) {
  const figure = (
    <span
      className={`tabular w-6 shrink-0 ${align === "right" ? "text-right" : ""} ${player.story ? "font-semibold text-ink" : "text-mute"}`}
    >
      {rank}
    </span>
  );
  const name = (
    <span className={`min-w-0 truncate ${player.story ? "font-semibold text-ink" : "text-mute"}`}>
      {/* Surnames until a column is wide enough for "Christian McCaffrey" whole. */}
      <span className="xl:hidden">{player.last}</span>
      <span className="hidden xl:inline">{player.name}</span>{" "}
      {/* A phone's column fits a surname or a surname and its position, not both whole. */}
      <span className="hidden font-label text-[12px] font-semibold text-faint sm:inline">
        {player.position}
      </span>
    </span>
  );
  return (
    <div
      className={`absolute inset-x-0 flex -translate-y-1/2 items-center gap-2 text-[13px] sm:text-[14px] ${align === "right" ? "justify-end pr-4" : "pl-4"}`}
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
 * A stop's marker: 9px, centred on its stop, with a 2px ring in the page's own
 * ground so crossing lines stay legible.
 */
function Dot({ top, stop, story }: { top: number; stop: number; story: boolean }) {
  const left = ["left-0", "left-1/2", "left-full"][stop];
  return (
    <span
      className={`absolute size-[9px] -translate-x-1/2 -translate-y-1/2 rounded-full ring-2 ring-canvas ${left} ${story ? "bg-energy" : "bg-chart-rest"}`}
      style={{ top }}
    />
  );
}
