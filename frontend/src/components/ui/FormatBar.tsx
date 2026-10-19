"use client";

import { useId } from "react";
import { profileCaption, profileLabel } from "@/lib/profiles";
import type { ScoringProfile } from "@/lib/types";

/**
 * The scoring switch, set as the product's headline control: every ruleset
 * you can score against, side by side, each saying what it pays a catch -- the
 * one thing the presets disagree on -- so the choice explains itself.
 *
 * Points are never stored; they are computed per request against whichever
 * ruleset is chosen here, which is why switching re-orders a board instead of
 * filtering it. A row of radios rather than a dropdown, because comparing the
 * options is the point, and real radio inputs inside a fieldset rather than
 * buttons with `role="radio"`: the native group gives one Tab stop and arrow
 * keys that move and select, for free.
 *
 * It wraps rather than scrolls: a member's own rulesets are exactly the ones
 * that would fall off the end of a scroller nobody knows is there.
 */
export function FormatBar({
  profiles,
  selected,
  onSelect,
  size = "md",
  legend = "Scoring",
  className = "",
}: {
  profiles: ScoringProfile[];
  selected: number | null;
  onSelect: (id: number) => void;
  size?: "md" | "sm";
  legend?: string;
  className?: string;
}) {
  const name = useId();
  const face = size === "md" ? "min-h-14 px-3 py-2 sm:px-4" : "min-h-11 px-2.5 py-1.5 sm:px-3";

  return (
    <fieldset className={`min-w-0 max-w-full ${className}`}>
      <legend className="sr-only">{legend}</legend>
      {/* Three to a row on a phone, so five rulesets are two even rows; from
          sm, one row that is as wide as its options and wraps if a member has
          many rulesets. */}
      <div className="grid max-w-full grid-cols-3 gap-1 rounded-control bg-well p-1 shadow-[inset_0_0_0_1px_var(--color-line)] sm:flex sm:w-fit sm:flex-wrap">
        {profiles.map((p) => {
          const checked = p.id === selected;
          return (
            <label key={p.id} className="relative min-w-0 sm:min-w-[8.5rem] sm:flex-1">
              <input
                type="radio"
                name={name}
                value={p.id}
                checked={checked}
                onChange={() => onSelect(p.id)}
                className="peer sr-only"
              />
              <span
                className={`relative flex h-full cursor-pointer flex-col justify-center rounded-[6px] text-left transition-colors duration-150 select-none peer-focus-visible:outline-2 peer-focus-visible:outline-offset-1 peer-focus-visible:outline-energy ${face} ${
                  checked ? "bg-lift text-ink" : "text-mute hover:bg-track hover:text-ink"
                }`}
              >
                <span className={`block max-w-[11rem] truncate leading-tight ${size === "md" ? "text-[15px]" : "text-[14px]"} ${checked ? "font-semibold" : "font-medium"}`}>
                  {profileLabel(p)}
                </span>
                <span className={`mt-0.5 block truncate text-[11px] leading-tight sm:text-[12px] ${checked ? "text-mute" : "text-faint"}`}>
                  {profileCaption(p)}
                </span>
                <span
                  aria-hidden
                  className={`slash absolute inset-x-2.5 bottom-0 h-[3px] bg-energy transition-opacity duration-200 ${checked ? "opacity-100" : "opacity-0"}`}
                />
              </span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
