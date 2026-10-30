# League sync, team-aware trades, and league-aware rankings: research and build plan

**For:** Codex, as the implementer. **Research by:** Claude, 2026-10-28. **Status:** S0 implemented on `feat/leagues-s0`; S1–S6 remain planned. See `../reviews/2026-10-29-leagues-s0.md` for evidence and remaining checks.
**Owner scope:** redraft leagues first. Deep coverage of Sleeper, ESPN and Yahoo; others are listed as later work.

Part A is what was measured, so the plan can be argued with. Part B is the build, in slices. Part C
is how to work. Read `CLAUDE.md` (invariants, traps), `docs/north-star.md` §4–§8 and `DESIGN.md`
before starting any slice.

---

## Part A: Research findings (measured 2026-10-28 unless marked *unverified*)

### A1. Sleeper

How it was probed: `curl`, no credentials, against `api.sleeper.app`.

| What | Finding |
|---|---|
| Auth | None. The API is read-only and public. Docs: "stay under 1000 API calls per minute, otherwise, you risk being IP-blocked". The limit is **per IP**, so all members share our server's budget. |
| Licence | Free for non-commercial use; commercial use needs a licence from Sleeper. Fantasy-kai is free, with no ads, which keeps it non-commercial. Monetising later reopens this (north-star §8). |
| `GET /v1/state/nfl` | 200 → `{"week":8,"season":"2026","season_type":"regular",…}`. This is the source for the current week. |
| User → leagues | `GET /v1/user/<username>` → `user_id`. Then `GET /v1/user/<user_id>/leagues/nfl/<season>`. **Store `user_id`, never the username**, because usernames can change. |
| `GET /v1/league/<id>` | 200, about 3 KB. Carries `roster_positions` (e.g. `["QB","RB","RB","WR","WR","TE","FLEX","FLEX","DEF","BN"×6]`), `scoring_settings` (a flat key → number map, about 90 keys), and `settings` (`num_teams`, `playoff_teams`, `playoff_week_start`, `trade_deadline`, `reserve_slots`, `taxi_slots`, `type`: 0 in the probe; 1 = keeper and 2 = dynasty are *unverified*), plus `previous_league_id`. |
| `…/rosters` | Array per team: `roster_id`, `owner_id`, `players[]`, `starters[]`, `reserve[]`, `taxi[]`, and `settings.{wins,losses,ties,fpts,fpts_decimal}`. |
| `…/users` | `user_id`, `display_name`, `metadata.team_name`. |
| `…/matchups/<week>` | `roster_id`, `matchup_id` (teams that share a `matchup_id` play each other), `starters`, `points`. *Unverified:* that future weeks already return pairings in a live 2026 league. This is the schedule source, so confirm it in S1 with the owner's own league. |
| `…/transactions/<week>`, `…/traded_picks`, `…/winners_bracket` | All 200. Not needed for v1. |
| `GET /v1/players/nfl` | **14,663,796 bytes** (the docs say about 5 MB; they are out of date), 12,229 players. The docs say fetch it at most once a day. `SleeperCrosswalk` already fetches it, by position. |
| Scoring key names | **They equal our `StatKey` names**: `pass_yd`, `pass_td`, `pass_int`, `pass_2pt`, `rush_yd`, `rush_td`, `rush_2pt`, `rec`, `rec_yd`, `rec_td`, `rec_2pt`, `fum_lost`. Values carry float noise (`pass_yd: 0.03999999910593033`), so round to 4 decimals. |
| Free agents | No endpoint, and none is needed: the pool is every player not on any roster in the league (see A6). |

### A2. ESPN

How it was probed: `curl`, no credentials, against `lm-api-reads.fantasy.espn.com`. The API is undocumented. North-star §5 records that it broke silently in 2019 and in April 2024.

