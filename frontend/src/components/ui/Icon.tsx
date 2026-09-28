/**
 * The product's icon set: a dozen hand-drawn strokes on a 24px grid, inline so
 * there is no dependency and no request. Decorative by default -- every icon
 * sits next to text, or next to an `sr-only` label, that says what it means.
 */

const PATHS = {
  home: "M4 11.5 12 5l8 6.5M6.5 10v9h11v-9M10 19v-5h4v5",
  teams: "M12 3.5 19 6v5.5c0 4.2-3 7.4-7 9-4-1.6-7-4.8-7-9V6l7-2.5ZM9 12l2 2 4-4",
  rankings: "M5 19V13M10 19V9M15 19v-4M20 19V5M3 19.5h18",
  startSit: "M7 4v16M7 4 4 7M7 4l3 3M17 20V4M17 20l-3-3M17 20l3-3",
  waivers: "M12 12 18 6M12 3a9 9 0 1 0 9 9M12 7a5 5 0 1 0 5 5",
  trades: "M4 8h14M18 8l-3.5-3.5M18 8l-3.5 3.5M20 16H6M6 16l3.5-3.5M6 16l3.5 3.5",
  draft: "M4 5h16v14H4zM4 10h16M4 14.5h16M9.5 5v14M14.5 5v14",
  players: "M12 12a3.8 3.8 0 1 0 0-7.6 3.8 3.8 0 0 0 0 7.6ZM4.5 20c.8-3.8 3.7-6 7.5-6s6.7 2.2 7.5 6",
  scoring: "M5 6h9M18 6h1M5 12h3M12 12h7M5 18h11M20 18h-1M16 4v4M10 10v4M18 16v4",
  search: "M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13ZM15.3 15.3 20 20",
  menu: "M4 7h16M4 12h16M4 17h16",
  close: "M6 6l12 12M18 6 6 18",
  account: "M12 12.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM6.5 18.6c1.3-2 3.2-3.1 5.5-3.1s4.2 1.1 5.5 3.1M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z",
  arrowRight: "M5 12h14M13 6l6 6-6 6",
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({
  name,
  size = 20,
  className = "",
}: {
  name: IconName;
  size?: number;
  className?: string;
}) {
  return (
    <svg
      aria-hidden
      focusable="false"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={`shrink-0 ${className}`}
    >
      <path d={PATHS[name]} />
    </svg>
  );
}
