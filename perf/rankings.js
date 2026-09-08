// §9 Step 2 -- the baseline load against GET /api/v1/rankings.
//
// Run both passes; the PAIR is the measurement, not either number alone:
//
//   k6 run --vus 1  --duration 60s perf/rankings.js
//   k6 run --vus 20 --duration 60s perf/rankings.js
//
// A scan-bound endpoint degrades gently from 1 VU to 20 because the rows are
// already in shared buffers. A compute-bound one degrades close to linearly,
// because every virtual user redoes the same Java scoring from scratch. That
// contrast is what justifies reaching for the cache before the index, and it is
// the answer to "how did you know the bottleneck was recomputation?".
//
// Re-run this same script unchanged after each Phase 6 step so the numbers
// compare.
import http from 'k6/http';
import { check } from 'k6';

// 2025 is pinned on purpose. The API defaults to the current season, and 2026
// has its schedule loaded but no stat lines published yet -- so the default
// would score zero rows and report a beautiful, meaningless p95.
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SEASON = __ENV.SEASON || '2025';

// All four presets, because §9's cache keys on the ruleset hash rather than the
// profile id: hitting a single profile would give Phase 6 a 100% hit rate on one
// key and flatter the delta. Four presets collapse to four entries no matter how
// many users exist, which is the actual claim being made.
const PROFILES = [1, 2, 3, 4];

// '' is the unfiltered ranking -- all four scorable positions, the heaviest case.
const POSITIONS = ['', 'QB', 'RB', 'WR', 'TE'];
const SCOPES = ['season', 'per_game', 'last4'];

export const options = {
  // §9 asks for p50/p95/p99; k6's default summary reports neither p50 nor p99.
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

function pick(xs) {
  return xs[Math.floor(Math.random() * xs.length)];
}

export default function () {
  const position = pick(POSITIONS);
  const url = `${BASE}/api/v1/rankings`
    + `?profileId=${pick(PROFILES)}`
    + `&season=${SEASON}`
    + (position ? `&position=${position}` : '')
    + `&scope=${pick(SCOPES)}`
    + `&size=50`;

  const res = http.get(url);

  check(res, {
    'status is 200': (r) => r.status === 200,
    // Guards against the failure mode that would silently invalidate the whole
    // baseline: measuring the latency of an empty result set.
    'ranking is not empty': (r) => {
      try {
        return JSON.parse(r.body).content.length > 0;
      } catch (e) {
        return false;
      }
    },
  });
}
