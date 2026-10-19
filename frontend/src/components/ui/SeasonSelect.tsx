"use client";

import { Icon } from "./Icon";

/**
 * The season, as a native select themed for a console strip. A select rather
 * than a segmented control: seasons are navigation, not a comparison, and
 * seven of them wrapped a phone's console into three rows with a lone 2020 on
 * the last one. The browser's own list keeps the keyboard and screen-reader
 * behaviour it always had; only the closed control is restyled.
 */
export function SeasonSelect({
  value,
  options,
  onChange,
  variant = "bare",
  label = "Season",
}: {
  value: number;
  /** Newest first. */
  options: number[];
  onChange: (season: number) => void;
  /** `field`: on its own, with the input's well and 3:1 edge. `bare`: inside a console strip. */
  variant?: "bare" | "field";
  label?: string;
}) {
  const look =
    variant === "field"
      ? "h-10 rounded-control bg-well shadow-[inset_0_0_0_1px_var(--color-line-strong)] hover:shadow-[inset_0_0_0_1px_var(--color-mute)]"
      : "h-10 rounded-[6px] bg-transparent hover:bg-well md:h-9";
  return (
    <label className="relative flex">
      <span className="sr-only">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className={`tabular cursor-pointer appearance-none pr-9 pl-3.5 text-sm font-semibold text-ink transition-[background-color,box-shadow] ${look}`}
      >
        {options.map((y) => (
          <option key={y} value={y}>
            {y}
          </option>
        ))}
      </select>
      <Icon
        name="chevronDown"
        size={14}
        className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-mute"
      />
    </label>
  );
}