| What | Finding |
|---|---|
| `GET /apis/v3/games/ffl/seasons/2026` | 200, `currentScoringPeriod.id = 8`. |
| League, unknown id | **404** `{"details":[{"type":"GENERAL_NOT_FOUND"}]}` |
| League, private | **401** `{"details":[{"type":"AUTH_LEAGUE_NOT_VISIBLE"}]}`. The two cases are distinct, so the UI can say "that league is private" rather than "not found". |
| Private-league credentials | The `espn_s2` and `SWID` cookies from the user's logged-in browser. *Unverified* but widely reported: `espn_s2` is a whole ESPN/Disney session cookie that lasts about a year, and signing out of ESPN invalidates it. |
| Settings shape (`leaguedefaults/3?view=mSettings`, 200, 8 KB) | `settings.rosterSettings.lineupSlotCounts` is a map from slot id to count. `settings.scoringSettings.scoringItems[]` is a list of `{statId, points, pointsOverrides{slotId: pts}}`. `settings.size`, `scheduleSettings` and `isPublic` are also present. |
| Lineup slot ids | From community client `espn-api`, `constant.py`; confirm against a captured league. 0 QB, 2 RB, 3 RB/WR, 4 WR, 5 WR/TE, 6 TE, 7 OP (superflex), 16 D/ST, 17 K, 20 bench, 21 IR, 23 FLEX (RB/WR/TE). The defaults are 1 QB, 2 RB, 2 WR, 1 TE, 1 FLEX, 1 D/ST, 1 K, 7 bench, 1 IR. |
| Stat ids we can map | 3 pass yds, 4 pass TD, 19 pass 2pt, 20 INT, 24 rush yds, 25 rush TD, 26 rush 2pt, 42 rec yds, 43 rec TD, 44 rec 2pt, 53 receptions, 72 fumbles lost, 101/102 kick/punt return TD. Defaults: 0.04 / 4 / 2 / −2 / 0.1 / 6 / 2 / 0.1 / 6 / 2 / 1 / −2 / 6. |
| Stat ids that need translation | 5–10, 27–32 and 47–52 are "every N yards" (step scoring, not linear). 17/18 are a 300–399 or 400+ passing game; 37/38 and 56/57 are the rushing and receiving equivalents (ranges, not thresholds). |
| Other views a sync needs | `mTeam`, `mRoster`, `mMatchup` (the schedule), `mStatus`. Over 50 results needs an `X-Fantasy-Filter` header (north-star §5). |
| Response headers | `cache-control: max-age=5`, served through CloudFront. There is no rate-limit header, so assume a low limit and be polite. |

### A3. Yahoo

How it was probed: `curl` plus Yahoo's developer docs.

| What | Finding |
|---|---|
| Unauthenticated | `GET fantasysports.yahooapis.com/fantasy/v2/game/nfl` → **401** `unable_to_determine_oauth_type`. |
| Auth | OAuth 2.0 authorization code. Authorize at `https://api.login.yahoo.com/oauth2/request_auth`; exchange the code at `https://api.login.yahoo.com/oauth2/get_token`, with an `Authorization: Basic` header built from the client id and secret. The access token lasts **3,600 s**. The refresh token is "long lifetime" and survives a password change, but the server can rotate it; when it does, drop the old one. Users revoke access in their Yahoo account settings; there is no revoke API. |
| App | The owner registers one Yahoo developer app with Fantasy Sports **read-only** access. The scope string is `fspt-r` (*unverified*; it is set at app creation). The client id and secret go in env. Whether Yahoo accepts a `localhost` redirect is *unverified*; `oob` exists, otherwise use an https tunnel for development. |
| Resources | `/league/{league_key}/settings` (roster positions, `stat_categories` + `stat_modifiers`, playoff settings), `/league/{key}/teams`, `/team/{key}/roster;week=N`, `/league/{key}/scoreboard;week=N`, `/league/{key}/players;status=A` (available players). Game key `nfl` resolves to the current season. |
| Stat ids | *Unverified until a settings response is captured* in S5: 4 pass yds, 5 pass TD, 6 INT, 9 rush yds, 10 rush TD, 11 receptions, 12 rec yds, 13 rec TD, 15 return TD, 16 two-point conversions (one stat covering pass, rush and rec), 18 fumbles lost. |

### A4. Other platforms (later, not planned here)
- **Fleaflicker** has a public JSON API.
- **MyFantasyLeague** has export endpoints and a login cookie.
- **NFL.com and CBS** have no stable public API worth building on.

All four fit the same `LeagueProvider` seam whenever they're wanted.

### A5. Player-ID matching: the biggest risk, and it was hidden

League rosters arrive keyed by platform player ids, so each one must resolve to a `players.id`.

Measured against the 496 QB/RB/WR/TE players with a 2026 game in our database:

