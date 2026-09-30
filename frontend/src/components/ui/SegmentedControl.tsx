"use client";

import { useId } from "react";

export interface Segment {
  value: string;
  label: React.ReactNode;
  title?: string;
  /** Extra classes on this option's face (the landing's entrance uses it). */
  className?: string;
}

/**
 * Where the control sits, which decides what draws its edge:
 * - `track`: on its own, in a well of its own.
 * - `bare`: inside the rankings console, whose strip is already the track.
 * - `pill`: the landing hero's ruleset switch, a well of its own in the
 *   plate's console, whose chosen step is a thumb that slides.
 */
export type SegmentedVariant = "track" | "bare" | "pill";

const TRACK: Record<SegmentedVariant, string> = {
  track: "gap-0.5 rounded-control bg-well p-0.5",
  bare: "gap-0.5",
  pill: "relative grid auto-cols-fr grid-flow-col rounded-full bg-well p-1",
};

// Selected: the chosen step (lift), full ink, and a 2px energy base line --
// blue is "you are here". Inner radius is the track's minus its padding. The
// pill's chosen step is not the option's own background but a thumb that
// slides between options (below), carrying the blue dot with it.
const OPTION: Record<SegmentedVariant, string> = {
  track:
    "h-10 rounded-[2px] px-3 text-sm md:h-8 peer-checked:bg-lift peer-checked:font-semibold peer-checked:shadow-[inset_0_-2px_0_var(--color-energy)]",
  bare: "h-10 rounded-[2px] px-3 text-sm md:h-8 peer-checked:bg-lift peer-checked:font-semibold peer-checked:shadow-[inset_0_-2px_0_var(--color-energy)]",
  pill: "relative z-[1] h-8 justify-center rounded-full pr-3.5 pl-6 text-[13px] font-semibold",
};

/**
 * A single-choice control whose alternatives stay visible -- the right shape
 * whenever comparing the options is the point, which on a rankings board it
 * nearly always is.
 *
 * Built from real radio inputs inside a fieldset rather than buttons with
 * `role="radio"`. The native group gives the full keyboard contract for free
 * (one Tab stop, arrow keys move and select) and a legend that names the
 * group; ARIA roles only *announce* that contract and leave you to implement
 * it, which is how the previous version ended up with neither.
 */
export function SegmentedControl({
  legend,
  options,
  value,
  onChange,
  hideLegend = false,
  variant = "track",
  thumbFrom,
}: {
  legend: string;
  options: Segment[];
  value: string | null;
  onChange: (value: string) => void;
  /**
   * For a toolbar whose options name themselves. The legend stays in the
   * markup, screen-reader only, so the group is still announced by name.
   */
  hideLegend?: boolean;
  variant?: SegmentedVariant;
  /**
   * Pill only: the option the thumb stands on before the landing's one-time
   * entrance slides it to the chosen one (`.thumb-entrance` in globals.css).
   * Omitted, the thumb is simply where the value is.
   */
  thumbFrom?: number;
}) {
  const name = useId();
  const chosen = options.findIndex((o) => o.value === value);

  return (
    <fieldset className="min-w-0 max-w-full">
      <legend className={hideLegend ? "sr-only" : "type-label mb-1.5 text-mute"}>{legend}</legend>
      {/* Wraps rather than scrolls: a clipped option behind a hidden
          scrollbar is an option nobody knows is there -- and a signed-in
          user's own rulesets are exactly the ones that would fall off. */}
      <div className={`${variant === "pill" ? "" : "flex flex-wrap"} w-fit max-w-full ${TRACK[variant]}`}>
        {variant === "pill" && chosen >= 0 && (
          <span
            aria-hidden
            className={`pointer-events-none absolute inset-y-1 left-1 rounded-full bg-lift before:absolute before:top-1/2 before:left-3 before:size-1.5 before:-translate-y-1/2 before:rounded-full before:bg-energy motion-safe:transition-transform motion-safe:duration-300 motion-safe:ease-out ${
              thumbFrom !== undefined ? "thumb-entrance" : ""
            }`}
            style={
              {
                width: `calc((100% - 0.5rem) / ${options.length})`,
                transform: `translateX(${chosen * 100}%)`,
                "--thumb-from": `translateX(${(thumbFrom ?? chosen) * 100}%)`,
              } as React.CSSProperties
            }
          />
        )}
        {options.map((option) => (
          <label key={option.value} title={option.title} className="relative">
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={option.value === value}
              onChange={() => onChange(option.value)}
              className="peer sr-only"
            />
            <span
              className={`flex cursor-pointer items-center whitespace-nowrap text-mute transition-[color,background-color,box-shadow] select-none hover:text-ink peer-checked:text-ink peer-focus-visible:outline-2 peer-focus-visible:outline-offset-1 peer-focus-visible:outline-energy ${OPTION[variant]} ${option.className ?? ""}`}
            >
              {option.label}
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
