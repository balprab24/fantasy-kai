"use client";

import { useId, useState } from "react";
import { STARTERS, bandFor, formatPoints } from "@/lib/board";
import { CATCH_RATES, PASS_TDS, describeMix, mixKey } from "@/lib/mixer";
import { ageOn } from "@/lib/player";
import { starterShare } from "@/lib/playerView";
import { PerformanceChart } from "../player/PerformanceChart";
import { positionGlow, positionHue } from "../rankings/PositionBadge";
import { QUALITY_TEXT } from "../rankings/PlayerRow";
import { initials } from "../rankings/PlayerAvatar";
import { PositionChip } from "../ui/PositionChip";
import { TabPanel, Tabs } from "../ui/Tabs";
import { FOLLOWED, FOLLOWED_SEASON, HERO_CAPTURED, MIXER_BOARDS } from "./heroData";

type Tab = "weeks" | "rules";

/**
 * The player view, shown: the followed player's season as a member opens it,
 * built from the product's own parts (the sidebar's figures, `PerformanceChart`, the
 * Scoring tab's ladder) over captured rows. Both tabs work. No likeness -- the
 * stage carries his initials in his position's hue, which is also exactly what
 * the product shows for a player without a headshot.
 */
export function PlayerShowcase() {
  const [tab, setTab] = useState<Tab>("weeks");
  const tabsId = useId();
  const s = FOLLOWED_SEASON;
  const line = STARTERS[FOLLOWED.position as keyof typeof STARTERS];
  const starts = starterShare(s.weeks, line);
  const quality = bandFor(s.posRank, FOLLOWED.position);
  const age = ageOn(FOLLOWED.birthDate, new Date(`${HERO_CAPTURED}T12:00:00Z`));

  // His season total and overall place on each of the hero's six boards.
  const ladder = CATCH_RATES.flatMap((rec) =>
    PASS_TDS.map((td) => {
      const row = MIXER_BOARDS[mixKey(rec, td)].find((r) => r.playerId === FOLLOWED.id);
      return { key: mixKey(rec, td), label: describeMix(rec, td), points: row?.points ?? null, rank: row?.rank ?? null };
    }),
  );
  const top = Math.max(1, ...ladder.map((l) => l.points ?? 0));

  return (
    <div className="overflow-hidden rounded-plate border border-line bg-surface">
      <div className="grid md:grid-cols-[minmax(0,15rem)_minmax(0,1fr)]">
        {/* The stage, without a photograph. */}
        <div aria-hidden className="relative isolate flex h-40 items-end justify-center overflow-hidden md:h-auto md:min-h-56">
          <svg viewBox="0 0 26 26" className="absolute inset-0 -z-10 m-auto h-[110%] w-auto" style={{ color: positionGlow(FOLLOWED.position, 0.1) }}>
            <path d="M9 3h6L9 23H3z" fill="currentColor" />
            <path d="M18 3h5l-6 20h-5z" fill="currentColor" opacity="0.6" />
          </svg>
          <div
            className="absolute inset-0 -z-10"
            style={{ background: `radial-gradient(55% 60% at 50% 85%, ${positionGlow(FOLLOWED.position, 0.22)}, transparent 100%)` }}
          />
          <span
            className="type-display translate-y-[12%] text-[8.5rem] text-transparent md:text-[10rem]"
            style={{ WebkitTextStroke: `2px ${positionGlow(FOLLOWED.position, 0.55)}` }}
          >
            {initials(FOLLOWED.name)}
          </span>
        </div>

        <div className="min-w-0 px-5 pt-2 pb-5 sm:px-7 md:pt-7">
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-[14px] text-mute">
            <PositionChip position={FOLLOWED.position} size="md" />
            <span className="font-medium text-ink">{FOLLOWED.teamName ?? FOLLOWED.team}</span>
            {age !== null && <span>Age {age}</span>}
          </p>
          <h3 className="mt-3 type-display text-[clamp(2.25rem,5vw,3.75rem)] text-ink">{FOLLOWED.name}</h3>
          <dl className="mt-5 grid grid-cols-3 gap-y-4 sm:grid-cols-5">
            <Figure label="Position rank">
              <span className={positionHue(FOLLOWED.position).text}>{FOLLOWED.position}</span>
              <span className={quality ? QUALITY_TEXT[quality] : "text-ink"}>{s.posRank}</span>
            </Figure>
            <Figure label="Points">{formatPoints(s.points)}</Figure>
            <Figure label="Per game">{formatPoints(s.pointsPerGame)}</Figure>
            <Figure label="Games" className="max-sm:hidden">{s.gamesPlayed}</Figure>
            {starts && (
              <Figure label={`Top-${starts.line} weeks`} className="max-sm:hidden">
                {starts.starts}
                <span className="text-[0.6em] text-mute"> of {starts.games}</span>
              </Figure>
            )}
          </dl>
          <p className="mt-4 text-[13px] text-mute">
            {s.season} regular season, scored under <span className="font-semibold text-ink">Half PPR</span>.
          </p>
        </div>
      </div>

      <div className="border-y border-line px-3 sm:px-5">
        <Tabs
          id={tabsId}
          label="Player sections"
          items={[
            { key: "weeks", label: "Week by week" },
            { key: "rules", label: "Every ruleset" },
          ]}
          value={tab}
          onChange={setTab}
        />
      </div>

      <TabPanel tabsId={tabsId} tab={tab} className="px-5 py-6 focus-visible:outline-none sm:px-7">
        {tab === "weeks" ? (
          <PerformanceChart seasons={[s]} position={FOLLOWED.position} scoringLabel="Half PPR" periods={false} initial={s.season} />
        ) : (
          <ul className="space-y-1">
            {ladder.map((l) => (
              <li key={l.key} className="grid grid-cols-[minmax(0,1fr)_4.5rem_3.5rem] items-center gap-x-4 gap-y-1.5 py-1.5 sm:grid-cols-[minmax(0,15rem)_minmax(0,1fr)_4.5rem_3.5rem]">
                <span className="truncate text-[14px] text-ink">{l.label}</span>
                <span aria-hidden className="col-span-3 row-start-2 h-2 overflow-hidden rounded-full bg-track sm:col-span-1 sm:col-start-2 sm:row-start-1">
                  <span className="block h-full rounded-full bg-mute/50" style={{ width: `${((l.points ?? 0) / top) * 100}%` }} />
                </span>
                <span className="tabular text-right text-[15px] font-semibold text-ink">
                  {l.points !== null ? formatPoints(l.points) : "—"}
                </span>
                <span className="tabular text-right text-[13px] text-mute" title="Place on the whole board">
                  {l.rank !== null ? `#${l.rank}` : "61+"}
                </span>
              </li>
            ))}
          </ul>
        )}
      </TabPanel>
    </div>
  );
}

function Figure({ label, children, className = "" }: { label: string; children: React.ReactNode; className?: string }) {
  return (
    <div className={`flex min-w-0 flex-col-reverse ${className}`}>
      <dt className="mt-1 text-[12px] text-mute">{label}</dt>
      <dd className="type-figure text-[28px] text-ink">{children}</dd>
    </div>
  );
}