| Source | Matched |
|---|---|
| `external_ids.espn` (stored today) | **496 / 496**, so ESPN rosters resolve directly |
| `external_ids.sleeper` (stored today by `SleeperCrosswalk`, keyed on gsis) | **105 / 496 (21%)** |
| Sleeper's file, by gsis **or** espn id | 133 / 496. Sleeper's own file carries gsis for only 960 of 3,047 active skill players, and espn for 1,402. |
| Sleeper's file, by **normalised name + position** (accents, punctuation and Jr/II/III removed) | **487 / 496 (98.2%)**, 0 ambiguous; the birth date agrees on 474 |
| The 9 misses | Fullbacks listed under another position (Ricard, Luepke), name variants ("Josh" vs "Joshua" Palmer, "Bam" Knight), and a few depth players |
| Yahoo | We store no Yahoo ids. Sleeper's file carries `yahoo_id` for 1,475 active players, so Yahoo ids can come through the Sleeper match. Name matching covers the rest. |

The DynastyProcess crosswalk (`db_playerids.csv`) carries every platform's id but is **GPL-3.0**. Do not ingest it.

**Conclusion.** A Sleeper import built on today's crosswalk would leave about 79% of a roster unmatched. S0 fixes this first.

### A6. Free agents without a free-agent endpoint
"Available in this league" = our QB/RB/WR/TE players with a current `roster_week` row **minus** every player on any roster in the league. V9 holds 3,025 distinct players for 2026 across all positions, through week 5 locally. This works the same on all three platforms, and costs no extra API calls.

Caveat: it does not tell waivers apart from free agency. Any roster player we cannot match is excluded rather than shown as available.

### A7. The trade model's inputs, measured from our own data

Method: half-PPR computed in a one-off query (nothing stored), 2021–2025 regular season, player-seasons of 8 or more games.

**Weekly spread around a player's own season average (σ against μ):**

| Pos | Player-seasons | σ at μ 5–10 | 10–15 | 15–20 | 20+ | Fit σ ≈ a + b·μ | r |
|---|---|---|---|---|---|---|---|
| QB | 181 | 6.84 | 7.13 | 7.43 | 8.06 | 5.48 + 0.120μ | 0.32 |
| RB | 492 | 5.76 | 7.09 | 8.77 | 10.51 | 2.00 + 0.426μ | 0.85 |
| WR | 795 | 5.46 | 7.26 | 8.76 | 7.02 | 1.89 + 0.455μ | 0.85 |
| TE | 407 | 5.11 | 7.54 | 7.55 | — | 1.18 + 0.556μ | 0.88 |

What this means:
- For RB, WR and TE, spread grows with the average. For QB it is nearly flat.
- These are spreads *around a hindsight average*. Projection error is larger: the backtest's overall PPR RMSE is about 6.9 (`docs/perf/projection-accuracy.md`). So **S2 refits (a, b) on the backtest's projection residuals**, not on this table. The table gives the expected shape and the order of magnitude.

**Correlation of weekly deviations** (players averaging 8+ points, same game):

| Pair | Same team | Opponents |
|---|---|---|
| QB–WR | **0.37 / 0.39** | 0.15 / 0.08 |
| QB–TE | **0.37 / 0.33** | 0.09 / 0.11 |
| QB–RB | 0.04 / 0.08 | 0.05 |
| WR–WR, RB–RB, RB–WR | −0.03 … 0.03 | −0.07 … 0.09 |
| QB–QB | — | 0.19 |

**Decision for the model:** keep same-team QB–WR and QB–TE covariance at r ≈ 0.37. Treat every other pair as independent in v1. Opposing-QB correlation is a v2 refinement.

### A8. Lineup optimisation
Every platform's slot types:

| Platform | Slots |
|---|---|
| Sleeper | QB, RB, WR, TE, FLEX, WRRB_FLEX, REC_FLEX, SUPER_FLEX, K, DEF, BN, plus reserve and taxi lists |
| ESPN | 0, 2, 3, 4, 5, 6, 7, 16, 17, 20, 21, 23 (A2) |
| Yahoo | QB, WR, RB, TE, W/R/T, W/R, W/T, Q/W/R/T, K, DEF, BN, IR |

**Greedy filling is not provably optimal here.** W/R and W/T overlap without one containing the other, so filling the most restrictive slot first can be beaten.

