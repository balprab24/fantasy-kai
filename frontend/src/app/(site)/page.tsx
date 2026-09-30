import type { Metadata } from "next";
import { BoardSlice } from "@/components/landing/BoardSlice";
import { CareerLine } from "@/components/landing/CareerLine";
import { ComingNext } from "@/components/landing/ComingNext";
import { EmailStart } from "@/components/landing/EmailStart";
import { FeatureSection, SectionCopy, SectionTitle } from "@/components/landing/FeatureSection";
import { HeroPlate } from "@/components/landing/HeroPlate";
import { PointsReceipt } from "@/components/landing/PointsReceipt";
import { NACUA_2025_PPR } from "@/components/landing/previewData";
import { RuleSwing } from "@/components/landing/RuleSwing";
import { WeeklyChart } from "@/components/player/WeeklyChart";
import { JOIN_FIELD_ID } from "@/lib/landing";

export const metadata: Metadata = {
  title: "fantasy-kai",
  description:
    "Fantasy football rankings scored by your league's own rules, game by game, refreshed every morning of the season. Free, with no ads.",
};

/**
 * The front door, for people deciding whether to join -- in Daylight, the
 * public pages' mode (app/(site)/layout.tsx). The product itself is behind an
 * account (app/(app)/layout.tsx), so this page shows it rather than serving it,
 * and shows it working: the hero is the real 2025 board in the product's own
 * dark skin, re-sorting when the rule changes. Every figure can be re-derived;
 * `previewData.ts` names the requests.
 *
 * The order is the argument, run as one: the rule moves the board (hero); the
 * starkest case and the arithmetic behind a total, then what the board is
 * built from and the board itself (scoring, rankings -- one section); one
 * player opened (players); and one last ask, with what is coming beside it.
 * One continuous chalk ground; a single hairline, before the ask.
 *
 * Below lg the hero reads headline, board, then the pitch and the form, so a
 * phone's first screen holds the product and not only a paragraph; the site
 * header's "Join the Kai" is the ask until the form scrolls into view.
 */
