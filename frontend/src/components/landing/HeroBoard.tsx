"use client";

import { RulesetSwitch } from "@/components/RulesetSwitch";
import { COL, COLUMN_COUNT, PlayerRow } from "@/components/rankings/PlayerRow";
import { TierHeader } from "@/components/rankings/TierHeader";
import { assignTiers, toBoardRows, type BoardRow, type Tier } from "@/lib/board";
import { boardLayout, castOf, type Placement } from "@/lib/heroDemo";
import { HERO_BOARDS_2026, HERO_LEAGUE_DETAIL, HERO_PROFILES, HERO_RULESETS, type HeroRuleset } from "./heroData";

/** Places each board shows; the window shows about seven, and the rest sit under its fade. */
export const HERO_TOP = 10;

const BOARDS = Object.fromEntries(HERO_RULESETS.map((r) => [r, toBoardRows(HERO_BOARDS_2026[r])])) as Record<
  HeroRuleset,
  BoardRow[]
>;
const TIERS = Object.fromEntries(HERO_RULESETS.map((r) => [r, assignTiers(BOARDS[r]).tiers])) as Record<
  HeroRuleset,
  Tier[]
>;
const RANK = Object.fromEntries(
  HERO_RULESETS.map((r) => [r, new Map(BOARDS[r].map((row) => [row.playerId, row.rank]))]),
) as Record<HeroRuleset, Map<number, number>>;
const CAST = castOf(
  HERO_RULESETS.map((r) => HERO_BOARDS_2026[r]),
  HERO_TOP,
);
const LAYOUT = Object.fromEntries(
  HERO_RULESETS.map((r) => [
    r,
    boardLayout(
      BOARDS[r],
      TIERS[r].map((t) => ({ letter: t.letter, first: t.rows[0].rank })),
      CAST,
      HERO_TOP,
    ),
  ]),
) as Record<HeroRuleset, ReturnType<typeof boardLayout>>;
const LETTERS = [...new Set(HERO_RULESETS.flatMap((r) => [...LAYOUT[r].dividers.keys()]))];

const ID_OF = Object.fromEntries(HERO_PROFILES.map((p) => [p.ruleset, p.id])) as Record<HeroRuleset, number>;
const RULESET_OF = new Map(HERO_PROFILES.map((p) => [p.id, p.ruleset]));

/** The board's top five under a scoring, in words: the caption's and the announcement's. */
export function topFive(ruleset: HeroRuleset) {
  return BOARDS[ruleset]
    .slice(0, 5)
    .map((r) => `${r.rank}, ${r.name}`)
    .join("; ");
}

/**
 * A place as CSS: rows of the board's own height (`--row`, as `PlayerRow` sets
 * it) and dividers of the band `BoardSkeleton` gives one. A row in flight
 * steps back, so the rows crossing it are not printed over at full strength.
 */
function placed({ rows, dividers, shown }: Placement, flying = false): React.CSSProperties {
  return {
    transform: `translateY(calc(var(--row) * ${rows} + 4.25rem * ${dividers}))`,
    opacity: shown ? (flying ? 0.35 : 1) : 0,
  };
}

/** Whether a place changed between two scorings: the rows that travel. */
function moves(a: Placement | undefined, b: Placement | undefined) {
  return !!a && !!b && (a.rows !== b.rows || a.dividers !== b.dividers);
}

/**
 * The hero's board: the rankings page as a member opens it, cut to what the
 * hero is about -- its name, its recipe line, the real scoring switch (live:
 * `onSelect`) and the board's tier dividers and rows (`TierHeader`,
 * `PlayerRow`). The rest of the app's console is left out rather than drawn
 * dead, and so are the team, games and gauge columns (landing CSS over the
 * real row, below), so the board reads in rank, name, points and move.
 *
 * "My league" says what it is wherever it is chosen (`HERO_LEAGUE_DETAIL`),
 * and the recipe line stays one line whatever it says, so the switch under it
 * never moves when it is used. Every move is counted from PPR -- the
 * column says so ("Δ vs PPR") -- so the same board always shows the same
 * moves, and under PPR none.
 *
 * So rows can travel when the scoring changes, each is its own one-row
 * fixed-layout table placed by `lib/heroDemo.ts`'s `boardLayout`; the `COL`
 * widths line the tables up as one. A row leaving the top ten sinks below the
 * window and fades; one arriving rises from there; rows in flight step back
 * (`inFlight`, against `from`, the board left). The moved rows' flash and the
 * fetching sweep are the board's own. No avatars: the landing shows no
 * likeness. The rows are hidden from assistive tech -- the caption and the
 * announcement say what they show.
 */