Rosters are at most about 25 players, with at most about 12 starting slots. So solve each week **exactly** as a maximum-weight assignment (Hungarian algorithm, about n³ with n ≈ 25, microseconds). Lineups are chosen on expected points before the games; outcomes are sampled after, never re-optimised.

### A9. Connection options and the recommendation

| Option | User effort | What we hold | Background sync | Verdict |
|---|---|---|---|---|
| Sleeper username | Type a username (about 10 s) | Nothing secret | Yes | **Ship first** |
| ESPN public league | Paste the league URL | Nothing secret | Yes | Ship |
| ESPN private: paste `espn_s2` + `SWID`, encrypted at rest | Copy two cookies from browser dev tools, with a guided panel (about 2 min, error-prone) | A whole-account session cookie | Yes | Ship as **opt-in**, with hardening (B-S4). This is what north-star row 7 already decided. |
| ESPN private: paste, use once, discard | The same paste on every sync | Nothing at rest | No | The fallback if the owner refuses storage |
| ESPN private: browser extension sends only league JSON | Install an extension | Nothing secret | Only when the user's browser syncs | Best security; an extension to maintain plus Chrome store review. **Later**, after usage justifies it. |
| Ask the commissioner to make the league public | One setting, for one person | Nothing | Yes | Mention it in the private-league panel |
| Yahoo OAuth | One "Connect Yahoo" button | A refresh token, encrypted | Yes | Ship third (needs the owner's Yahoo app) |

**Recommended order: Sleeper, then ESPN (public, then private), then Yahoo.**

North-star row 7 says ESPN first. This plan reverses that for three measured reasons:
- Sleeper needs no secrets, so the whole league domain can be built and proven before any credential handling exists.
- Its scoring keys are our keys.
- The ESPN path additionally needs encryption, a threat model and cookie UX.

The ESPN slice follows immediately. **Update north-star row 7's wording in S1's PR.**

---

## Part B: The build, in slices

Each slice is a branch, a PR and a review pass (`.claude/commands/review-pass.md`). The suite stays green, and each slice adds its own tests, including at least one mutation per protected line (CLAUDE.md: "A test that passes is not a test that can fail").

### S0. Prerequisites (small, ship first)
1. **Player matcher.**
   - Extend the crosswalk into a `PlayerMatcher` (`ingest/` or a new `leagues/` package) that resolves a platform player in this order:
     1. stored external id;
     2. gsis id;
     3. espn id;
     4. normalised name + position (+ birth date to break ties);
     5. normalised name + birth date, any position (catches fullbacks);
     6. a `player_id_overrides` table (new migration; columns platform, external id, player id, reason) for the rest.
   - Persist hits into `players.external_ids` (`sleeper`, `yahoo`) the way `SleeperCrosswalk` already does.
   - **Acceptance:** at least 98% of the 496 2026 skill players resolve to a Sleeper id, measured and pasted into the PR. A unit test covers each rule, including the "Joshua/Josh" and "Jr." normalisation and an ambiguous-name refusal (two "Josh Allen"s; CLAUDE.md trap: never key on a name alone).
   - Add the ESPN expression index on `players ((external_ids->>'espn'))` that north-star §8 says is owed. `players` is not the frozen table.
2. **Projection board sort** on the unrounded total (open item in `docs/CLAUDE-HANDOFF-2026-10-28.md`). Test: two players 0.004 apart order correctly.

### S1. League domain + Sleeper import (no secrets anywhere)

**Migration V11, `leagues`.** (V10 is the S0 matcher prerequisite.) Every table carries `user_id` for the ownership filter, cascading from `users`:
- `league_connections`
  - Columns: `id`, `user_id`, `platform` (CHECK sleeper/espn/yahoo), `external_user_id`, `credential_ciphertext BYTEA NULL`, `credential_nonce BYTEA NULL`, `key_version SMALLINT NULL`, `status`, `last_error`, `created_at`.
  - `UNIQUE (user_id, platform, external_user_id)`. All three must be `NOT NULL`, given the CLAUDE.md trap: NULLs defeat a UNIQUE.
- `leagues`
  - Columns: `id`, `user_id`, `connection_id`, `platform`, `external_league_id`, `season`, `name`, `num_teams`, `league_type`, `scoring_profile_id` (FK, the auto-created profile), `roster_slots JSONB`, `playoff_teams`, `playoff_week_start`, `trade_deadline`, `my_team_external_id`, `unmodeled_scoring JSONB`, `synced_at`.
  - `UNIQUE (user_id, platform, external_league_id, season)`.
  - **One copy per user**, never shared between members. That is the "never aggregated" invariant, and it makes cross-member leaks structurally impossible.
- `league_teams (league_id, external_team_id, name, manager_name, wins, losses, ties)`. Do **not** store the platform's points-for; compute ours on read (invariant: never store points).
- `league_rosters (league_id, external_team_id, external_player_id, player_id NULL, slot, status)`. This is the current snapshot, replaced in one transaction per sync, as V9 does. `player_id` NULL means unmatched and is shown to the user.
- `league_matchups (league_id, week, external_team_id, opponent_external_team_id)`.

**Backend package `com.fantasykai.leagues`:**
- `LeagueProvider` (interface, per north-star): `platform()`, `discover(credentials)` → leagues, `fetch(leagueRef, credentials)` → `LeagueSnapshot` (settings, scoring, slots, teams, rosters, schedule). Pure: it returns platform data and neither writes nor scores.
- `SleeperLeagueProvider`.
  - Fixed base URL from config; the user-supplied parts are validated: username `[A-Za-z0-9_]{1,40}`, league id `\d{1,20}`.
  - One sync = league + users + rosters + 1 state call, plus matchups for the remaining weeks on the first import only, because the schedule is fixed. About 4 calls afterwards.
  - Responses are capped at 2 MB, with a 10 s timeout.
- `LeagueHttp`, the one outbound client:
  - JDK `HttpClient`, as `NflverseClient` uses;
  - a **host allowlist** (api.sleeper.app, lm-api-reads.fantasy.espn.com, fantasysports.yahooapis.com, api.login.yahoo.com);
  - redirects to any other host are refused;
  - a body-size cap and a timeout;
  - **a shared Redis bucket for Sleeper at 600 calls a minute across all members** (A1: the limit is per IP);
  - it never logs request headers.
- **`SleeperScoringMapper` → `Ruleset` (version 1):**
  - Exact keys go to `base`.
  - `bonus_pass_yd_300/400`, `bonus_rush_yd_100/200` and `bonus_rec_yd_100/200` become `Bonus(stat, gte, points)`.
  - `bonus_rec_te/rb/wr` go to `position_overrides.{TE,RB,WR}.rec = rec + bonus`.
  - `kr_td`, `pr_td` and `st_td` become `ret_td` (approximate; note it).
  - Every other key with a non-zero value goes to `unmodeled_scoring` with its value: `pass_att`, `pass_cmp`, `rush_att`, first downs, `pass_sack`, any-fumble `fum`, kicker and DEF keys.
  - Round to 4 decimals. The result must pass `RulesetValidator` (±10 rates, at most 20 bonuses). On failure, import the league and **say why the profile wasn't created**. Never clamp; the validator's own rule is "rejections are loud".
  - Persist it as a `scoring_profiles` row named `"<league name> (Sleeper)"`, at most 64 characters, with a numeric suffix on a name clash (V5's unique index on user id + name).
- **`RosterSlots`:** a slot enum `QB, RB, WR, TE, FLEX_RWT, FLEX_WR, FLEX_WT, SUPERFLEX, K, DST, BENCH, IR`, with each slot's eligible positions, plus the per-platform tables from A2/A3/A8. It is **separate from `ScoringPosition`**, which stays a filter whitelist (north-star §8).
- **`LeagueService.importLeague` / `sync`:** fetch, match players (S0), map scoring, then replace the snapshot in one transaction. Ownership lives in the SQL (`WHERE user_id = ?`), and another member's league is a 404.
- **`LeagueController`:**
  - `POST /api/v1/leagues/sleeper/discover` with `{username, season}` returns the user's leagues and **stores nothing**.
  - `POST /api/v1/leagues` with `{platform, externalLeagueId, season, myTeamExternalId}`.
  - `GET /api/v1/leagues` and `GET /api/v1/leagues/{id}`.
  - `POST /api/v1/leagues/{id}/sync`, with a cooldown of 1 per 5 minutes per league (Redis), on top of `MemberRateLimitFilter`.
  - `DELETE /api/v1/leagues/{id}`.
  - Every request body gets `@Valid`.

**Frontend:**
- A new `app/(app)/teams` route.
- **Connect flow:** a "Sleeper username" field, then a list of that user's 2026 leagues, then "Which team is yours?" (pre-selected when the Sleeper `owner_id` equals the user's id), then Import.
- **League page:**
  - roster by slot;
  - the scoring that was imported, linking to its profile;
  - an "unmodelled scoring" notice, worded plainly, e.g. "This league also scores first downs, which Fantasy Kai doesn't model yet";
  - unmatched players listed as such;
  - standings, and "Last synced … · Sync now".
- Move **My Teams** from `COMING` to `PRIMARY_NAV` in `components/shell/nav.ts`, and update `DESIGN.md` Product pages.
- Follow DESIGN.md: Prime time tokens, lines not cards, no eyebrows, 1320px container.

**Acceptance:**
- A captured, **redacted** fixture of a real Sleeper league (the owner's, with consent): league ids, user ids and names replaced. Mapper, slot and matcher tests run against it.
- `QuerySafetyTests`-style injection tests on every new parameter.
- An ownership test: member B gets a 404 on member A's league, on GET, sync and DELETE.
- A test that the discover call writes nothing.
- A test that an unmodelled key is reported, never dropped.

### S2. Rest-of-season projections, lineup optimizer, team needs
- **Rest-of-season projection ("V1.1", owed before Phase 8 by the owner's 2026-10-08 decision).**
  - For each remaining week *w*, the player's expected line equals the provider's per-game line from `WeightedAverageProjectionProvider`, using history up to the latest completed week.
  - That line is multiplied by 0 on the team's **bye** (from `games`) and by 0 when the player is **OUT** (next week only) or **RESERVE / NOT_ON_ROSTER** (`AvailabilityRules`). Each week is scored under the league ruleset with `ScoringEngine`, then summed.
  - **Nothing stored**, because it is computed on read (invariants: project the stat line, never store points).
  - Every weekly number is an `ExplainedScore` (stat × rate parts).
- **`ProjectionUncertainty`: σ = a_pos + b_pos·μ.**
  - Fit (a, b) per position on the **backtest residuals** from the test-scoped harness `backend/src/test/java/com/fantasykai/projection/`, 2021–2025.
  - Record the fit and the data range in `docs/perf/projection-accuracy.md`, as config constants with their source.
  - A7 is the sanity range: RB b ≈ 0.43, QB nearly flat.
  - Same-team QB–WR and QB–TE correlation is 0.37 (A7), as a named constant with its measurement.
- **`LineupOptimizer`.**
  - Exact maximum-weight assignment (Hungarian) of players to slots, maximising the sum of μ for each week.
  - K and DST slots are filled with a **constant** equal to the league-average weekly points for that slot, flagged `unmodeled` (north-star §8 K/DST gap).
  - Tests:
    - a W/R + W/T case where greedy fails and the exact solve wins;
    - a superflex league;
    - a bye week that forces a bench player in;
    - an empty slot that scores 0;
    - mutate the solver to greedy and watch the first test fail.
- **Team needs (`GET /api/v1/leagues/{id}/needs`).** For each starting slot, show your optimiser's starter μ against the league median starter μ for that slot over the rest of the season. The answer is a surplus or a deficit in points per week, which is the "weak at QB/RB/WR" readout.
  - Show it on the league page as a quiet strip of one row per slot, e.g. "WR2: 11.2 a week, the league's median WR2 is 13.9, so −2.7".
  - Use one quality colour only, per DESIGN.md.

### S3. Trade evaluator (ΔEW for both sides) and playoff odds
- **`WinProbability`.**
  - For each week: team A's mean is the sum of its starters' μ. Its variance is the sum of σ² plus 2·r·σ_QB·σ_X for each same-team QB–pass-catcher pair among its starters.
  - P(A beats B) = Φ((μ_A − μ_B) / √(Var_A + Var_B)).
  - **Expected wins = the sum of P(win) over the remaining regular-season weeks**, against the **actual scheduled opponent** (from `league_matchups`). Weeks past the playoff start are excluded.
- **`TradeEvaluator.evaluate(league, myTeam, give[], theirTeam, get[])`.**
  1. Apply the swap to both rosters.
  2. Enforce roster size:
     - **receiving more than you give**: drop the lowest-ΔEW bench player(s), trying each candidate;
     - **receiving fewer**: add the best free agent from A6's pool, chosen by ΔEW, behind a "fill open spots from free agents" toggle that is on by default.

     This is how a 2-for-1 consolidation gets valued honestly.
  3. Re-optimise every week.
  4. Return expected wins before and after for **both** teams, ΔEW for both, a per-week table, and a **per-slot explanation**, e.g. "WR2: Nacua replaces Wilson, +4.6 a week in weeks 9–14".
  5. Report the players dropped or added in step 2.

  The response is an `ExplainedScore`-style tree, rounded once at the API boundary.
- **`SeasonSimulator`, for playoff odds.**
  - Monte Carlo with 10,000 seasons: weekly scores are sampled from each team's normal (mean, variance) above.
  - Standings come from the current record plus the simulated weeks. The tiebreak is points-for.
  - The top `playoff_teams` qualify.
  - Output: ΔP(playoffs) for both sides, alongside ΔEW.
  - Seeded and deterministic, so a test can pin it.
  - Budget: 12 teams × at most 14 weeks × 10k samples, under 100 ms on the VM. Measure it and report the number.
- **Headline:** show ΔEW as the main number and Δplayoff% beside it, every week. Do not switch headlines by week.
- **Endpoint:** `POST /api/v1/leagues/{id}/trades/evaluate` with a body validated so that the players are on the named rosters and the team ids belong to the league. It writes nothing.
- **Frontend `app/(app)/trades`:**
  - pick a league, then a trade partner;
  - two roster columns to select players from;
  - a result panel: both sides' ΔEW, playoff odds, the per-slot changes, and the drop/add notes.

  Move **Trades** from `COMING` to `PRIMARY_NAV`.
- **Calibration gate. This is a pre-registered requirement before the Trades nav item ships.**
  - Backtest the win-probability model on 2021–2025: build synthetic 12-team leagues by snake-drafting on each player's prior-season points per game, then project every week with the harness's `Timeline.asOf` leakage door.
  - **Pass bar, fixed before running:** a Brier score below a constant-0.5 baseline by at least 10%, and a reliability-curve slope within [0.8, 1.2] across 10 bins.
  - Write the result into `docs/perf/` in the house style.
  - If it fails, hold Trades until the model passes. The owner confirmed on 2026-10-29 that there is no public beta fallback.

### S4. ESPN
1. **Public leagues.**
   - `EspnLeagueProvider` uses views `mSettings`, `mTeam`, `mRoster`, `mMatchup`, `mStatus`. Players match on `external_ids.espn` (496/496).
   - Accept a full league URL or a bare id; parse the `leagueId` query parameter only.
   - Map 401 `AUTH_LEAGUE_NOT_VISIBLE` to "This league is private". Map 404 to "No league with that id this season".
   - **`EspnScoringMapper`:**
     - exact ids go to `base`;
     - "every N yards" becomes rate = points ÷ N, marked approximate;
     - range bonuses ("300–399" gives X, "400+" gives Y) become cumulative thresholds: `Bonus(300, X)` and `Bonus(400, Y−X)`, provided |Y−X| ≤ 20;
     - `pointsOverrides` for slots 0/2/4/6 go to `position_overrides`;
     - D/ST and K items go to `unmodeled_scoring`.
2. **Private leagues, opt-in "Keep this league synced".**
   - **`CredentialCipher`:**
     - AES-256-GCM via the JDK (`javax.crypto`; no new dependency), with a random 12-byte nonce per encryption;
     - **associated data = `"league_connection:" + id + ":user:" + userId`**, so a ciphertext copied to another row fails to decrypt;
     - the key comes from env `LEAGUE_CREDENTIAL_KEY` (base64, 32 bytes, in `.env.example` with no value), with `key_version` recorded for rotation;
     - the app refuses to start if encrypted rows exist and the key is missing.
   - Cookies are only ever accepted in a POST body. They are never placed in a URL, never logged (every type holding them overrides `toString`), and never returned by any endpoint.
   - **Disconnect** deletes the ciphertext immediately. Account deletion cascades.
   - The UI explains plainly what the two cookies are, that they are a sign-in to the user's ESPN account, how to revoke them (sign out of ESPN), and the alternative of asking the commissioner to make the league public.
   - Tests:
     - encrypt and decrypt a round trip;
     - a tampered ciphertext fails;
     - a ciphertext moved to another row fails (associated data);
     - a log-capture test proves the cookie value never appears in logs or exception messages;
     - an ownership test.
   - **Threat note for the owner, in the PR:** the key sits on the same VM as the database. That protects against a leaked backup or dump, not against a compromised VM. Say so plainly.

### S5. Yahoo
- **Owner action:** create the Yahoo developer app (read-only Fantasy) and put `YAHOO_CLIENT_ID` and `YAHOO_CLIENT_SECRET` in env.
- **Flow:**
  - `GET /api/v1/leagues/yahoo/authorize` returns the authorize URL with a **single-use `state`** (Redis, 10 minutes, bound to the member) and PKCE if Yahoo supports it (*verify*).
  - The callback checks `state`, exchanges the code, and stores the refresh token **encrypted** with S4's cipher.
  - Access tokens are held in memory only, and refreshed on demand. Rotated refresh tokens replace the old one in the same transaction.
- **Capture the owner's Yahoo league settings** (redacted) and build the `stat_id` mapping from the real response. A3's list is unverified. Players match through the Sleeper-provided `yahoo_id`, then by name.
- Revoked access (401 on refresh) marks the connection `needs_reconnect` and shows a "Reconnect Yahoo" button. It never retries in a loop.

### S6. League-aware rankings
- **A "Scored for" control on `/rankings`** when the member has leagues. Choosing a league:
  - sets the profile to that league's;
  - replaces `STARTERS` in `lib/board.ts` with the league's slot counts, which drive the quality band and the per-game tiers;
  - adds a "Rostered by" column;
  - adds an "Available" position filter (A6).
- **"Best available for your team":** rank free agents by ΔEW(add X, drop your weakest bench player). This is the Waivers engine (north-star §7), and it moves **Waivers** out of `COMING`.

### Later (not in this plan)
- Market-consensus board (Phase 9: FFC ADP, Sleeper roster percentage and trending, attribution).
- Trade finder.
- Keeper and dynasty (multi-year value, draft picks; Sleeper `settings.type` 1/2 already tells us the format).
- Opposing-QB correlation.
- An ESPN browser extension.
- Fleaflicker and MFL providers.

---

## Part C: How to work (Codex)

- **Branch per slice from `main`**, after the prerequisite integration lands. PR #61 was merged, but the later projection services and V7–V9 were absent from main; `feat/leagues-s0` integrates those backend changes while preserving main’s redesigned frontend. Write the PR description with what was measured, the tests and the mutations caught, as in the earlier phase rows in `CLAUDE.md`.
- **The gates, every slice:**
  - `./scripts/session-check.sh`;
  - `cd backend && ./mvnw -B verify` (JDK 25; Docker for Testcontainers);
  - `cd frontend && npm run lint && npm test && npm run build`;
  - browser QA with a throwaway local account, deleted afterwards.
- **Invariants to walk in every review pass:**
  - no stored fantasy points (league points-for included);
  - project the stat line;
  - round once at the boundary;
  - score each game, then sum;
  - league data fetched as the user and never shared across members;
  - no expert rankings or projections from any platform (never read Sleeper or ESPN projections or ranks);
  - every external source is optional, so a failed sync shows the last snapshot with its age and never a 500;
  - no SQL concatenation;
  - ownership in the query;
  - new Flyway migrations only;
  - `player_game_stats` untouched.
- **Secrets:**
  - nothing secret in fixtures; redact ids and names;
  - `git grep -iE "espn_s2|swid|refresh_token|client_secret" -- backend/src/test frontend/tests docs` must find no values;
  - never call a platform with another person's credentials.
- **Docs to update as slices land:** north-star §10 rows 7 and 8 (status, and Sleeper first), `docs/map.md`, `CLAUDE.md` (phase rows, "Where things are", the new traps found), `DESIGN.md` (My Teams and Trades pages), and `.env.example` (new keys, no values).

### Owner decisions confirmed 2026-10-29
1. **Sleeper ships before ESPN.** This reverses north-star row 7's order, for the reasons in A9.
2. **ESPN private leagues store the cookie encrypted, opt-in** (north-star's existing decision). The no-storage fallback stays available.
3. **One league copy per member**, never shared.
4. **ΔEW is the headline**, with Δplayoff% beside it.
5. **Trades ships only after passing the calibration gate.** If it fails, hold Trades; no public beta fallback.
6. **Free agents = our players not on a league roster**; waivers and free agency are not told apart in v1.
