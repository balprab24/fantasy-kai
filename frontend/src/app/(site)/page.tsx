import type { Metadata } from "next";
import { BoardSlice } from "@/components/landing/BoardSlice";
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
 * The front door, for people deciding whether to join. The product itself is
 * behind an account (app/(app)/layout.tsx), so this page shows it rather than
 * serving it -- and shows it working: a real season the visitor can re-score,
 * a real slice of the board, a rule change drawn out, the arithmetic behind a
 * total. Every figure can be re-derived; `previewData.ts` names the requests.
 *
 * The pace changes on purpose: the hero, then a dense board slice, a quiet
 * band that makes the argument, a chart, a list, and one last ask.
 */
export default function Landing() {
  return (
    <>
      <section className="overflow-x-clip">
        <div className="mx-auto grid max-w-[1320px] items-center gap-x-16 gap-y-12 px-4 pt-12 pb-12 sm:px-6 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] lg:px-8 lg:pt-20 lg:pb-32">
          <div className="min-w-0">
            <h1 className="type-display text-[clamp(4.25rem,9vw,8.25rem)] text-ink">
              <span className="block">Ranked by</span>
              <span className="block">your rules.</span>
            </h1>
            <p className="mt-9 max-w-[34rem] border-y border-line py-5 text-[19px] leading-[1.55] text-mute">
              Every NFL player&rsquo;s box scores since 2020, scored under the settings your league
              actually uses and refreshed every morning of the season.
            </p>
            <div className="mt-8">
              <EmailStart fieldId={JOIN_FIELD_ID} />
            </div>
          </div>
          <HeroPlate />
        </div>
      </section>

      <FeatureSection id="rankings" className="pt-16 pb-24 lg:py-32">
        <div className="grid items-start gap-x-16 gap-y-12 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
          <div>
            <SectionTitle id="rankings">Built from box scores, one game at a time.</SectionTitle>
            <SectionCopy className="mt-7">
              <p>
                Every ranking starts from the box score of each game, from nflverse&rsquo;s open data.
                Each game is scored under your league&rsquo;s rules and then the games are added up,
                so a yardage bonus lands in the game that earned it rather than on a season average.
              </p>
              <p>
                The board refreshes at <strong>6 a.m. Eastern</strong> every day of the season. Tiers
                are drawn where the points genuinely drop off, so you can see who is interchangeable
                and who isn&rsquo;t.
              </p>
              <p>
                No expert picks go into it, now or later. When a consensus arrives, it will be the
                market&rsquo;s: real drafts and real rosters.
              </p>
            </SectionCopy>
          </div>
          <BoardSlice />
        </div>
      </FeatureSection>

      <FeatureSection id="scoring" className="bg-surface py-24 lg:py-32">
        <SectionTitle id="scoring" className="max-w-[15ch]">
          Change one rule and the order changes.
        </SectionTitle>
        <div className="mt-12 grid items-start gap-x-20 gap-y-16 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)]">
          <div>
            <SectionCopy>
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
          <div>
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
          </div>
        </div>
      </FeatureSection>

      <FeatureSection id="players" className="py-24 lg:py-32">
        <div className="grid items-end gap-x-16 gap-y-12 lg:grid-cols-[minmax(0,4fr)_minmax(0,8fr)]">
          <div>
            <SectionTitle id="players">Every player, week by week.</SectionTitle>
            <SectionCopy className="mt-7">
              <p>
                Open any player to see each week of a season set against the starter line for their
                position, the full game log, and every season since 2020 with where they finished,
                all under the scoring you picked.
              </p>
            </SectionCopy>
          </div>
          <div>
            <p className="mb-4 text-[15px] text-mute">
              <span className="font-semibold text-ink">Puka Nacua</span>, 2025 regular season: the
              receiver the rule change moved from 20th to 2nd.
            </p>
            <WeeklyChart season={NACUA_2025_PPR} position="WR" scoringLabel="PPR" />
          </div>
        </div>
      </FeatureSection>

      <FeatureSection id="next" className="border-t border-line py-24 lg:py-32">
        <SectionTitle id="next">What is being built next.</SectionTitle>
        <SectionCopy className="mt-7">
          <p>
            None of this exists yet. It is listed so you know where the product is headed, not as a
            promise of when.
          </p>
        </SectionCopy>
        <ComingNext />
      </FeatureSection>

      <section id="join" className="scroll-mt-16 border-t border-line">
        <div className="mx-auto grid max-w-[1320px] items-end gap-x-16 gap-y-8 px-4 py-24 sm:px-6 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] lg:px-8 lg:py-32">
          <h2 className="type-display text-[clamp(3.5rem,9vw,8.25rem)] text-ink">Join the Kai.</h2>
          <div className="lg:pb-3">
            <p className="max-w-[34rem] text-[17px] leading-[1.6] text-mute">
              Free, and it stays that way: no ads, no paywall, nothing to upgrade to.
            </p>
            <div className="mt-7">
              <EmailStart tone="footer" />
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
