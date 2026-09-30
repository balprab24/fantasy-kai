/**
 * The roadmap, in words a visitor can use -- north-star §10's phases 6 to 10,
 * in that order, with no dates. None of it is built, and the section that
 * holds this list says so before it starts; the rail inside the product
 * (`components/shell/nav.ts`) shows the same items as plainly unavailable.
 */
const NEXT: { name: string; what: string }[] = [
  {
    name: "Projections",
    what: "A projected stat line for every player, every week, scored by your rules like everything else.",
  },
  {
    name: "League import",
    what: "Bring in your ESPN or Sleeper league, signed in as you.",
  },
  {
    name: "Start/sit and waivers",
    what: "Lineup and pickup calls made against your roster and your scoring.",
  },
  {
    name: "Trades",
    what: "Valued in expected wins for your team, not a points number stuck on each player.",
  },
  {
    name: "Market consensus",
    what: "Where real drafts and real rosters put each player. The market's view, never an expert's.",
  },
  {
    name: "iPhone app",
    what: "The same product, built native.",
  },
];

/**
 * One column, beside the last ask: a short spec sheet of what is not built
 * yet, name then line, rather than a grid of feature tiles.
 */
export function ComingNext() {
  return (
    <dl className="mt-5 border-t border-line">
      {NEXT.map((item) => (
        <div
          key={item.name}
          className="grid gap-x-6 gap-y-0.5 border-b border-line py-3 sm:grid-cols-[10.5rem_minmax(0,1fr)]"
        >
          <dt className="text-[15px] font-semibold text-ink">{item.name}</dt>
          <dd className="text-[14px] leading-relaxed text-mute">{item.what}</dd>
        </div>
      ))}
    </dl>
  );
}
