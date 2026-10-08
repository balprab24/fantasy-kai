import type { Metadata } from "next";
import Link from "next/link";
import { EmailStart } from "@/components/landing/EmailStart";
import { FeatureSection, SectionCopy, SectionTitle } from "@/components/landing/FeatureSection";
import { HeroDemo } from "@/components/landing/HeroDemo";
import { OneBoard } from "@/components/landing/OneBoard";
import { PlayersBand } from "@/components/landing/PlayersBand";
import { BUTTON_JOIN } from "@/components/ui/buttons";
import { CLOSING_ID, HERO_JOIN_ID } from "@/lib/landing";

export const metadata: Metadata = {
  title: "fantasy-kai",
  description:
    "Fantasy football rankings and player pages, scored by your league's exact settings: PPR, half PPR, 0 PPR or your own. Free, with no ads.",
};

/**
 * The front door, for people deciding whether to join -- in Daylight, the
 * public pages' mode (app/(site)/layout.tsx). The product itself is behind an
 * account (app/(app)/layout.tsx), so this page shows it rather than serving
 * it, and every figure on it is captured from the real API (`heroData.ts` and
 * `previewData.ts` name the requests).
 *
 * The order is hook, product, difference, depth, ask -- recognition first,
 * then understanding:
 *   - the hero (paper): what this is, in words, beside the product itself --
 *     the rankings page for the season being played, in its own dark skin,
 *     running off the page's edge, under a member's own ruleset ("My league"):
 *     its scoring switch works, and a short loop shows the rows re-sorting
 *     between PPR and My league -- your league's rules rewrite the board;
 *   - one board, three rulesets (paper): what a catch is worth, alone --
 *     2025's board, which waits for the visitor, following one player;
 *   - every player, every game (the stage: a full-width band of Prime time):
 *     that player opened, with the player page's own scoring switch live;
 *   - the last ask at full width, with what is coming said in one line.
 *
 * Each section has its own composition, so the page changes pace instead of
 * repeating one heading-paragraph-chart template down the scroll.
 */
export default function Landing() {
  return (
    <>
      <section className="overflow-x-clip">
        <div className="mx-auto grid max-w-[1320px] items-center gap-x-14 px-4 pt-6 pb-16 sm:px-6 sm:pt-10 lg:px-8 lg:pt-14 lg:grid-cols-[minmax(0,37fr)_minmax(0,63fr)] lg:pb-20">
          <div className="min-w-0">
            <h1 className="type-display text-[clamp(4.25rem,11vw,7rem)] text-ink lg:text-[clamp(5rem,7.4vw,7.25rem)]">
              <span className="block">Ranked by</span>
              <span className="block">your rules.</span>
            </h1>
            <p className="mt-5 max-w-[30rem] text-[17px] leading-[1.55] text-pretty text-mute sm:mt-6 sm:text-[19px]">
              <span className="text-ink">Fantasy football rankings and player pages, scored by your league&rsquo;s exact
              settings</span>: PPR, half PPR, 0 PPR or your own.
              {/* A phone's first screen is for the ask; the rest is said again below. */}
              <span className="hidden sm:inline"> Every NFL game since 2020, updated every morning of the season.</span>
            </p>
            <div className="mt-7 flex flex-wrap items-center gap-x-5 gap-y-3 sm:mt-8">
              <Link id={HERO_JOIN_ID} href="/register" className={`${BUTTON_JOIN} w-full sm:w-auto`}>
                Join the Kai
              </Link>
              <p className="text-[15px] text-mute">Free account. No ads, no paywall.</p>
            </div>
            <a
              href="#rankings"
              className="mt-6 hidden text-[15px] text-ink underline decoration-line-strong underline-offset-4 transition-colors hover:decoration-energy sm:inline-block"
            >
              See how scoring changes the board <span aria-hidden>&darr;</span>
            </a>
          </div>
          {/* The product runs off the page: full width on a phone, to the browser's right edge from lg. */}
          <div className="-mx-4 mt-10 min-w-0 sm:mx-0 sm:mt-12 lg:mt-0 lg:-mr-[max(2rem,calc((100vw-1320px)/2+2rem))]">
            <HeroDemo />
          </div>
        </div>
      </section>

      {/* Mirrored against the hero: the working part on the left, the words on the right. */}
      <FeatureSection id="rankings" className="pb-16 sm:pb-24 lg:pb-28">
        <div className="grid items-start gap-x-16 gap-y-10 border-t border-line pt-14 sm:pt-16 lg:grid-cols-[minmax(0,8fr)_minmax(0,4fr)] lg:pt-20">
          <div className="min-w-0 lg:order-2">
            <SectionTitle id="rankings">
              <span className="block">One board.</span>
              <span className="block">Three rulesets.</span>
            </SectionTitle>
            <SectionCopy className="mt-6">
              <p>
                2025, the last full season. The three presets differ in one number, what a catch is worth, and Puka
                Nacua goes from <strong>20th</strong> to <strong>11th</strong> to <strong>2nd</strong>.
              </p>
            </SectionCopy>
            <Provenance className="mt-8 hidden lg:block" />
          </div>
          <div className="min-w-0 lg:order-1">
            <OneBoard />
          </div>
          {/* Below lg the board comes straight after its heading, and where its numbers come from follows it. */}
          <Provenance className="lg:hidden" />
        </div>
      </FeatureSection>

      <PlayersBand />

      {/* One last ask, at full width: the headline at the hero's scale, the form on one line under it. */}
      <section id={CLOSING_ID} aria-labelledby="join-title" className="scroll-mt-16">
        <div className="mx-auto max-w-[1320px] px-4 sm:px-6 lg:px-8">
          <div className="pt-16 pb-20 sm:pt-20 sm:pb-24 lg:pt-24 lg:pb-28">
            <h2 id="join-title" className="type-display text-[clamp(4.25rem,11vw,8rem)] text-ink">
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
        </div>
      </section>
    </>
  );
}

/** Where the board's numbers come from: beside the words from lg, after the board below it. */
function Provenance({ className }: { className: string }) {
  return (
    <p className={`max-w-[30rem] border-t border-line pt-4 text-[14px] leading-relaxed text-pretty text-mute ${className}`}>
      Box scores from nflverse for every game since 2020, scored when you ask and never stored as points. No expert
      picks go in, now or later.
    </p>
  );
}