export default function Landing() {
  return (
    <>
      <section className="overflow-x-clip">
        <div className="mx-auto grid max-w-[1320px] gap-x-12 px-4 pt-8 pb-20 sm:px-6 lg:grid-cols-2 lg:grid-rows-[auto_auto] lg:px-8 lg:pt-14 lg:pb-24 xl:gap-x-20">
          <h1 className="type-display text-[clamp(4.25rem,8.6vw,8.25rem)] text-ink lg:row-start-1 lg:self-end">
            <span className="block">Ranked by</span>
            <span className="block">your rules.</span>
          </h1>
          <div className="mt-8 min-w-0 lg:col-start-2 lg:row-span-2 lg:row-start-1 lg:mt-0 lg:self-center">
            <HeroPlate />
          </div>
          <div className="mt-10 min-w-0 lg:row-start-2 lg:mt-9 lg:self-start">
            <p className="max-w-[34rem] border-t border-line pt-5 text-[19px] leading-[1.55] text-mute">
              Every NFL player&rsquo;s box scores since 2020, scored under the settings your league actually
              uses and refreshed every morning of the season.
            </p>
            <div className="mt-8">
              <EmailStart fieldId={JOIN_FIELD_ID} />
            </div>
          </div>
        </div>
      </section>

      {/* One passage: the rule swing and the receipt, then -- under the receipt,
          where the slope chart leaves room -- what the board is built from, and
          the board itself across the full width. "Rankings" in the header lands
          on that heading. */}
      <FeatureSection id="scoring" className="pt-16 pb-20 lg:pt-20 lg:pb-24">
        <div className="grid items-start gap-x-20 gap-y-12 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)]">
          <div>
            <SectionTitle id="scoring">
              <span className="block">Change one rule</span>
              <span className="block">and the order changes.</span>
            </SectionTitle>
            <SectionCopy className="mt-6">
              <p>
                Same season, same stat lines, one setting. Under PPR, Puka Nacua finished{" "}
                <strong>2nd</strong> in 2025. Take away the point per catch and Nacua finished{" "}
                <strong>20th</strong>.
              </p>
            </SectionCopy>
            <div className="mt-10">
              <RuleSwing />
            </div>
          </div>
          <div className="lg:pt-2">
            <SectionCopy>
              <p>
                Start from PPR, half PPR, 0 PPR or TE premium, or build your own: every rate,
                overrides for a single position, and bonuses at the thresholds your league pays out
                on. Save it once and choose it anywhere the site asks which scoring to use.
              </p>
            </SectionCopy>
            <div className="mt-10">
              <PointsReceipt />
            </div>
            <section id="rankings" aria-labelledby="rankings-title" className="mt-16 scroll-mt-20">
              <SectionTitle id="rankings">
                <span className="block">Built from box scores,</span>
                <span className="block">one game at a time.</span>
              </SectionTitle>
              <SectionCopy className="mt-6">
                <p>
                  Each game is scored under your league&rsquo;s rules and then the games are added
                  up, so a yardage bonus lands in the game that earned it rather than on a season
                  average. The board refreshes at <strong>6 a.m. Eastern</strong> every day of the
                  season, and tiers are drawn where the points genuinely drop off.
                </p>
                <p>
                  No expert picks go into it, now or later. When a consensus arrives, it will be the
                  market&rsquo;s: real drafts and real rosters.
                </p>
              </SectionCopy>
            </section>
          </div>
        </div>
        <div className="mt-12">
          <BoardSlice />
        </div>
      </FeatureSection>

      <FeatureSection id="players" className="pb-20 lg:pb-24">
        <div className="grid items-start gap-x-16 gap-y-10 lg:grid-cols-[minmax(0,4fr)_minmax(0,8fr)]">
          <div>
            <SectionTitle id="players">
              <span className="block">Every player,</span>
              <span className="block">week by week.</span>
            </SectionTitle>
            <SectionCopy className="mt-6">
              <p>
                Open any player to see each week of a season set against the starter line for their position,
                the full game log, and every season since 2020 with where they finished, all under the scoring
                you picked.
              </p>
            </SectionCopy>
          </div>
          <div>
            <p className="mb-4 text-[15px] text-mute">
              <span className="font-semibold text-ink">Puka Nacua</span>: the receiver the rule change moved
              from 20th to 2nd.
            </p>
            <WeeklyChart season={NACUA_2025_PPR} position="WR" scoringLabel="PPR" />
            <div className="mt-10">
              <CareerLine />
            </div>
          </div>
        </div>
      </FeatureSection>

      <section id="join" aria-labelledby="join-title" className="scroll-mt-16 border-t border-line">
        <div className="mx-auto grid max-w-[1320px] items-start gap-x-20 gap-y-14 px-4 py-20 sm:px-6 lg:grid-cols-[minmax(0,6fr)_minmax(0,6fr)] lg:px-8 lg:py-24">
          <div>
            <h2 id="join-title" className="type-display text-[clamp(2.375rem,4.2vw,3.25rem)] text-ink">
              Join the Kai.
            </h2>
            <p className="mt-5 max-w-[34rem] text-[17px] leading-[1.6] text-mute">
              Free, and it stays that way: no ads, no paywall, nothing to upgrade to.
            </p>
            <div className="mt-8">
              <EmailStart tone="footer" />
            </div>
          </div>
          <section id="next" aria-labelledby="next-title" className="scroll-mt-20">
            <h3 id="next-title" className="type-heading text-ink">
              What is being built next
            </h3>
            <p className="mt-3 max-w-[36rem] text-[15px] leading-relaxed text-mute">
              None of this exists yet. It is listed so you know where the product is headed, not as a promise
              of when.
            </p>
            <ComingNext />
          </section>
        </div>
      </section>
    </>
  );
}
