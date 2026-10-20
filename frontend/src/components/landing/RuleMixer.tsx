"use client";

import { useEffect, useId, useMemo, useRef, useState } from "react";
import { formatPoints, toBoardRows, type BoardRow } from "@/lib/board";
import { CATCH_RATES, PASS_TDS, castOf, describeMix, formatRate, mixKey, slotOn, type Catch, type PassTouchdown } from "@/lib/mixer";
import { biggestMovers } from "@/lib/movers";
import { Movement } from "../Movement";
import { PositionChip } from "../ui/PositionChip";
import { HERO_SEASON, HERO_THROUGH_WEEK, MIXER_BOARDS, type MixKey } from "./heroData";

/** Rows on the board: ten from sm, eight on a phone (the last two step back). */
const TOP = 10;
const PHONE_TOP = 8;

/** Every board's rows with positional ranks derived, once, at module load. */
const BOARDS = Object.fromEntries(
  Object.entries(MIXER_BOARDS).map(([key, rows]) => [key, toBoardRows(rows)]),
) as Record<MixKey, BoardRow[]>;
const CAST = castOf(Object.values(BOARDS), TOP);
const BY_ID = Object.fromEntries(
  Object.entries(BOARDS).map(([key, rows]) => [key, new Map(rows.map((r) => [r.playerId, r]))]),
) as Record<MixKey, Map<number, BoardRow>>;

/** The one moment the hero moves on its own: catches go from half a point to a full one. */
const DEMO_AT_MS = 1600;

function ordinal(n: number) {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) return `${n}th`;
  return `${n}${({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th"}`;
}

/**
 * The hero: the real rankings board of the season being played, with two of
 * a league's settings as dials -- what a catch is worth, and what a passing
 * touchdown is worth. Six combinations, every one captured from the real
 * engine (`heroData.ts`, `scripts/capture-landing.mjs`); turning a dial sorts
 * the board to that capture, and the rows travel to their new places, so the
 * re-order explains itself. Nothing is scored here.
 *
 * It moves on its own exactly once: when half of it is first on screen, catches
 * go from half a point to a full point -- the switch most leagues argue about
 * -- and then it waits. Never under reduced motion; never after a visitor has
 * touched a dial. The visible rows are drawn for the eye (`aria-hidden`); a
 * screen reader gets the same board as an ordered list, and a visitor's own
 * change is announced.
 */
