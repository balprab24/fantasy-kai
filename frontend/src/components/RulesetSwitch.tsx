"use client";

import type { ScoringProfile } from "@/lib/types";

/**
 * The hero control, and the product's whole argument in one widget.
 *
 * Points are never stored -- they are computed per request against whichever
 * ruleset you pick, which is why switching this re-orders the board instead of
 * filtering it. It is a segmented control rather than a dropdown on purpose:
 * the comparison is the point, so the alternatives stay visible.
 */
export function RulesetSwitch({
  profiles,
  selected,
  onSelect,
}: {
  profiles: ScoringProfile[];
  selected: number | null;
  onSelect: (id: number) => void;
}) {
  return (
    <div
      role="radiogroup"
      aria-label="Scoring ruleset"
      className="inline-flex flex-wrap gap-px overflow-hidden rounded-md border border-line-strong bg-line-strong"
    >
      {profiles.map((profile) => {
        const active = profile.id === selected;
        return (
          <button
            key={profile.id}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onSelect(profile.id)}
            className={
              active
                ? "bg-field px-3.5 py-2 text-sm font-medium text-white"
                : "bg-raised px-3.5 py-2 text-sm text-mute transition-colors hover:bg-field-soft hover:text-ink"
            }
          >
            {profile.name}
          </button>
        );
      })}
    </div>
  );
}
