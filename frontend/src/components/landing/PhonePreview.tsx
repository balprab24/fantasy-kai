"use client";

import { useState } from "react";
import { Movement } from "@/components/Movement";
import { WeeklyChart } from "@/components/player/WeeklyChart";
import { PlayerAvatar } from "@/components/rankings/PlayerAvatar";
import { positionHue } from "@/components/rankings/PositionBadge";
import { LogoMark } from "@/components/shell/Sidebar";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { assignTiers, formatPoints, toBoardRows, type BoardRow, type Tier, type TierLetter } from "@/lib/board";
import { HALF_PPR_2025, MCCAFFREY_2025_PPR, PPR_VS_ZERO_2025 } from "./previewData";

export type Screen = "rankings" | "scoring" | "players";

const SCREENS: { value: Screen; label: string }[] = [
  { value: "rankings", label: "Rankings" },
  { value: "scoring", label: "Scoring" },
  { value: "players", label: "Players" },
];

/**
 * Which screen a landing section shows: its own, if it has one. Above the
 * first section the board; "Coming next" has no screen of its own and keeps
 * the last one, the player page.
 */
function screenFor(section: string | null): Screen {
  if (section === "rankings" || section === "scoring" || section === "players") return section;
  return section === null ? "rankings" : "players";
}

/**
 * The product on a phone, drawn from its own components and real 2025 rows
 * (`previewData.ts`). A picture, not an app: `inert`, so nothing inside takes
 * focus or a click, and hidden from screen readers, because the section beside
 * it already says in words what it shows.
 *
 * The screen follows the section being read; the control under the phone
 * switches it by hand. On a phone the preview sits once above the sections
 * rather than beside them, and the control is the only way to change it.
 *
 * Not the board's own `PlayerRow`: its columns step away at *viewport*
 * breakpoints, so inside a 280px frame on a desktop screen it would draw every
 * column. These rows reuse the board's parts -- avatar, position hue, points,
 * movement, tiers -- at a phone's width.
 */
export function PhonePreview({ section }: { section: string | null }) {
  // A screen picked by hand holds only while the reader stays in the section
  // they picked it in; scrolling on hands the phone back to the page. Derived,
  // not synced in an effect: `picked` records where it was chosen.
  const [picked, setPicked] = useState<{ screen: Screen; in: string | null } | null>(null);
  const screen = picked && picked.in === section ? picked.screen : screenFor(section);

  return (
    <div className="flex flex-col items-center gap-5">
      <div
        aria-hidden
        inert
        className="relative w-[300px] shrink-0 rounded-[48px] bg-[#0c0e15] p-[10px] shadow-[0_40px_90px_-40px_rgb(0_0_0/0.95),inset_0_0_0_1px_rgb(255_255_255/0.08)]"
      >
        <div className="relative h-[584px] overflow-hidden rounded-[38px] bg-paper">
          <div className="absolute top-2.5 left-1/2 z-20 h-[26px] w-[92px] -translate-x-1/2 rounded-full bg-black" />
          <div className="tabular flex h-11 items-end justify-between px-7 pb-1 text-[12px] font-semibold text-ink">
            <span>9:41</span>
            <span className="flex items-center gap-1">
              <span className="h-2.5 w-4 rounded-[2px] border border-ink/70" />
            </span>
          </div>
          {SCREENS.map(({ value }) => (
            <div
              key={value}
              className={`phone-screen absolute inset-x-0 top-11 bottom-0 ${
                screen === value ? "opacity-100" : "pointer-events-none opacity-0"
              }`}
            >
              {value === "rankings" && <RankingsScreen />}
              {value === "scoring" && <ScoringScreen />}
              {value === "players" && <PlayerScreen />}
            </div>
          ))}
          {/* The list runs off the bottom of the glass, as a real one would. */}
          <div className="pointer-events-none absolute inset-x-0 bottom-0 z-10 h-16 bg-gradient-to-t from-paper to-transparent" />
        </div>
      </div>
      <SegmentedControl
        legend="Show on the phone"
        hideLegend
        options={SCREENS}
        value={screen}
        onChange={(v) => setPicked({ screen: v as Screen, in: section })}
      />
    </div>
  );
}

function AppBar({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <div className="border-b border-line px-4 pt-2 pb-3">
      <div className="flex items-center gap-2">
        <LogoMark size={18} />
        <span className="font-display text-[19px] leading-none font-bold tracking-tight">{title}</span>
      </div>
      {children && <div className="mt-2 flex items-center gap-2 text-[11px] text-mute">{children}</div>}
    </div>
  );
}

function Pill({ children, on = false }: { children: React.ReactNode; on?: boolean }) {
  return (
    <span
      className={`rounded-md px-2 py-1 ${
        on
          ? "bg-field-soft font-medium text-energy-text shadow-[inset_0_0_0_1px_rgb(77_163_255/0.45)]"
          : "text-mute"
      }`}
    >
      {children}
    </span>
  );
}

/** The same glyph the board's tier rows carry (`TierHeader`), at phone size. */
const GLYPH: Record<TierLetter, string> = {
  S: "border-ki text-ki shadow-[0_0_10px_-2px_rgb(255_138_61/0.6)]",
  A: "border-ink text-ink",
  B: "border-line-strong text-ink",
  C: "border-line-strong text-mute",
  D: "border-line-strong text-mute",
};

