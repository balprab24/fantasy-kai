"use client";

import { useId } from "react";

export interface Segment {
  value: string;
  label: React.ReactNode;
  title?: string;
}

/**
 * Where the control sits, which decides what draws its edge:
 * - `track`: on its own, in a well of its own.
 * - `bare`: inside the rankings console, whose strip is already the track.
 * - `pill`: floating over imagery (the landing's ruleset chip).
 */
export type SegmentedVariant = "track" | "bare" | "pill";

const TRACK: Record<SegmentedVariant, string> = {
  track: "gap-0.5 rounded-control bg-well p-0.5",
  bare: "gap-0.5",
  pill: "gap-1 rounded-full bg-surface p-1 shadow-[inset_0_1px_0_rgb(255_255_255/0.07),var(--shadow-float)]",
};

// Selected: the chosen step (lift), full ink, and a 2px energy base line --
// blue is "you are here". Inner radius is the track's minus its padding.
const OPTION: Record<SegmentedVariant, string> = {
  track:
    "h-10 rounded-[2px] px-3 text-sm md:h-8 peer-checked:shadow-[inset_0_-2px_0_var(--color-energy)]",
  bare: "h-10 rounded-[2px] px-3 text-sm md:h-8 peer-checked:shadow-[inset_0_-2px_0_var(--color-energy)]",
  pill: "h-8 gap-1.5 rounded-full px-3.5 text-[13px] before:hidden before:size-1.5 before:rounded-full before:bg-energy peer-checked:before:block",
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
}) {
  const name = useId();

  return (
    <fieldset className="min-w-0 max-w-full">
      <legend className={hideLegend ? "sr-only" : "type-label mb-1.5 text-mute"}>{legend}</legend>
      {/* Wraps rather than scrolls: a clipped option behind a hidden
          scrollbar is an option nobody knows is there -- and a signed-in
          user's own rulesets are exactly the ones that would fall off. */}
      <div className={`flex w-fit max-w-full flex-wrap ${TRACK[variant]}`}>
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
              className={`flex cursor-pointer items-center whitespace-nowrap text-mute transition-[color,background-color,box-shadow] select-none hover:text-ink peer-checked:bg-lift peer-checked:font-semibold peer-checked:text-ink peer-focus-visible:outline-2 peer-focus-visible:outline-offset-1 peer-focus-visible:outline-energy ${OPTION[variant]}`}
            >
              {option.label}
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
