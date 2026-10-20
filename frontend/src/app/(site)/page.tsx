import type { Metadata } from "next";
import Link from "next/link";
import { EmailStart } from "@/components/landing/EmailStart";
import { FOLLOWED, RECEIPT_GAME } from "@/components/landing/heroData";
import { PlayerShowcase } from "@/components/landing/PlayerShowcase";
import { Receipt } from "@/components/landing/Receipt";
import { RuleMixer } from "@/components/landing/RuleMixer";
import { RulesSheet } from "@/components/landing/RulesSheet";
import { BUTTON_JOIN } from "@/components/ui/buttons";
import { formatPoints } from "@/lib/board";
import { CLOSING_ID, HERO_JOIN_ID } from "@/lib/landing";
import { matchup } from "@/lib/player";

export const metadata: Metadata = {
  title: "fantasy-kai",
  description:
    "Fantasy football rankings and player pages, scored by your league's exact settings: PPR, half PPR, 0 PPR or your own. Free, with no ads.",
};

const WORDS = ["Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen", "Twenty"];
/** A small count as a word for a headline; a larger one as digits. */
const word = (n: number) => WORDS[n] ?? String(n);

/** The page's one container: 1440 wide, the product's own gutters. */
const CONTAINER = "mx-auto w-full max-w-[1440px] px-4 sm:px-6 lg:px-8";

/**
 * The front door, for people deciding whether to join. The product is behind
 * an account, so this page shows it rather than serving it, and every figure
 * on it is a capture from the real API (`heroData.ts`, written by
 * `scripts/capture-landing.mjs`, which lists the requests).
 *
 * One argument, told four ways, each with its own composition:
 *   - the hero: your league's rules rewrite the rankings -- the season's real
 *     board with two settings as dials;
 *   - the receipt: why -- one real game, stat by stat, priced three ways;
 *   - the player: what a member opens -- one season, week by week and under
 *     every ruleset;
 *   - your league, everywhere: build it once and every page follows; then the
 *     ask.
 *
 * Same night ground as the product: the landing is the product's front room,
 * not a different building.
 */