export function HeroBoard({
  ruleset,
  from,
  flash,
  sweep,
  inFlight,
  onSelect,
  onConsoleFocus,
}: {
  ruleset: HeroRuleset;
  /** The board just left, for the rows in flight. */
  from: HeroRuleset;
  flash: boolean;
  sweep: boolean;
  /** Just switched: the travelling rows step back until they are most of the way there. */
  inFlight: boolean;
  onSelect: (ruleset: HeroRuleset) => void;
  onConsoleFocus: () => void;
}) {
  const current = new Map(BOARDS[ruleset].map((row) => [row.playerId, row]));
  const tier = new Map<string, Tier>(TIERS[ruleset].map((t) => [t.letter, t]));
  // The travel, and a quicker fade for stepping back in flight and for arriving or leaving.
  const travel =
    "motion-safe:[transition:transform_850ms_cubic-bezier(0.16,1,0.3,1),opacity_250ms_ease-out]";
  // Counted from PPR, always: the baseline every league starts from.
  const move = (row: BoardRow) => {
    const was = RANK.PPR.get(row.playerId);
    return was === undefined ? null : was - row.rank;
  };

  return (
    <div className="h-full px-4 pt-6 sm:px-7 sm:pt-8 lg:pr-[max(2rem,calc((100vw-1320px)/2+2rem))]">
      <p className="type-title">Rankings</p>
      {/* Always one line, whatever it says, so the switch below never moves under a finger: a phone drops
          "Scored under" (the line still sits under the page's name) and cuts rather than wraps. */}
      <p className="mt-2 text-[15px] leading-normal text-mute max-sm:truncate">
        <span className="max-sm:hidden">Scored under </span>
        <span className="font-semibold text-ink">{ruleset}</span>
        {ruleset === "My league" && <> &middot; {HERO_LEAGUE_DETAIL}</>}
      </p>

      {/* The console, cut to its scoring switch: the one control the hero is about, and it works. */}
      <div
        data-hero-console
        onFocusCapture={onConsoleFocus}
        className="mt-5 w-fit max-w-full rounded-control bg-surface p-1.5 sm:mt-6"
      >
        <RulesetSwitch
          profiles={HERO_PROFILES}
          selected={ID_OF[ruleset]}
          onSelect={(id) => onSelect(RULESET_OF.get(id)!)}
          hideLegend
          variant="bare"
        />
      </div>
      {/* A phone folds the Δ column into each row's second line; this says what its arrows count from. */}
      <p className="mt-2 text-[12px] text-mute sm:hidden">&#9650;&#9660; places vs PPR</p>

      {/* The board's columns, cut by landing CSS over the real row: no team (4th), no games (5th), no gauge;
          the player column narrows between lg and xl so the rest fit the window. */}
      <div
        aria-hidden
        className="relative mt-2 [&_.bg-track]:hidden [&_tr>*:nth-child(4)]:hidden [&_tr>*:nth-child(5)]:hidden lg:[&_tr>*:nth-child(2)]:w-56 xl:[&_tr>*:nth-child(2)]:w-72"
      >
        {sweep && (
          <div className="absolute inset-x-0 top-0 z-20 h-0.5 overflow-hidden">
            <div className="sweep h-full w-2/5 bg-energy" />
          </div>
        )}
        <table className="w-full table-fixed border-separate border-spacing-0 text-sm">
          <thead>
            <tr className="text-left [&>th]:h-9 [&>th]:border-b [&>th]:border-line [&>th]:font-label [&>th]:text-[12px] [&>th]:font-semibold [&>th]:text-mute">
              <th className={COL.rank}>#</th>
              <th className={COL.player}>Player</th>
              <th className={COL.pos}>Pos</th>
              <th className={COL.team}>Team</th>
              <th className={COL.games}>G</th>
              <th className={COL.primary}>Points</th>
              <th className={COL.secondary}>Per G</th>
              <th className={`${COL.move} whitespace-nowrap`}>&Delta; vs PPR</th>
            </tr>
          </thead>
        </table>

        <div
          className="relative [--row:48px] sm:[--row:44px]"
          style={{ height: `calc(var(--row) * ${HERO_TOP + 1} + 4.25rem * ${LETTERS.length})` }}
        >
          {LETTERS.map((letter) => {
            const t = tier.get(letter);
            const at = LAYOUT[ruleset].dividers.get(letter);
            return (
              <div
                key={letter}
                className={`absolute inset-x-0 top-0 h-[4.25rem] ${travel}`}
                style={at && t ? placed(at) : { opacity: 0 }}
              >
                {t && (
                  <table className="w-full table-fixed border-separate border-spacing-0 text-sm">
                    <tbody>
                      <TierHeader
                        letter={t.letter}
                        count={t.rows.length}
                        high={t.high}
                        low={t.low}
                        unit="pts"
                        colSpan={COLUMN_COUNT}
                      />
                    </tbody>
                  </table>
                )}
              </div>
            );
          })}
          {CAST.map((id) => {
            const row = current.get(id);
            return (
              <div
                key={id}
                data-hero-row={id}
                className={`absolute inset-x-0 top-0 ${travel}`}
                style={placed(
                  LAYOUT[ruleset].rows.get(id)!,
                  inFlight && moves(LAYOUT[from].rows.get(id), LAYOUT[ruleset].rows.get(id)),
                )}
              >
                {row && (
                  <table className="w-full table-fixed border-separate border-spacing-0 text-sm">
                    <tbody>
                      <PlayerRow
                        row={row}
                        delta={move(row)}
                        baseline={60}
                        metric="points"
                        settle={flash}
                        avatar={false}
                      />
                    </tbody>
                  </table>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
