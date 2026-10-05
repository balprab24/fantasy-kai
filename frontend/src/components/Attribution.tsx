import { Wordmark } from "./shell/Wordmark";
import { LINK_QUIET } from "./ui/buttons";

/**
 * Owed since Phase 0, and not optional: nflverse ships under CC BY 4.0, which
 * requires attribution wherever the data is shown, and Fantasy Football
 * Calculator asks for the same. This is the licence being honoured, not a
 * courtesy -- which is why both route-group layouts carry it, the product's
 * (via AppShell) and the landing page's, rather than any one page.
 *
 * Quiet on purpose: small type, one hairline to say the page has ended. The
 * measure is 54ch because `ch` is the width of a zero, wider than this face's
 * average letter: 72ch set 96 characters to a line.
 */
export function Attribution({ variant = "product" }: { variant?: "product" | "site" }) {
  const credits = (
    <p className="max-w-[54ch]">
      Play-by-play, rosters and schedules from{" "}
      <a href="https://github.com/nflverse/nflverse-data" className={LINK_QUIET}>
        nflverse
      </a>
      , used under{" "}
      <a href="https://creativecommons.org/licenses/by/4.0/" className={LINK_QUIET}>
        CC BY 4.0
      </a>
      . Player identifiers from{" "}
      <a href="https://docs.sleeper.com/" className={LINK_QUIET}>
        Sleeper
      </a>
      . Draft position from{" "}
      <a href="https://fantasyfootballcalculator.com/" className={LINK_QUIET}>
        Fantasy Football Calculator
      </a>
      . Player headshots and team logos load from ESPN&rsquo;s image servers, keyed by the ESPN id nflverse
      publishes; nothing is copied or stored here.
    </p>
  );
  const independence = (
    <p className="max-w-[54ch]">
      Not affiliated with or endorsed by the NFL. No expert rankings are used anywhere in this product —
      consensus here means the market: real drafts, real roster rates, real lines.
    </p>
  );

  // The public pages close on the name as well as the credits, in two
  // columns, so a wide screen does not end on one narrow paragraph -- and on
  // the band, so the page ends on a change of ground rather than a rule.
  if (variant === "site") {
    return (
      <footer className="bg-band">
        <div className="mx-auto grid max-w-[1320px] gap-x-20 gap-y-8 px-4 pt-10 pb-14 text-[13px] leading-relaxed text-mute sm:px-6 lg:grid-cols-[minmax(0,4fr)_minmax(0,8fr)] lg:px-8">
          <div>
            <Wordmark />
            <p className="mt-3 max-w-[32ch]">
              Fantasy football rankings, scored by your league&rsquo;s own rules.
            </p>
          </div>
          <div className="grid gap-x-12 gap-y-3 md:grid-cols-2">
            {credits}
            {independence}
          </div>
        </div>
      </footer>
    );
  }

  return (
    <footer className="mt-24 border-t border-line">
      <div className="mx-auto max-w-[1320px] px-4 pt-8 pb-12 text-[13px] leading-relaxed text-mute sm:px-6 lg:px-8">
        {credits}
        <div className="mt-3">{independence}</div>
      </div>
    </footer>
  );
}
