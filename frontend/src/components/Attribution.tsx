/**
 * Owed since Phase 0, and not optional: nflverse ships under CC BY 4.0, which
 * requires attribution wherever the data is shown, and Fantasy Football
 * Calculator asks for the same. This is the licence being honoured, not a
 * courtesy -- which is why both route-group layouts carry it, the product's
 * (via AppShell) and the landing page's, rather than any one page.
 */
export function Attribution() {
  return (
    <footer className="mt-16 border-t border-line">
      <div className="mx-auto max-w-[1320px] px-4 py-8 text-sm leading-relaxed text-mute sm:px-6 lg:px-8">
        <p className="max-w-[68ch]">
          Play-by-play, rosters and schedules from{" "}
          <a
            href="https://github.com/nflverse/nflverse-data"
            className="text-ink underline decoration-line-strong underline-offset-2 hover:decoration-field"
          >
            nflverse
          </a>
          , used under{" "}
          <a
            href="https://creativecommons.org/licenses/by/4.0/"
            className="text-ink underline decoration-line-strong underline-offset-2 hover:decoration-field"
          >
            CC BY 4.0
          </a>
          . Player identifiers from{" "}
          <a
            href="https://docs.sleeper.com/"
            className="text-ink underline decoration-line-strong underline-offset-2 hover:decoration-field"
          >
            Sleeper
          </a>
          . Draft position from{" "}
          <a
            href="https://fantasyfootballcalculator.com/"
            className="text-ink underline decoration-line-strong underline-offset-2 hover:decoration-field"
          >
            Fantasy Football Calculator
          </a>
          . Player headshots and team logos load from ESPN&rsquo;s image servers, keyed by the ESPN
          id nflverse publishes; nothing is copied or stored here.
        </p>
        <p className="mt-3 max-w-[68ch]">
          Not affiliated with or endorsed by the NFL. No expert rankings are used anywhere in this
          product — consensus here means the market: real drafts, real roster rates, real lines.
        </p>
      </div>
    </footer>
  );
}
