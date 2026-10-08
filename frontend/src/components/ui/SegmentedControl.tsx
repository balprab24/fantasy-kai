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
 * - `stops`: the landing's big switch -- the hero's three scoring formats and
 *   the catch rate -- where each option is a whole figure, not a word. Equal
 *   columns on a well of their own, filling the width they are given; the
 *   chosen step is a thumb that slides between them, so a change is seen
 *   moving rather than blinking.
 */
export type SegmentedVariant = "track" | "bare" | "stops";

const TRACK: Record<SegmentedVariant, string> = {
  track: "flex w-fit flex-wrap gap-0.5 rounded-control bg-well p-0.5",
  bare: "flex w-fit flex-wrap gap-0.5",
  // An edge of its own (Edge, 3:1 on its ground), so the track reads as a control even where the well matches the page.
  stops: "relative grid w-full auto-cols-fr grid-flow-col rounded-control bg-well p-1 shadow-[inset_0_0_0_1px_var(--color-line-strong)]",
};

// Selected: the chosen step (lift), full ink, and a 2px energy base line --
// blue is "you are here". Inner radius is the track's minus its padding. The
// stops' chosen step is not the option's own background but a thumb that
// slides between options (below), carrying the base line with it, and every
// option stays in full ink: there the options are figures to compare, and
// the thumb alone says which is chosen. A face's own layout and height come
// from the caller (`Segment.className`; a height as `min-h-*`, which the
// face's `h-full` cannot override): a figure is laid out by what it says.
const OPTION: Record<SegmentedVariant, string> = {
  track:
    "h-10 items-center whitespace-nowrap rounded-[2px] px-3 text-sm text-mute hover:text-ink md:h-8 peer-checked:bg-lift peer-checked:font-semibold peer-checked:text-ink peer-checked:shadow-[inset_0_-2px_0_var(--color-energy)]",
  bare: "h-10 items-center whitespace-nowrap rounded-[2px] px-3 text-sm text-mute hover:text-ink md:h-8 peer-checked:bg-lift peer-checked:font-semibold peer-checked:text-ink peer-checked:shadow-[inset_0_-2px_0_var(--color-energy)]",
  stops: "relative z-[1] h-full flex-col rounded-[2px] text-ink peer-[:not(:checked)]:hover:bg-track",
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
   * Stops only: the option the thumb stands on before the landing's one-time
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
      <div className={`max-w-full ${TRACK[variant]}`}>
        {variant === "stops" && chosen >= 0 && (
          // Placed by `left`, not a transform: at rest on a fractional
          // translateX it showed a 1px seam partway down its left edge in
          // Chrome captures (tile rasterisation, most likely -- not proven),
          // and placed by `left` it does not. A transform is only the
          // entrance's, which moves and then is gone.
          <span
            aria-hidden
            className={`pointer-events-none absolute inset-y-1 rounded-[2px] bg-lift shadow-[inset_0_-2px_0_var(--color-energy)] motion-safe:transition-[left] motion-safe:duration-300 motion-safe:ease-out ${
              thumbFrom !== undefined ? "thumb-entrance" : ""
            }`}
            style={
              {
                width: `calc((100% - 0.5rem) / ${options.length})`,
                left: `calc(0.25rem + ${chosen} * (100% - 0.5rem) / ${options.length})`,
                "--thumb-from": `translateX(${((thumbFrom ?? chosen) - chosen) * 100}%)`,
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
              className={`flex cursor-pointer transition-[color,background-color,box-shadow] select-none peer-focus-visible:outline-2 peer-focus-visible:outline-offset-1 peer-focus-visible:outline-energy ${OPTION[variant]} ${option.className ?? ""}`}
            >
              {option.label}
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