export function RuleMixer() {
  const [rec, setRec] = useState<Catch>(0.5);
  const [passTd, setPassTd] = useState<PassTouchdown>(4);
  const [previous, setPrevious] = useState<MixKey | null>(null);
  const [touched, setTouched] = useState(false);
  const [announced, setAnnounced] = useState("");
  const frame = useRef<HTMLDivElement>(null);
  // Read inside the demo's timer, which outlives the render that armed it.
  const touchedRef = useRef(false);
  const recName = useId();
  const tdName = useId();

  const key = mixKey(rec, passTd);
  const board = BOARDS[key];
  const prevMap = useMemo(
    () => (previous ? new Map(BOARDS[previous].map((r) => [r.playerId, r.rank])) : null),
    [previous],
  );
  const spotlight = useMemo(() => {
    if (!prevMap) return null;
    const { up, down } = biggestMovers(board, prevMap, { window: TOP, count: 1 });
    const best = [up[0], down[0]].filter(Boolean).sort((a, b) => Math.abs(b!.delta) - Math.abs(a!.delta))[0];
    return best ?? null;
  }, [board, prevMap]);

  const change = (nextRec: Catch, nextTd: PassTouchdown, byVisitor: boolean) => {
    const next = mixKey(nextRec, nextTd);
    if (next === key) return;
    setPrevious(key);
    setRec(nextRec);
    setPassTd(nextTd);
    if (byVisitor) {
      touchedRef.current = true;
      setTouched(true);
      const top3 = BOARDS[next]
        .slice(0, 3)
        .map((r) => `${r.rank} ${r.name}`)
        .join(", ");
      setAnnounced(`Under ${describeMix(nextRec, nextTd)}: ${top3}.`);
    }
  };

  // The one automatic switch: when half the board is first in view, motion is
  // welcome, and nobody has touched a dial yet.
  useEffect(() => {
    const el = frame.current;
    if (!el || touched) return;
    if (!window.matchMedia("(prefers-reduced-motion: no-preference)").matches) return;
    let timer: number | undefined;
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && timer === undefined) {
          timer = window.setTimeout(() => {
            io.disconnect();
            // A visitor who reached a dial first has the board; the demo yields.
            if (touchedRef.current) return;
            setPrevious("0.5|4");
            setRec(1);
          }, DEMO_AT_MS);
        }
      },
      { threshold: 0.5 },
    );
    io.observe(el);
    return () => {
      io.disconnect();
      if (timer !== undefined) window.clearTimeout(timer);
    };
    // Once: the demo never re-arms, whatever happens after.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div
      ref={frame}
      className="relative overflow-hidden rounded-plate border border-line bg-surface shadow-float"
    >
      <div className="flex flex-wrap items-end justify-between gap-x-4 gap-y-1 px-5 pt-5 sm:px-6 sm:pt-6">
        <h2 className="type-heading text-[1.75rem] text-ink">Rankings</h2>
        <p className="pb-0.5 text-[13px] text-mute">
          {HERO_SEASON} season, through week {HERO_THROUGH_WEEK}
        </p>
      </div>

      {/* The two dials: a league's settings, one rate each. */}
      <div className="mt-4 grid gap-2 px-5 sm:grid-cols-2 sm:gap-3 sm:px-6">
        <Dial
          name={recName}
          legend="Points per catch"
          short="Per catch"
          options={CATCH_RATES}
          value={rec}
          onChange={(v) => change(v as Catch, passTd, true)}
        />
        <Dial
          name={tdName}
          legend="Passing touchdown"
          options={PASS_TDS}
          value={passTd}
          onChange={(v) => change(rec, v as PassTouchdown, true)}
        />
      </div>

      <p className="sr-only" aria-live="polite">
        {announced}
      </p>
      {/* The board for assistive tech, in rank order. */}
      <ol className="sr-only" aria-label={`Top ${TOP} under ${describeMix(rec, passTd)}`}>
        {board.slice(0, TOP).map((r) => (
          <li key={r.playerId}>
            {r.rank}, {r.name}, {r.position}
            {r.posRank}, {formatPoints(r.points)} points
          </li>
        ))}
      </ol>

      <div className="mt-4 border-t border-line">
        <div
          aria-hidden
          className="relative h-[calc(var(--rows)*var(--row))] overflow-hidden select-none [--row:3rem] [--rows:8] sm:[--row:3.25rem] sm:[--rows:10]"
        >
          {CAST.map((id) => {
            const slot = slotOn(board, id, TOP);
            const row = BY_ID[key].get(id) ?? (previous ? BY_ID[previous].get(id) : undefined);
            if (!row) return null;
            const shown = slot < TOP;
            const was = prevMap?.get(id);
            const delta = shown && prevMap ? (was === undefined ? null : was - row.rank) : 0;
            return (
              <div
                key={id}
                className={`absolute inset-x-0 top-0 grid h-[var(--row)] grid-cols-[2.25rem_minmax(0,1fr)_4.25rem_3.25rem] items-center border-b border-line px-3 transition-[transform,opacity] duration-700 ease-[var(--ease-out)] motion-reduce:transition-none sm:grid-cols-[2.75rem_minmax(0,1fr)_4.5rem_4.5rem_4rem] sm:px-4 ${
                  shown ? "opacity-100" : "opacity-0"
                } ${slot >= PHONE_TOP ? "max-sm:opacity-0" : ""}`}
                style={{ transform: `translateY(calc(${slot} * var(--row)))` }}
              >
                <span className="type-figure text-right text-[16px] text-mute">{shown ? row.rank : ""}</span>
                <span className="flex min-w-0 items-center gap-2.5 pl-3">
                  <span className="truncate text-[15px] font-semibold text-ink">{row.name}</span>
                  <PositionChip position={row.position} rank={row.posRank} className="max-sm:hidden" />
                </span>
                <span className="hidden text-[13px] font-medium text-mute sm:block">{row.team}</span>
                <span className="tabular text-right text-[15px] font-semibold text-ink">{formatPoints(row.points)}</span>
                <span className="text-right text-[12px]">{prevMap && shown && <Movement delta={delta} baseline={60} />}</span>
              </div>
            );
          })}
        </div>
      </div>

      <div className="flex min-h-[4.5rem] flex-wrap items-center justify-between gap-x-6 gap-y-1 px-5 py-4 text-[14px] sm:px-6">
        <p className="text-mute">
          Scored as <span className="font-semibold text-ink">{describeMix(rec, passTd)}</span>
        </p>
        {spotlight && (
          <p className="text-mute">
            Biggest move: <span className="font-semibold text-ink">{spotlight.name}</span>,{" "}
            <span className="tabular">
              {ordinal(spotlight.previous)} to {ordinal(spotlight.rank)}
            </span>
          </p>
        )}
      </div>
    </div>
  );
}

/**
 * One of a league's settings as a row of radios: the legend says which rate,
 * the options are its values. Real radio inputs in a fieldset, so the group is
 * one Tab stop and the arrow keys move and select.
 */
function Dial({
  name,
  legend,
  short,
  options,
  value,
  onChange,
}: {
  name: string;
  legend: string;
  /** Shown instead of the legend under 360px, where the full one truncated. */
  short?: string;
  options: readonly number[];
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <fieldset className="flex min-w-0 items-center justify-between gap-3 rounded-control bg-well py-1 pr-1 pl-3.5 shadow-[inset_0_0_0_1px_var(--color-line)]">
      <legend className="sr-only">{legend}</legend>
      <span aria-hidden className="truncate text-[13px] text-mute">
        {short ? (
          <>
            <span className="max-[359px]:hidden">{legend}</span>
            <span className="min-[360px]:hidden">{short}</span>
          </>
        ) : (
          legend
        )}
      </span>
      <span className="flex shrink-0 gap-1">
        {options.map((o) => (
          <label key={o} className="relative">
            <input
              type="radio"
              name={name}
              value={o}
              checked={o === value}
              onChange={() => onChange(o)}
              className="peer sr-only"
            />
            <span className="relative flex h-10 min-w-11 cursor-pointer items-center justify-center rounded-[6px] px-3 font-label text-[17px] font-bold text-mute transition-colors select-none peer-checked:bg-lift peer-checked:text-ink peer-checked:shadow-[inset_0_-2px_0_var(--color-energy)] peer-focus-visible:outline-2 peer-focus-visible:outline-offset-1 peer-focus-visible:outline-energy hover:text-ink">
              {formatRate(o)}
            </span>
          </label>
        ))}
      </span>
    </fieldset>
  );
}
