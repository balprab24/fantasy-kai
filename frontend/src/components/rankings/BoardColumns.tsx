/** Both header and body use this grid so horizontal scrolling keeps labels aligned. */
export const COL = {
  rank: "sticky left-0 z-[3] bg-surface px-2 text-right",
  // Pinned beside the rank below lg, where the table scrolls: a phone scrolling to the weeks
  // still sees whose they are. Not from lg: a sticky cell becomes the containing block of the
  // name link's row-wide ::after, shrinking the click target from the row to this cell.
  player: "max-lg:sticky max-lg:left-10 max-lg:z-[3] max-lg:bg-surface pr-3",
  pos: "pr-2",
  team: "pr-2",
  games: "pr-2 text-right",
  primary: "pr-3 text-right",
  secondary: "pr-3 text-right",
  recent: "pr-3 text-right",
  form: "pr-3 text-right",
  detail: "px-3 text-right",
  usage: "pr-3 text-right",
  move: "pr-3 text-right",
} as const;

export function boardTableClass(view: "overview" | "usage", showMove: boolean) {
  // Sized so the lg board (1024 wide, beside the rail) fits without scrolling.
  const width = view === "usage"
    ? showMove ? "min-w-[964px]" : "min-w-[900px]"
    : showMove ? "min-w-[944px]" : "min-w-[880px]";
  return `w-full table-fixed border-separate border-spacing-0 text-sm ${width}`;
}

export function BoardColumns({ view, showMove }: { view: "overview" | "usage"; showMove: boolean }) {
  return (
    <colgroup>
      <col className="w-10" />
      <col className="w-[156px] sm:w-[240px]" />
      {/* Remaining width is shared across individual stats, not swallowed by the final cell. */}
      <col span={7} />
      {view === "usage" ? <col span={3} /> : <col className="w-[200px]" />}
      {showMove && <col className="w-16" />}
    </colgroup>
  );
}
