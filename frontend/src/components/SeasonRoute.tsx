"use client";

import { useId } from "react";
import { routePath, runs, tangents, type Point } from "@/lib/trace";

/**
 * A season drawn as a route: the line through each week's points, like an
 * analyst's telestrator stroke over the film. It is the product's one drawn
 * mark and its one anime echo -- a ki trail -- but it is data first: it passes
 * through every week, breaks at a bye, and never bulges past a real score
 * (`lib/trace.ts`).
 *
 * Drawn in the coordinates of the box the season's bars occupy (`width` by
 * `height`), so it lands on the bar tops. `lead` brings the line in from past
 * the left edge and `arrow` ends it with a head just past the last week; the
 * SVG overflows its box so both can cross the plate's edge.
 */
export function SeasonRoute({
  values,
  max,
  min = 0,
  width,
  height,
  lead = 0,
  arrow = false,
  draw = false,
  drawDelay = 780,
  drawDuration = 1100,
  stretch = false,
  className = "",
}: {
  /** One per week slot, in week order; null where no game was played. */
  values: readonly (number | null)[];
  max: number;
  min?: number;
  width: number;
  height: number;
  lead?: number;
  arrow?: boolean;
  /** Draw the line on as it mounts (skipped under reduced motion). */
  draw?: boolean;
  drawDelay?: number;
  drawDuration?: number;
  /** Fill a box of any shape: the stroke keeps its width, there is no arrowhead to distort. */
  stretch?: boolean;
  className?: string;
}) {
  const id = useId();
  const span = max - min || 1;
  const slot = width / Math.max(1, values.length);
  const points = values.map((v, i): Point | null =>
    v === null ? null : { x: (i + 0.5) * slot, y: height - ((v - min) / span) * height },
  );
  const d = routePath(points);
  const all = runs(points).filter((r) => r.length >= 2);
  if (!d || all.length === 0) return null;

  const first = all[0][0];
  const firstTangent = tangents(all[0])[0];
  const last = all[all.length - 1];
  const end = last[last.length - 1];
  const endTangent = tangents(last)[last.length - 1];

  const unit = (t: number) => {
    const len = Math.hypot(1, t);
    return { x: 1 / len, y: t / len };
  };
  const u0 = unit(firstTangent);
  const u1 = unit(endTangent);
  const leadFrom = { x: first.x - u0.x * lead, y: first.y - u0.y * lead };
  const shaft = slot * 0.9;
  const tip = { x: end.x + u1.x * shaft, y: end.y + u1.y * shaft };
  // The head: two strokes back from the tip, 30 degrees either side of the line.
  const head = (sign: number) => {
    const a = Math.atan2(u1.y, u1.x) + Math.PI + sign * (Math.PI / 6);
    return { x: tip.x + Math.cos(a) * 9, y: tip.y + Math.sin(a) * 9 };
  };
  const h1 = head(1);
  const h2 = head(-1);
  const fixed = stretch ? { vectorEffect: "non-scaling-stroke" as const } : {};

  return (
    <svg
      aria-hidden
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio={stretch ? "none" : "xMidYMid meet"}
      className={`pointer-events-none absolute inset-0 size-full overflow-visible ${className}`}
      fill="none"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {lead > 0 && (
        <defs>
          <linearGradient
            id={`${id}-lead`}
            gradientUnits="userSpaceOnUse"
            x1={leadFrom.x}
            y1={leadFrom.y}
            x2={first.x}
            y2={first.y}
          >
            <stop offset="0" stopColor="var(--color-energy)" stopOpacity="0" />
            <stop offset="1" stopColor="var(--color-energy)" stopOpacity="1" />
          </linearGradient>
        </defs>
      )}
      {/* No glow: the line is data, and a halo around it is decoration (2026-10-05). */}
      <g>
        {lead > 0 && (
          <path
            d={`M${leadFrom.x} ${leadFrom.y} L${first.x} ${first.y}`}
            stroke={`url(#${id}-lead)`}
            strokeWidth={2.5}
            {...fixed}
          />
        )}
        <path
          d={d}
          stroke="var(--color-energy)"
          strokeWidth={2.5}
          pathLength={1}
          className={draw ? "draw" : undefined}
          style={
            draw
              ? ({ "--draw-delay": `${drawDelay}ms`, "--draw-duration": `${drawDuration}ms` } as React.CSSProperties)
              : undefined
          }
          {...fixed}
        />
        {arrow && !stretch && (
          <path
            d={`M${end.x} ${end.y} L${tip.x} ${tip.y} M${h1.x} ${h1.y} L${tip.x} ${tip.y} L${h2.x} ${h2.y}`}
            stroke="var(--color-energy)"
            strokeWidth={2.5}
            className={draw ? "appear" : undefined}
            style={draw ? ({ "--appear-delay": `${drawDelay + drawDuration - 100}ms` } as React.CSSProperties) : undefined}
          />
        )}
      </g>
    </svg>
  );
}
