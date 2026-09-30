"use client";

import { profileLabel } from "@/lib/profiles";
import type { ScoringProfile } from "@/lib/types";
import { SegmentedControl, type SegmentedVariant } from "./ui/SegmentedControl";

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
  hideLegend = false,
  variant = "track",
}: {
  profiles: ScoringProfile[];
  selected: number | null;
  onSelect: (id: number) => void;
  hideLegend?: boolean;
  variant?: SegmentedVariant;
}) {
  return (
    <SegmentedControl
      legend="Scoring"
      options={profiles.map((p) => ({ value: String(p.id), label: profileLabel(p) }))}
      value={selected === null ? null : String(selected)}
      onChange={(id) => onSelect(Number(id))}
      hideLegend={hideLegend}
      variant={variant}
    />
  );
}
