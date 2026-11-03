/** Both header and body use this grid so horizontal scrolling keeps labels aligned. */
export const COL = {
  rank: "sticky left-0 z-[3] bg-surface px-2 text-right",
  player: "pr-3",
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
  const width = view === "usage"
    ? showMove ? "min-w-[1184px]" : "min-w-[1120px]"
    : showMove ? "min-w-[1104px]" : "min-w-[1040px]";
  return `w-full table-fixed border-separate border-spacing-0 text-sm ${width}`;
}

export function BoardColumns({ view, showMove }: { view: "overview" | "usage"; showMove: boolean }) {
  return (
    <colgroup>
      <col className="w-10" />
      <col className="w-[240px]" />
      {/* Remaining width is shared across individual stats, not swallowed by the final cell. */}
      <col span={7} />
      {view === "usage" ? <col span={3} /> : <col className="w-[200px]" />}
      {showMove && <col className="w-16" />}
    </colgroup>
  );
}
