/**
 * The roadmap, in words a visitor can use -- north-star §10's phases 6 to 10,
 * in that order, with no dates. None of it is built, and the section that
 * holds this list says so before it starts; inside the product, the top bar's
 * "Coming" menu (`components/shell/nav.ts`) lists what is unbuilt as plainly
 * unavailable.
 */
const NEXT: { name: string; what: string }[] = [
  { name: "Projections", what: "Each week ahead, scored your way." },
  { name: "League import", what: "Your ESPN or Sleeper league." },
  { name: "Start/sit and waivers", what: "Calls made against your roster." },
  { name: "Trades", what: "Valued in expected wins." },
  { name: "Market consensus", what: "Real drafts and rosters. No experts." },
  { name: "iPhone app", what: "The same product, built native." },
];

/**
 * What is not built yet, kept small on purpose: a short rundown of names, each
 * with one line, in plain text between hairlines rather than a grid of
 * feature tiles. It is a footnote to the product, not a pitch of its own.
 */
export function ComingNext() {
  return (
    <ul className="grid grid-cols-2 gap-x-5 sm:gap-x-8 md:grid-cols-3 xl:grid-cols-6">
      {NEXT.map((item) => (
        <li key={item.name} className="border-t border-line py-3">
          <span className="block text-[15px] font-semibold text-ink">{item.name}</span>
          <span className="block text-[14px] leading-snug text-mute">{item.what}</span>
        </li>
      ))}
    </ul>
  );
}
