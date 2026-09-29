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
// Re-run this same script unchanged after each Phase 11 step so the numbers
// compare.
//
// Since 2026-09-29 every read needs an account, so the run signs in once and
// every virtual user carries the token:
//
//   K6_EMAIL=you@example.com K6_PASSWORD=... k6 run --vus 1 --duration 60s perf/rankings.js
//
// The Phase 3.5 baseline in docs/perf/baseline.md was measured anonymously. The
// difference is one HS256 verification per request -- microseconds against a
// 12 ms p50 -- but Phase 11 re-captures its baseline with this script before it
// compares anything, so the two runs differ in nothing else.
import http from 'k6/http';
import { check } from 'k6';

// 2025 is pinned on purpose. The API defaults to the current season, and 2026
// has its schedule loaded but no stat lines published yet -- so the default
// would score zero rows and report a beautiful, meaningless p95.
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SEASON = __ENV.SEASON || '2025';

// All four presets, because §9's cache keys on the ruleset hash rather than the
// profile id: hitting a single profile would give Phase 11 a 100% hit rate on one
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

// Signs in once, before any virtual user starts. The access token lives 15
// minutes, which covers every run this file describes. No credentials, no run:
// registering a fresh account each time would leave test users in a database
// whose full dump has already carried test accounts into production (F8).
export function setup() {
  const email = __ENV.K6_EMAIL;
  const password = __ENV.K6_PASSWORD;
  if (!email || !password) {
    throw new Error('set K6_EMAIL and K6_PASSWORD: every read needs an account since 2026-09-29');
  }
  const res = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } });
  if (res.status !== 200) {
    throw new Error(`sign-in failed with ${res.status}; the run would measure 401s`);
  }
  return { token: JSON.parse(res.body).accessToken };
}

export default function (data) {
  const position = pick(POSITIONS);
  const url = `${BASE}/api/v1/rankings`
    + `?profileId=${pick(PROFILES)}`
    + `&season=${SEASON}`
    + (position ? `&position=${position}` : '')
    + `&scope=${pick(SCOPES)}`
    + `&size=50`;

  const res = http.get(url, { headers: { Authorization: `Bearer ${data.token}` } });

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
