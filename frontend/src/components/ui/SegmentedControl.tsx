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
 * - `bare`: inside a strip that is already the track.
 */
export type SegmentedVariant = "track" | "bare";

const TRACK: Record<SegmentedVariant, string> = {
  track: "flex w-fit flex-wrap gap-1 rounded-control bg-well p-1 shadow-[inset_0_0_0_1px_var(--color-line)]",
  bare: "flex w-fit flex-wrap gap-1",
};

// Selected: the chosen step (lift), full ink, and a 2px energy base line --
// blue is "you are here". Inner radius is the track's minus its padding.
const OPTION =
  "h-10 items-center whitespace-nowrap rounded-[6px] px-3.5 text-sm text-mute hover:bg-track hover:text-ink md:h-9 peer-checked:bg-lift peer-checked:font-semibold peer-checked:text-ink peer-checked:shadow-[inset_0_-2px_0_var(--color-energy)]";

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
  fill = false,
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
  /** Below sm, stretch to the row and share it equally (a phone's filter rows). */
  fill?: boolean;
}) {
  const name = useId();

  return (
    <fieldset className={`min-w-0 max-w-full ${fill ? "w-full sm:w-auto" : ""}`}>
      <legend className={hideLegend ? "sr-only" : "type-label mb-1.5 text-mute"}>{legend}</legend>
      {/* Wraps rather than scrolls: a clipped option behind a hidden
          scrollbar is an option nobody knows is there. */}
      <div className={`max-w-full ${TRACK[variant]} ${fill ? "max-sm:w-full max-sm:flex-nowrap" : ""}`}>
        {options.map((option) => (
          <label key={option.value} title={option.title} className={`relative ${fill ? "max-sm:min-w-0 max-sm:flex-1" : ""}`}>
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={option.value === value}
              onChange={() => onChange(option.value)}
              className="peer sr-only"
            />
            <span
              className={`flex cursor-pointer justify-center transition-[color,background-color,box-shadow] select-none peer-focus-visible:outline-2 peer-focus-visible:outline-offset-1 peer-focus-visible:outline-energy ${OPTION}`}
            >
              {option.label}
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
