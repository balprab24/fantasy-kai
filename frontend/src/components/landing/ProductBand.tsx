"use client";

import { COL, COLUMN_COUNT, PlayerRow } from "@/components/rankings/PlayerRow";
import { FilterBar } from "@/components/rankings/FilterBar";
import { TierHeader } from "@/components/rankings/TierHeader";
import { assignTiers, toBoardRows, type Tier } from "@/lib/board";
import type { ScoringProfile } from "@/lib/types";
import { FeatureSection, SectionTitle } from "./FeatureSection";
import { PlayerReport } from "./PlayerReport";
import { WR_PPR_2025, WR_PPR_FROM_ZERO_2025 } from "./previewData";

/** Ranks shown: the top ten receivers, across both of the board's first tier breaks (S, A, B). */
const FROM = 1;
const TO = 10;

/** The row the band follows into the report beside it. */
const FOLLOWED = 16153; // Puka Nacua

/** The four presets as `/scoring-profiles` lists them (V3), for the console's switch. */
const PRESETS: ScoringProfile[] = [
  { id: 1, name: "Standard", preset: true },
  { id: 2, name: "Half PPR", preset: true },
  { id: 3, name: "Full PPR", preset: true },
  { id: 4, name: "TE Premium", preset: true },
];

const noop = () => {};

/** The rows ranked `from` to `to` of a tiered board, still grouped by tier. */
function rowsBetween(tiers: Tier[], from: number, to: number): Tier[] {
  return tiers
    .map((tier) => ({ ...tier, rows: tier.rows.filter((r) => r.rank >= from && r.rank <= to) }))
    .filter((tier) => tier.rows.length > 0);
}

/**
 * Where the page cuts to the product as it really looks: a full-width band in
 * its own dark skin (`.primetime`), the page's one stretch of Prime time after
 * the hero's plate. On the left, the real 2025 board filtered to wide
 * receivers -- the board's own console with WR chosen, its rows, tier dividers
 * and per-game colours, not a picture of them; on the right, the player its
 * lit row belongs to, opened. Filtered, so the band shows the product doing
 * something the hero's board does not, rather than the same eight names again.
 *
 * Tiers are cut over the board's top 60, as the board cuts them
 * (`previewData.ts` says why sixty), and ranks 1 to 10 are shown, so three
 * tiers and two breaks are on screen. The movement column is real too: each
 * receiver's move from the 0 PPR WR board. A demonstration, not a tool: the
 * board is `inert` and hidden from screen readers, because the lines under it
 * say in words what it shows, and the report beside it is readable content.
 */
export function ProductBand() {
  const full = assignTiers(toBoardRows(WR_PPR_2025)).tiers;
  // A divider counts its whole tier, even where only its first rows are shown.
  const size = new Map(full.map((t) => [t.letter, t.rows.length]));
  const tiers = rowsBetween(full, FROM, TO);

  return (
    <FeatureSection id="rankings" tone="stage" className="py-12 sm:py-20 lg:py-28">
      <div className="grid items-end gap-x-16 gap-y-5 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)]">
        <SectionTitle id="rankings">
          <span className="block">The board.</span>
          <span className="block">Then the player.</span>
        </SectionTitle>
        {/* A phone has the heading say it: the board and the player follow at once. */}
        <p className="hidden max-w-[30rem] text-[17px] leading-[1.6] text-pretty text-mute sm:block">
          Signed in, this is the product: any season since 2020, all four positions or one at a time, tiered
          where the points drop off, and every player&rsquo;s weeks behind his number.
        </p>
      </div>

      <div className="mt-8 grid gap-x-12 gap-y-10 sm:mt-12 sm:gap-y-14 xl:grid-cols-[minmax(0,8fr)_minmax(0,4fr)]">
        <div className="min-w-0">
          {/* The board's recipe, said the way the real board says it. */}
          <p className="mb-3 text-[15px] text-mute">
            Wide receivers, scored under <span className="font-semibold text-ink">PPR</span> for the 2025 season,
            by season total.
          </p>
          {/* The board below is a picture to assistive tech (inert, hidden); this is what it says. */}
          <div className="sr-only">
            <ol aria-label="The top ten wide receivers of 2025 under PPR, by season total">
              {tiers.flatMap((tier) =>
                tier.rows.map((row) => (
                  <li key={row.playerId}>
                    {row.rank}, {row.name}, {row.team}, {row.points.toFixed(1)} points, tier {tier.letter}
                  </li>
                )),
              )}
            </ol>
          </div>
          <div aria-hidden inert className="select-none">
            <div className="hidden sm:block">
              <FilterBar
                profiles={PRESETS}
                profileId={3}
                onProfile={noop}
                season={2025}
                onSeason={noop}
                scope="season"
                onScope={noop}
                position="WR"
                onPosition={noop}
                find=""
                onFind={noop}
              />
            </div>
            <table className="mt-2 w-full border-separate border-spacing-0 text-sm [mask-image:linear-gradient(to_bottom,black_86%,transparent)]">
              <thead>
                <tr className="text-left [&>th]:h-9 [&>th]:border-b [&>th]:border-line [&>th]:font-label [&>th]:text-[12px] [&>th]:font-semibold [&>th]:text-mute">
                  <th className={COL.rank}>#</th>
                  <th className={COL.player}>Player</th>
                  <th className={COL.pos}>Pos</th>
                  <th className={COL.team}>Team</th>
                  <th className={COL.games}>G</th>
                  <th className={COL.primary}>Points</th>
                  <th className={COL.secondary}>Per G</th>
                  <th className={`${COL.move} whitespace-nowrap`}>vs 0 PPR</th>
                </tr>
              </thead>
              {tiers.map((tier, i) => (
                // A phone shows three receivers across the first break, so the stage stays
                // about one screen: the board, not a scroll through it. A tbody's first row
                // is its tier's divider.
                <tbody
                  key={tier.letter}
                  className={i > 1 ? "max-sm:hidden" : i === 1 ? "max-sm:[&>tr:nth-child(n+3)]:hidden" : ""}
                >
                  <TierHeader
                    letter={tier.letter}
                    count={size.get(tier.letter) ?? tier.rows.length}
                    high={tier.high}
                    low={tier.low}
                    unit="pts"
                    colSpan={COLUMN_COUNT}
                  />
                  {tier.rows.map((row) => {
                    const was = WR_PPR_FROM_ZERO_2025[row.playerId];
                    return (
                      <PlayerRow
                        key={row.playerId}
                        row={row}
                        delta={was === undefined ? null : was - row.rank}
                        baseline={200}
                        metric="points"
                        settle={false}
                        avatar={false}
                        lit={row.playerId === FOLLOWED}
                      />
                    );
                  })}
                </tbody>
              ))}
            </table>
          </div>
          {/* What the colour and the letters mean, said once; a phone has the board's own legend in the app. */}
          <dl className="mt-6 hidden gap-x-10 gap-y-3 text-[14px] text-mute sm:grid sm:grid-cols-2">
            <div className="flex items-baseline gap-3">
              <dt className="w-8 shrink-0 font-display text-[26px] leading-none font-black text-ki-text italic">
                S
              </dt>
              <dd>Tiers break where the points drop off.</dd>
            </div>
            <div className="flex items-baseline gap-3">
              <dt className="tabular w-8 shrink-0 font-semibold text-q-good">23.4</dt>
              <dd>Per game in green: inside a 12-team league&rsquo;s starters at the position.</dd>
            </div>
          </dl>
        </div>

        <div id="players" className="min-w-0 scroll-mt-20">
          <PlayerReport />
        </div>
      </div>
    </FeatureSection>
  );
}
