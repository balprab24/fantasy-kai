import type { Metadata } from "next";
import { ComingNext } from "@/components/landing/ComingNext";
import { EmailStart } from "@/components/landing/EmailStart";
import { FeatureSection, SectionAsk } from "@/components/landing/FeatureSection";
import { FeatureStage } from "@/components/landing/FeatureStage";
import { HeroRunner } from "@/components/landing/HeroRunner";
import { PointsReceipt } from "@/components/landing/PointsReceipt";
import { JOIN_FIELD_ID } from "@/lib/landing";

export const metadata: Metadata = {
  title: "fantasy-kai",
  description:
    "Fantasy football rankings scored by your league's own rules, game by game, refreshed every morning of the season. Free, with no ads.",
};

/**
 * The front door, for people deciding whether to join. The product itself is
 * behind an account (app/(app)/layout.tsx), so this page shows it rather than
 * serving it: a phone drawn from real 2025 rows, and a few paragraphs on how
 * each part works. Every figure on it can be re-derived -- see `previewData.ts`
 * for the requests, and `PointsReceipt` for the arithmetic.
 *
 * Static and server-rendered; the header, the forms and the phone are the only
 * parts that need the browser.
 */
export default function Landing() {
  return (
    <>
      <section className="field relative isolate overflow-hidden">
        <div className="mx-auto grid max-w-[1320px] items-center gap-12 px-4 pt-14 pb-16 sm:px-6 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] lg:px-8 lg:pt-24 lg:pb-28">
          <div className="relative z-10">
            <h1 className="font-display max-w-[14ch] text-[clamp(2.75rem,6vw,5.5rem)] leading-[0.95] font-bold tracking-[-0.015em] text-balance">
              Rank every player by your league&rsquo;s rules.
            </h1>
            <p className="mt-6 max-w-[36rem] text-[18px] leading-[1.6] text-mute">
              Every player&rsquo;s games since 2020, scored against the settings your league
              actually uses and refreshed every morning of the season.
            </p>
            <div className="mt-9">
              <EmailStart fieldId={JOIN_FIELD_ID} />
            </div>
          </div>
          <HeroRunner />
        </div>
      </section>

      <FeatureStage>
        <FeatureSection id="rankings" title="Built from box scores, one game at a time.">
          <p>
            Every ranking starts from the box score of each game, from nflverse&rsquo;s open data.
            Each game is scored under your league&rsquo;s rules and then the games are added up, so
            a yardage bonus lands in the game that earned it rather than on a season average.
          </p>
          <p>
            The board refreshes at <strong>6 a.m. Eastern</strong> every day of the season. Tiers
            are drawn where the points genuinely drop off, so you can see who is interchangeable and
            who isn&rsquo;t.
          </p>
          <PointsReceipt />
          <p>
            No expert picks go into it, now or later. When a consensus arrives, it will be the
            market&rsquo;s: real drafts and real rosters.
          </p>
          <SectionAsk />
        </FeatureSection>

        <FeatureSection id="scoring" title="Change one rule and the order changes.">
          <p>
            Same season, same stat lines, one setting. Under PPR, Puka Nacua finished{" "}
            <strong>2nd</strong> in 2025. Take away the point per catch and Nacua finished{" "}
            <strong>20th</strong>.
          </p>
          <p>
            Start from PPR, half PPR, 0 PPR or TE premium, or build your own: every rate, overrides
            for a single position, and bonuses at the thresholds your league pays out on. Save it
            once and choose it anywhere the site asks which scoring to use.
          </p>
          <SectionAsk />
        </FeatureSection>

        <FeatureSection id="players" title="Every player, week by week.">
          <p>
            Open any player to see each week of a season set against the starter line for their
            position, the full game log, and every season since 2020 with where they finished, all
            under the scoring you picked.
          </p>
          <SectionAsk />
        </FeatureSection>

        <FeatureSection id="next" title="What is being built next.">
          <p>
            None of this exists yet. It is listed so you know where the product is headed, not as
            a promise of when.
          </p>
          <ComingNext />
        </FeatureSection>
      </FeatureStage>

      <section id="join" className="scroll-mt-28 border-t border-line md:scroll-mt-20">
        <div className="mx-auto max-w-[1320px] px-4 py-20 sm:px-6 lg:px-8 lg:py-28">
          <h2 className="font-display text-[clamp(2.25rem,4.5vw,3.75rem)] leading-[1] font-bold tracking-[-0.01em]">
            Join the Kai.
          </h2>
          <p className="mt-4 max-w-[40rem] text-[17px] leading-[1.6] text-mute">
            Free, and it stays that way: no ads, no paywall, nothing to upgrade to.
          </p>
          <div className="mt-8">
            <EmailStart tone="footer" />
          </div>
        </div>
      </section>
    </>
  );
}
