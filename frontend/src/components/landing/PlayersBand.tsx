"use client";

import { useEffect, useId, useRef, useState } from "react";
import { RulesetSwitch } from "@/components/RulesetSwitch";
import { BUTTON_SECONDARY } from "@/components/ui/buttons";
import { WeeklyChart } from "@/components/player/WeeklyChart";
import { StatTable, type LeadColumn } from "@/components/player/StatTable";
import { PositionBadge, positionHue, positionName } from "@/components/rankings/PositionBadge";
import { formatPoints } from "@/lib/board";
import { matchup } from "@/lib/player";
import { columnsFor } from "@/lib/playerStats";
import type { CareerSeason, ScoringProfile } from "@/lib/types";
import { FeatureSection, SectionTitle } from "./FeatureSection";
import {
  NACUA_2025_BY_RULESET,
  NACUA_2025_LOG,
  NACUA_LIT_WEEK,
  type LogGame,
  type Ruleset,
} from "./previewData";

/** The three presets as `/scoring-profiles` lists them (V3), for the page's own switch. */
const PRESETS: ScoringProfile[] = [
  { id: 1, name: "Standard", preset: true },
  { id: 2, name: "Half PPR", preset: true },
  { id: 3, name: "Full PPR", preset: true },
];
const RULESET_OF: Record<number, Ruleset> = { 1: "0 PPR", 2: "Half PPR", 3: "PPR" };

/** The box score's columns the band keeps: the ones a catch rate prices. */
const KEPT = new Set(["rec", "rec_yd", "rec_td"]);

/**
 * Games the log shows until it is opened: eight from sm, where it stands beside
 * the plate and the chart and should end about where they do, and six on a
 * phone, where it follows the plate. Both include the lit week.
 */
const SHOWN = 8;
const PHONE_SHOWN = 6;

const POSITION = "WR";

type Row = { kind: "game"; game: LogGame } | { kind: "season"; season: CareerSeason };

/**
 * Where the page cuts to the product as it really looks: a full-width band in
 * its own dark skin (`.primetime`), the landing's second and last stretch of
 * Prime time after the hero's poster. It opens the player the board above
 * followed -- Puka Nacua, 20th to 2nd -- the way a member opens him: the
 * plate, his weeks charted (`WeeklyChart`), and his game log (`StatTable`),
 * the player page's own parts.
 *
 * Its switch is the player page's own (`RulesetSwitch`), and it works: every
 * figure on the band re-prices from captures of all three presets, so a
 * visitor sees one rule change a whole page -- the season, every week's bar,
 * every game in the log. One game is lit, his most catches (13, week 4), where
 * the rate does the most. No cut-out: the landing shows no likeness, so the
 * plate is type. The log keeps the columns a catch rate prices and leaves the
 * rest of the box score to the product, and shows its first games until it is
 * opened, so it ends about where the plate and the chart beside it do.
 */
