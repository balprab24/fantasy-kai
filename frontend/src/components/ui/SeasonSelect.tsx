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
}: {
  value: number;
  /** Newest first. */
  options: number[];
  onChange: (season: number) => void;
}) {
  return (
    <label className="relative flex">
      <span className="sr-only">Season</span>
      <select
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="tabular h-10 cursor-pointer appearance-none rounded-[2px] bg-transparent pr-8 pl-3 text-sm font-semibold text-ink transition-colors hover:bg-well md:h-8"
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
        className="pointer-events-none absolute top-1/2 right-2.5 -translate-y-1/2 text-mute"
      />
    </label>
  );
}