function TierLabel({ letter }: { letter: TierLetter }) {
  return (
    <div className="flex items-center gap-2 px-4 pt-3 pb-1.5">
      <span
        className={`inline-flex h-5 w-6 -skew-x-12 items-center justify-center rounded-[3px] border bg-paper ${GLYPH[letter]}`}
      >
        <span className="font-display skew-x-12 text-[12px] leading-none font-bold">{letter}</span>
      </span>
      <span className="text-[10px] font-semibold tracking-[0.1em] text-mute uppercase">
        Power Tier {letter}
      </span>
      <span className="h-px flex-1 bg-line" />
    </div>
  );
}

function Row({ row, right }: { row: BoardRow; right: React.ReactNode }) {
  const hue = positionHue(row.position);
  return (
    <div className="mx-2 mb-0.5 flex h-11 items-center gap-2 rounded-md bg-raised px-2">
      <span className="tabular w-4 text-right text-[12px] text-mute">{row.rank}</span>
      <PlayerAvatar name={row.name} position={row.position} espnId={null} />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] leading-tight font-medium text-ink">{row.name}</span>
        <span className="tabular block text-[11px] leading-tight text-mute">
          <span className={`font-semibold ${hue.text}`}>
            {row.position}
            {row.posRank}
          </span>{" "}
          {row.team}
        </span>
      </span>
      <span className="text-right">{right}</span>
    </div>
  );
}

/** The first `count` rows of a tiered board, still grouped by tier. */
function firstRows(tiers: Tier[], count: number): Tier[] {
  return tiers.reduce<Tier[]>((out, tier) => {
    const room = count - out.reduce((n, t) => n + t.rows.length, 0);
    return room > 0 ? [...out, { ...tier, rows: tier.rows.slice(0, room) }] : out;
  }, []);
}

function RankingsScreen() {
  const { tiers } = assignTiers(toBoardRows(HALF_PPR_2025));
  return (
    <div>
      <AppBar title="Rankings">
        <span>2025 season</span>
        <Pill on>Half PPR</Pill>
        <Pill>PPR</Pill>
        <Pill>0 PPR</Pill>
      </AppBar>
      {firstRows(tiers, 10).map((tier) => {
        const rows = tier.rows;
        return (
          <div key={tier.letter}>
            <TierLabel letter={tier.letter} />
            {rows.map((row) => (
              <Row
                key={row.playerId}
                row={row}
                right={
                  <span className="tabular text-[13px] font-semibold text-ink">{formatPoints(row.points)}</span>
                }
              />
            ))}
          </div>
        );
      })}
    </div>
  );
}

function ScoringScreen() {
  const rows = toBoardRows(PPR_VS_ZERO_2025.map((p) => p.row));
  const was = new Map(PPR_VS_ZERO_2025.map((p) => [p.row.playerId, p.zeroPprRank]));
  return (
    <div>
      <AppBar title="Rankings">
        <span>2025 season</span>
        <Pill>Half PPR</Pill>
        <Pill on>PPR</Pill>
        <Pill>0 PPR</Pill>
      </AppBar>
      <p className="px-4 pt-3 pb-2 text-[11px] leading-snug text-mute">
        Switched from 0 PPR. Arrows show how far each player moved.
      </p>
      {rows.map((row) => (
        <Row
          key={row.playerId}
          row={row}
          right={
            <span className="flex flex-col items-end leading-tight">
              <span className="tabular text-[13px] font-semibold text-ink">{formatPoints(row.points)}</span>
              <span className="tabular text-[11px]">
                <Movement delta={(was.get(row.playerId) ?? row.rank) - row.rank} baseline={200} />
              </span>
            </span>
          }
        />
      ))}
      <p className="mx-4 mt-3 rounded-md border border-line px-3 py-2.5 text-[11px] leading-snug text-mute">
        Under PPR a catch is worth <span className="tabular text-ink">1</span> point, a rushing or
        receiving yard <span className="tabular text-ink">0.1</span>, and a rushing or receiving
        touchdown <span className="tabular text-ink">6</span>.
      </p>
    </div>
  );
}

function PlayerScreen() {
  const season = MCCAFFREY_2025_PPR;
  return (
    <div>
      <AppBar title="Players" />
      <div className="flex items-center gap-3 px-4 pt-3">
        <PlayerAvatar name="Christian McCaffrey" position="RB" espnId={null} />
        <div className="min-w-0">
          <p className="truncate text-[14px] leading-tight font-semibold text-ink">Christian McCaffrey</p>
          <p className="text-[11px] text-mute">
            <span className="font-semibold text-pos-rb">RB</span> {season.teams.join("/")}, age {season.age}
          </p>
        </div>
      </div>
      <dl className="mx-4 mt-3 grid grid-cols-3 gap-px overflow-hidden rounded-md bg-line text-center">
        {[
          ["Points", formatPoints(season.points)],
          ["Per game", formatPoints(season.pointsPerGame)],
          ["Finish", `RB${season.posRank}`],
        ].map(([label, value]) => (
          <div key={label} className="bg-raised px-1 py-2">
            <dt className="text-[10px] text-mute">{label}</dt>
            <dd className="tabular text-[14px] font-semibold text-ink">{value}</dd>
          </div>
        ))}
      </dl>
      {/* The chart thins its week labels below `sm` -- a viewport breakpoint,
          which a 300px frame on a desktop screen never reaches -- so the
          same thinning is applied here by hand. */}
      <div className="px-3 pt-4 [&_figcaption>span:first-child]:text-[11px] [&_ol[aria-hidden]>li:nth-child(even)]:invisible">
        <WeeklyChart season={season} position="RB" scoringLabel="PPR" />
      </div>
    </div>
  );
}