export function PlayersBand() {
  const [profileId, setProfileId] = useState(3);
  // A switch lights the week the band points at a shade stronger for a moment, so the eye lands on the figure that moved.
  const [pulse, setPulse] = useState(false);
  const pulseTimer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(pulseTimer.current), []);
  const switchTo = (id: number) => {
    if (id === profileId) return;
    setProfileId(id);
    setPulse(true);
    window.clearTimeout(pulseTimer.current);
    pulseTimer.current = window.setTimeout(() => setPulse(false), 700);
  };
  const [allGames, setAllGames] = useState(false);
  const logId = useId();
  const ruleset = RULESET_OF[profileId];
  const season = NACUA_2025_BY_RULESET[ruleset];
  const lit = NACUA_2025_LOG.find((g) => g.week === NACUA_LIT_WEEK)!;

  const lead: LeadColumn<Row>[] = [
    {
      key: "wk",
      label: "Wk",
      title: "Week",
      group: "Game",
      align: "left",
      width: "w-14",
      cell: (r) => (r.kind === "game" ? r.game.week : null),
    },
    {
      key: "opp",
      label: "Opp",
      title: "Opponent",
      group: "Game",
      align: "left",
      width: "w-24",
      cell: (r) =>
        r.kind === "game" ? (
          matchup(r.game.opponent, r.game.home)
        ) : (
          <span className="text-mute">{r.season.gamesPlayed} games</span>
        ),
    },
    {
      key: "pts",
      label: "Pts",
      title: `Fantasy points under ${ruleset}`,
      group: "Fantasy",
      cell: (r) => (
        <span className="tabular font-semibold text-ink">
          {formatPoints(r.kind === "game" ? r.game.points[ruleset] : r.season.points)}
        </span>
      ),
    },
  ];
  const groups = columnsFor(POSITION, NACUA_2025_LOG)
    .map((g) => ({ ...g, columns: g.columns.filter((c) => KEPT.has(c.key)) }))
    .filter((g) => g.columns.length > 0);
  const rows: Row[] = NACUA_2025_LOG.map((game) => ({ kind: "game", game }));

  return (
    <FeatureSection id="players" tone="stage" className="py-14 sm:py-20 lg:py-28">
      <div className="grid items-end gap-x-16 gap-y-5 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)]">
        <SectionTitle id="players">
          <span className="block">Every player.</span>
          <span className="block">Every game.</span>
        </SectionTitle>
        <p className="max-w-[30rem] text-[17px] leading-[1.6] text-pretty text-mute">
          Open anyone on the board: his season under your scoring, every week charted, every game in the log. Change
          the scoring and all of it changes with it.
        </p>
      </div>

      {/* The player page's console: its scoring switch, live. */}
      <div className="mt-10 flex flex-wrap items-center justify-between gap-x-6 gap-y-3 sm:mt-14">
        <p className="text-[15px] text-mute">
          Puka Nacua&rsquo;s 2025 regular season, scored under <span className="font-semibold text-ink">{ruleset}</span>.
        </p>
        <div className="max-w-full rounded-control bg-surface p-1.5">
          <RulesetSwitch
            profiles={PRESETS}
            selected={profileId}
            onSelect={switchTo}
            hideLegend
            variant="bare"
          />
        </div>
      </div>

      <div className="mt-5 grid gap-x-12 gap-y-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
        <div className="flex min-w-0 flex-col gap-12">
          <Plate season={season} ruleset={ruleset} />
          {/* A phone keeps the band to about one screen: the plate and the log carry it there. */}
          <div className="max-sm:hidden">
            <WeeklyChart season={season} position={POSITION} scoringLabel={ruleset} />
          </div>
        </div>

        <div className="min-w-0">
          {/* The one game the band points at, said in words; its row is lit below. */}
          {/* Three lines held on a phone, so switching the scoring never moves the table under it. */}
          <p className="mb-4 text-[15px] leading-relaxed text-pretty text-mute max-sm:min-h-[4.9em]" aria-live="polite">
            <span className="font-semibold text-ink">Week {lit.week}</span>, at home to Indianapolis:{" "}
            {lit.stats.rec} catches for {lit.stats.rec_yd} yards, and{" "}
            <span className="tabular font-semibold text-ink">{formatPoints(lit.points[ruleset])}</span> points under{" "}
            {ruleset}.
          </p>
          <div id={logId}>
            <StatTable
              caption={`Puka Nacua's 2025 game log, regular season: fantasy points under ${ruleset}, and his catches, receiving yards and receiving touchdowns`}
              lead={lead}
              groups={groups}
              rows={rows}
              source={(r) => (r.kind === "game" ? r.game : r.season)}
              rowKey={(r) => (r.kind === "game" ? String(r.game.week) : "season")}
              rowProps={(r) => {
                if (r.kind !== "game") return {};
                const i = NACUA_2025_LOG.indexOf(r.game);
                // Closed, the log shows its first games and a dashed rule for the ones it leaves out; the season is always there.
                const closed = allGames
                  ? []
                  : [
                      i >= SHOWN ? "hidden" : i >= PHONE_SHOWN ? "max-sm:hidden" : "",
                      i === SHOWN - 1 ? "[&>td]:border-dashed [&>td]:border-line-strong" : "",
                      i === PHONE_SHOWN - 1 ? "max-sm:[&>td]:border-dashed max-sm:[&>td]:border-line-strong" : "",
                    ];
                const tint = r.game.week !== NACUA_LIT_WEEK ? "" : pulse ? "[&>td]:bg-energy/30" : "[&>td]:bg-energy/15";
                return { className: [tint, ...closed].join(" ") };
              }}
              foot={{ row: { kind: "season", season }, label: "Season" }}
            />
          </div>
          <button
            type="button"
            aria-expanded={allGames}
            aria-controls={logId}
            onClick={() => setAllGames((open) => !open)}
            className={`${BUTTON_SECONDARY} mt-4`}
          >
            {allGames ? "Show fewer" : `Show all ${NACUA_2025_LOG.length} games`}
          </button>
        </div>
      </div>
    </FeatureSection>
  );
}

/**
 * The player page's plate, as type: the name, who he is, and the season's
 * line under the chosen scoring -- what the photo stands beside in the app.
 */
function Plate({ season, ruleset }: { season: CareerSeason; ruleset: Ruleset }) {
  return (
    <article aria-labelledby="plate-name" className="rounded-plate bg-surface px-5 pt-6 pb-6 sm:px-7">
      <h3 id="plate-name" className="type-display text-[clamp(2.5rem,4.4vw,3.5rem)] text-ink">
        Puka Nacua
      </h3>
      <p className="mt-2.5 flex flex-wrap gap-x-4 gap-y-1 text-[15px] text-mute">
        <span className={`font-semibold capitalize ${positionHue(POSITION).text}`}>{positionName(POSITION)}</span>
        <span>{season.teams.join(", ")}</span>
        <span className="tabular">
          Age {season.age} in {season.season}
        </span>
      </p>
      <dl className="mt-5 flex flex-wrap items-baseline gap-x-6 gap-y-2">
        <Figure label="Rank">
          <PositionBadge
            position={POSITION}
            rank={season.posRank}
            basis={`by ${season.season} season points`}
            className="font-label text-[2rem] leading-none font-bold italic"
          />
        </Figure>
        <Figure label="Points" unit="pts">
          {formatPoints(season.points)}
        </Figure>
        <Figure label="Per game" unit="a game">
          {formatPoints(season.pointsPerGame)}
        </Figure>
      </dl>
      <p className="mt-2 text-[13px] text-mute">
        {season.season} regular season, {season.gamesPlayed} games, scored under {ruleset}
      </p>
    </article>
  );
}

/** A figure of the season line, as the player page sets it: the number big, its unit small after it. */
function Figure({ label, unit, children }: { label: string; unit?: string; children: React.ReactNode }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <dt className="sr-only">{label}</dt>
      <dd className="flex items-baseline gap-1.5">
        <span className="type-stat text-[2rem] text-ink">{children}</span>
        {unit && <span className="text-[13px] text-mute">{unit}</span>}
      </dd>
    </div>
  );
}
