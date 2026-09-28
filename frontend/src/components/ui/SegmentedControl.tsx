"use client";

import { useId } from "react";

export interface Segment {
  value: string;
  label: React.ReactNode;
  title?: string;
}

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
}) {
  const name = useId();

  return (
    <fieldset className="min-w-0 max-w-full">
      <legend className={hideLegend ? "sr-only" : "eyebrow mb-1.5"}>{legend}</legend>
      {/* Wraps rather than scrolls: a clipped option behind a hidden
          scrollbar is an option nobody knows is there -- and a signed-in
          user's own rulesets are exactly the ones that would fall off. */}
      <div className="flex w-fit max-w-full flex-wrap gap-0.5 rounded-lg border border-line-strong bg-raised p-0.5">
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
            <span className="flex h-10 cursor-pointer items-center rounded-md px-3 text-sm whitespace-nowrap text-mute transition-colors select-none hover:text-ink peer-checked:bg-field-soft peer-checked:font-medium peer-checked:text-energy-text peer-checked:shadow-[inset_0_0_0_1px_rgb(77_163_255/0.45)] peer-focus-visible:outline-2 peer-focus-visible:outline-offset-1 peer-focus-visible:outline-energy md:h-8">
              {option.label}
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
