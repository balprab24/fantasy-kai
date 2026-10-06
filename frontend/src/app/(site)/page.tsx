import type { Metadata } from "next";
import Link from "next/link";
import { ComingNext } from "@/components/landing/ComingNext";
import { EmailStart } from "@/components/landing/EmailStart";
import { FeatureSection, SectionCopy, SectionTitle } from "@/components/landing/FeatureSection";
import { GameToSeason } from "@/components/landing/GameToSeason";
import { HeroPlate } from "@/components/landing/HeroPlate";
import { ProductBand } from "@/components/landing/ProductBand";
import { RuleSwing } from "@/components/landing/RuleSwing";
import { BUTTON_JOIN } from "@/components/ui/buttons";
import { CLOSING_ID, HERO_JOIN_ID } from "@/lib/landing";

export const metadata: Metadata = {
  title: "fantasy-kai",
  description:
    "Fantasy football rankings scored by your league's own rules, game by game, refreshed every morning of the season. Free, with no ads.",
};

/**
 * The front door, for people deciding whether to join -- in Daylight, the
 * public pages' mode (app/(site)/layout.tsx). The product itself is behind an
 * account (app/(app)/layout.tsx), so this page shows it rather than serving it,
 * and every figure on it is captured from the real API (`previewData.ts`
 * names the requests).
 *
 * The order is the argument, and the ground changes with it, so the page has
 * a rhythm instead of one long sheet:
 *   - the hero (paper): the real board in its own dark skin, re-sorting when
 *     the rule changes, beside one ask;
 *   - one rule, three boards (paper): how far that one rule moves people;
 *   - the product (the stage: a full-width band of Prime time): the board and
 *     the player it leads to, as they really look;
 *   - your rates, every game (the band): how one number is made, with the
 *     one rate the presets disagree on as a control;
 *   - what is coming (paper, small), then the last ask at full width.
 *
 * Below lg the hero reads headline, pitch, ask, then the board, so a phone's
 * first screen holds both the action and the product.
 */
export default function Landing() {
  return (
    <>
      <section className="overflow-x-clip">
        <div className="mx-auto grid max-w-[1320px] items-center gap-x-12 px-4 pt-6 pb-16 sm:px-6 sm:pt-8 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:px-8 lg:pt-14 lg:pb-16 xl:gap-x-16">
          <div className="min-w-0">
            <h1 className="type-display text-[clamp(4.25rem,11vw,7rem)] text-ink lg:text-[clamp(5rem,8.2vw,8rem)]">
              <span className="block">Ranked by</span>
              <span className="block">your rules.</span>
            </h1>
            <p className="mt-5 max-w-[30rem] text-[17px] leading-[1.55] text-pretty text-mute sm:mt-6 sm:text-[19px]">
              Fantasy football rankings scored by the settings your league actually uses.
              {/* A phone's first screen is for the board; the rest is said again below. */}
              <span className="hidden sm:inline">
                {" "}
                Every NFL box score since 2020, updated every morning of the season.
              </span>
            </p>
            <div className="mt-7 flex flex-wrap items-center gap-x-5 gap-y-3 sm:mt-8">
              <Link id={HERO_JOIN_ID} href="/register" className={`${BUTTON_JOIN} w-full sm:w-auto`}>
                Join the Kai
              </Link>
              <p className="text-[15px] text-mute">Free. No ads, no paywall.</p>
            </div>
          </div>
          <div className="mt-10 min-w-0 sm:mt-12 lg:mt-0">
            <HeroPlate />
          </div>
        </div>
      </section>

      {/* The rule's effect, drawn at the page's full width: the heading and its one
          line above it, not beside it. */}
      <FeatureSection id="scoring" className="pb-16 sm:pb-24 lg:pb-28">
        <div className="border-t border-line pt-14 sm:pt-16 lg:pt-14">
          <div className="grid items-end gap-x-16 gap-y-5 lg:grid-cols-[minmax(0,6fr)_minmax(0,6fr)]">
            <SectionTitle id="scoring">
              <span className="block">One rule.</span>
              <span className="block">Three boards.</span>
            </SectionTitle>
            <SectionCopy>
              <p>
                Same 2025 season, same stat lines. The three presets differ in one number, what a catch is
                worth, and Puka Nacua goes from <strong>20th</strong> to <strong>2nd</strong>.
              </p>
            </SectionCopy>
          </div>
          <div className="mt-12 lg:mt-16">
            <RuleSwing />
          </div>
        </div>
      </FeatureSection>

      <ProductBand />

      {/* Mirrored: the working part on the left, the words on the right. */}
      <section aria-labelledby="rates-title" className="bg-band">
        <div className="mx-auto grid max-w-[1320px] items-start gap-x-20 gap-y-12 px-4 py-16 sm:px-6 sm:py-20 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] lg:px-8 lg:py-28">
          {/* The words stay in view while the receipt beside them is read. */}
          <div className="lg:sticky lg:top-24 lg:order-2 lg:self-start lg:pt-1">
            <SectionTitle id="rates">
              <span className="block">Your rates.</span>
              <span className="block">Every game.</span>
            </SectionTitle>
            <SectionCopy className="mt-6">
              <p>
                Start from a preset or set every rate yourself, with overrides for one position and bonuses at
                the thresholds your league pays. Each game is scored on its own, then added up.
              </p>
            </SectionCopy>
            <p className="mt-8 max-w-[30rem] border-t border-line pt-4 text-[14px] leading-relaxed text-pretty text-mute">
              Box scores from nflverse for every game since 2020, pulled at 6 a.m. Eastern every morning of the
              season and scored when you ask, never stored as points. No expert picks go in, now or later.
            </p>
          </div>
          <GameToSeason />
        </div>
      </section>

      <section id="next" aria-labelledby="next-title" className="scroll-mt-16">
        <div className="mx-auto max-w-[1320px] px-4 pt-16 pb-14 sm:px-6 sm:pt-20 lg:px-8 lg:pt-24">
          <div className="flex flex-wrap items-baseline justify-between gap-x-10 gap-y-2">
            <h2 id="next-title" className="type-title text-ink">
              Being built next
            </h2>
            <p className="text-[15px] text-pretty text-mute">
              None of it exists yet. It is here so you know where the product is headed.
            </p>
          </div>
          <div className="mt-6">
            <ComingNext />
          </div>
        </div>
      </section>

      {/* One last ask, at full width: the headline at the hero's scale, the form on one line under it. */}
      <section id={CLOSING_ID} aria-labelledby="join-title" className="scroll-mt-16">
        <div className="mx-auto max-w-[1320px] px-4 sm:px-6 lg:px-8">
          <div className="border-t border-line pt-14 pb-20 sm:pt-16 sm:pb-24 lg:pt-20 lg:pb-28">
            <h2 id="join-title" className="type-display text-[clamp(4.25rem,11vw,8rem)] text-ink">
              Join the Kai.
            </h2>
            <p className="mt-5 max-w-[36rem] text-[17px] leading-[1.6] text-pretty text-mute sm:text-[19px]">
              Free, and it stays that way: no ads, no paywall, nothing to upgrade to.
            </p>
            <div className="mt-10">
              <EmailStart />
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