export default function Landing() {
  const g = RECEIPT_GAME;
  return (
    <>
      <section aria-labelledby="hero-title" className="relative isolate overflow-x-clip">
        <Strokes />
        <div className={`${CONTAINER} grid items-center gap-x-14 gap-y-10 pt-8 pb-16 sm:pt-14 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:pt-16 lg:pb-24`}>
          <div className="min-w-0">
            <h1 id="hero-title" className="type-display text-[clamp(3.75rem,10vw,7.25rem)] text-ink">
              <span className="block">Ranked by</span>
              <span className="block">your rules.</span>
            </h1>
            <p className="mt-6 max-w-[32rem] text-[17px] leading-[1.6] text-pretty text-mute sm:text-[19px]">
              Fantasy football rankings and player pages, scored by your league&rsquo;s exact settings. Change one
              rule and the whole board changes with it.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-x-5 gap-y-3">
              <Link id={HERO_JOIN_ID} href="/register" className={`${BUTTON_JOIN} w-full sm:w-auto`}>
                Join the Kai
              </Link>
              <p className="text-[15px] text-mute">Free. No ads, no paywall.</p>
            </div>
            <p className="mt-8 max-w-[32rem] border-t border-line pt-5 text-[14px] leading-relaxed text-mute">
              Every NFL game since 2020, scored when you ask against the rules you choose, and refreshed every
              morning of the season.
            </p>
          </div>
          <div id="rankings" className="min-w-0 scroll-mt-24">
            <RuleMixer />
          </div>
        </div>
      </section>

      {/* Mirrored against the hero: the working part on the left. */}
      <section id="scoring" aria-labelledby="scoring-title" className="scroll-mt-16 border-t border-line">
        <div className={`${CONTAINER} grid items-center gap-x-16 gap-y-10 py-16 sm:py-20 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] lg:py-28`}>
          <div className="min-w-0 lg:order-2">
            <h2 id="scoring-title" className="type-display text-[clamp(2.75rem,5.4vw,4.25rem)] text-ink">
              <span className="block">{word(g.stats.rec)} catches.</span>
              <span className="block">Three answers.</span>
            </h2>
            <p className="mt-6 max-w-[34rem] text-[17px] leading-[1.65] text-pretty text-mute">
              Week {g.week} {matchup(g.opponent, g.home)}, {FOLLOWED.name} caught {g.stats.rec} passes for{" "}
              {g.stats.rec_yd} yards. Under 0 PPR that game is worth{" "}
              <span className="font-semibold text-ink">{formatPoints(g.points[0])}</span> points; under PPR,{" "}
              <span className="font-semibold text-ink">{formatPoints(g.points[1])}</span>. Same game, same stats: one
              line of your league&rsquo;s settings decides.
            </p>
            <p className="mt-5 max-w-[34rem] text-[14px] leading-relaxed text-mute">
              No point is stored. Every one is computed from the box score when you ask, so changing a rule
              re-prices every game at once.
            </p>
          </div>
          <div className="min-w-0 lg:order-1">
            <Receipt />
          </div>
        </div>
      </section>

      <section id="players" aria-labelledby="players-title" className="scroll-mt-16 border-t border-line">
        <div className={`${CONTAINER} py-16 sm:py-20 lg:py-28`}>
          <div className="grid items-end gap-x-16 gap-y-5 lg:grid-cols-[minmax(0,6fr)_minmax(0,6fr)]">
            <h2 id="players-title" className="type-display text-[clamp(2.75rem,5.4vw,4.25rem)] text-ink">
              <span className="block">Every player.</span>
              <span className="block">Every week.</span>
            </h2>
            <p className="max-w-[36rem] text-[17px] leading-[1.65] text-pretty text-mute lg:pb-2">
              Open anyone on the board: his season under your scoring, every week charted, every game in the log,
              and the same stats under every ruleset you use, side by side.
            </p>
          </div>
          <div className="mt-10 lg:mt-12">
            <PlayerShowcase />
          </div>
        </div>
      </section>

      <section aria-labelledby="league-title" className="border-t border-line">
        <div className={`${CONTAINER} grid items-center gap-x-16 gap-y-10 py-16 sm:py-20 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:py-28`}>
          <div className="min-w-0">
            <h2 id="league-title" className="type-display text-[clamp(2.75rem,5.4vw,4.25rem)] text-ink">
              <span className="block">Your league.</span>
              <span className="block">Everywhere.</span>
            </h2>
            <p className="mt-6 max-w-[32rem] text-[17px] leading-[1.65] text-pretty text-mute">
              Set the rates your league actually uses and save them once. The rankings, every player page and
              every game log are then scored by them, not by a generic 12-team PPR.
            </p>
            <p className="mt-5 max-w-[32rem] text-[14px] leading-relaxed text-mute">
              No expert picks go in, now or later. The numbers are the box scores and your rules.
            </p>
          </div>
          <div className="min-w-0">
            <RulesSheet />
          </div>
        </div>
      </section>

      {/* One last ask, at full width. */}
      <section id={CLOSING_ID} aria-labelledby="join-title" className="scroll-mt-16 border-t border-line">
        <div className={`${CONTAINER} pt-16 pb-20 sm:pt-20 sm:pb-24 lg:pt-24 lg:pb-28`}>
          <h2 id="join-title" className="type-display text-[clamp(3.75rem,10vw,7.25rem)] text-ink">
            Join the Kai.
          </h2>
          <p className="mt-5 max-w-[36rem] text-[17px] leading-[1.6] text-pretty text-mute sm:text-[19px]">
            Free, and it stays that way: no ads, no paywall, nothing to upgrade to.
          </p>
          <div className="mt-10">
            <EmailStart />
          </div>
          {/* What is coming, as a footnote to the ask rather than a pitch of its own. */}
          <p className="mt-12 max-w-[44rem] text-[14px] leading-relaxed text-pretty text-mute">
            Coming next: projections, ESPN and Sleeper league import, start/sit and waivers, trades. None of it is
            built yet.
          </p>
        </div>
      </section>
    </>
  );
}

/**
 * The logo's two strokes, enormous and nearly the colour of the night behind
 * the hero: the product's mark as the stage, not decoration borrowed from
 * anywhere. From lg only, where the hero has a second column to stand behind.
 */
function Strokes() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 26 26"
      preserveAspectRatio="xMaxYMid slice"
      className="pointer-events-none absolute top-0 right-[-12%] -z-10 h-full w-[85%] text-[#0c1426] max-lg:hidden"
    >
      <path d="M9 3h6L9 23H3z" fill="currentColor" />
      <path d="M18 3h5l-6 20h-5z" fill="currentColor" />
    </svg>
  );
}
