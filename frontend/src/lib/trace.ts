/**
 * The drawn route: a smooth line through a season's weekly points, as SVG path
 * data. Pure -- no React, no browser -- so `tests/lib.test.ts` checks the
 * geometry itself.
 *
 * Monotone cubic (Steffen's method, the curve d3 calls `curveMonotoneX`): it
 * passes through every point and never overshoots between two of them, so the
 * line never shows a peak or a dip the data does not have. A plain Catmull-Rom
 * curve would bulge past a 39-point week on its way down to a 10-point one.
 *
 * A gap -- a bye, or a week the player did not play -- breaks the line. A route
 * that bridged it would draw a game nobody played.
 */

export interface Point {
  x: number;
  y: number;
}

/** Path data through `points`; `null` breaks the line. Runs shorter than two points draw nothing. */
export function routePath(points: readonly (Point | null)[]): string {
  return runs(points)
    .filter((run) => run.length >= 2)
    .map(monotone)
    .join(" ");
}

/** Consecutive non-null points, split at every gap. */
export function runs(points: readonly (Point | null)[]): Point[][] {
  const out: Point[][] = [];
  let run: Point[] = [];
  for (const p of points) {
    if (p === null) {
      if (run.length) out.push(run);
      run = [];
    } else {
      run.push(p);
    }
  }
  if (run.length) out.push(run);
  return out;
}

/** The tangent (dy/dx) at each point of one run, with no overshoot between points. */
export function tangents(pts: readonly Point[]): number[] {
  const n = pts.length;
  if (n < 2) return n === 1 ? [0] : [];
  if (n === 2) {
    const s = (pts[1].y - pts[0].y) / (pts[1].x - pts[0].x);
    return [s, s];
  }
  const t = new Array<number>(n);
  for (let i = 1; i < n - 1; i++) {
    const [a, b, c] = [pts[i - 1], pts[i], pts[i + 1]];
    const h0 = b.x - a.x;
    const h1 = c.x - b.x;
    const s0 = (b.y - a.y) / h0;
    const s1 = (c.y - b.y) / h1;
    const p = (s0 * h1 + s1 * h0) / (h0 + h1);
    t[i] = (Math.sign(s0) + Math.sign(s1)) * Math.min(Math.abs(s0), Math.abs(s1), 0.5 * Math.abs(p)) || 0;
  }
  const end = (a: Point, b: Point, inner: number) => {
    const h = b.x - a.x;
    return h ? (3 * (b.y - a.y)) / h / 2 - inner / 2 : inner;
  };
  t[0] = end(pts[0], pts[1], t[1]);
  t[n - 1] = end(pts[n - 2], pts[n - 1], t[n - 2]);
  return t;
}

/** One run as a moveto and one cubic per segment. */
function monotone(pts: Point[]): string {
  const t = tangents(pts);
  let d = `M${num(pts[0].x)} ${num(pts[0].y)}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const a = pts[i];
    const b = pts[i + 1];
    const h = (b.x - a.x) / 3;
    d += ` C${num(a.x + h)} ${num(a.y + t[i] * h)} ${num(b.x - h)} ${num(b.y - t[i + 1] * h)} ${num(b.x)} ${num(b.y)}`;
  }
  return d;
}

/** Two decimals is sub-pixel at any size these paths are drawn. */
function num(n: number) {
  return String(Math.round(n * 100) / 100);
}
