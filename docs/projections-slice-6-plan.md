# Projections Slice 6 — plan

**Status: a proposal for the owner's approval. No UI code has been written, and none starts until this is
approved.** Written 2026-11-27 against `main` at `395cbe1`. It builds on the approved UX in
[`projections-v1-architecture.md`](projections-v1-architecture.md) §11 and on Slice 5's contract (§9 "as built");
where this plan and either differ, §3 below asks for a decision. Two decisions are already made and recorded in
[`HANDOFF.md`](HANDOFF.md): the first job run is **Tue 2026-12-08**, and the popover shows **both** error figures,
labelled PPR.

*Sizes are rough: **S** under half a day, **M** one to two days, **L** three or more.*

## 0. In short

- **Slice 6 is the first consumer of the Slice 5 API**, and nothing calls it yet. It is two surfaces: a "Next week"
  scope on the rankings board, and a "Week N projection" panel on the player page.
- **The spec is approved, but reading it against the built API and the frontend found four things it assumes that
  are not there** (§1): the API serves no rank and no usage counts (so the spec's "6.1 targets · 4.2 rec" line and
  a ranked board cannot be built from it), it serves one error figure where two were decided, the player page puts a
  phone's whole identity card above anything in the main column, and the board's columns, tiers and per-game quality
  band are all hard-wired to season totals.
- **So the first PR is a small backend contract change (6.0)**, before any UI. It is the same cost-asymmetry argument
  as Slice 5: while nothing consumes the shape it is free to change; after the screen exists every field is a
  migration of a screen.
- **Seven decisions** are needed (§3), each with a recommendation. The UI itself is three PRs after 6.0.
- **Deploy order matters** (D6.5): build now, ship after the job has run once, or members meet an empty "Next week".

## 1. What the spec asks, against what exists

The frontend facts below were read from the files (an exploration of `frontend/src` on 2026-11-27) and the
load-bearing ones spot-checked; "built" means in `main` today.

| Spec (arch §11) | What exists | Gap |
|---|---|---|
| A fourth scope option, **"Next week"**, that switches the data source and hides the season selector | The scope control is a native `<select aria-label="Window">` built from `SCOPES` in `components/rankings/FilterBar.tsx` (3 entries); the URL whitelist is `SCOPES` in `lib/boardParams.ts` (line 25); the Season `<select>` is rendered unconditionally | one more entry in both whitelists, and a prop to hide Season. The spec says "segmented"; it is not |
| Rows show "@ KC · Sun 1:00", a lock when kicked off, Q/D badges | `matchup(opponent, home)` ("vs NO" / "@ BUF") in `lib/player.ts:53`; `kickoff` and `locked` are served | **no time formatter** (no `Intl`/time-zone code in the frontend), **no lock icon** (`ui/Icon.tsx` has no lock), **no badge component** |
| One caption line: "Week 6 · Fantasy-Kai Baseline ⓘ · injury report as of Sat 6:00 AM ET" | the board already has a one-line metadata caption (`RankingsWorkspace.tsx` ~190-193, "Data through Week N") | extend it; the as-of time needs the time formatter |
| The scoring switch re-ranks live | `profileId` is in the board's query key, and `usePreviousRanks` (`RankingsBoard.tsx`) diffs ranks to show who moved | **the projections API serves no `rank`**, so movement and any rank chip have nothing to read |
| Player panel: the fixed raw line ("6.1 targets · 4.2 rec · 52 yd · 0.4 TD") | the row serves `parts` (stat, value, rate, points) for every stat with a value or a rate | **targets, pass attempts and carries are stored (`pass_att`, `rush_att`, `targets`) but not served** |
| A "0 PPR 9.8 · Half 11.9 · PPR 14.0" strip, plus the member's league if custom | `presets` is served (Standard, Half, Full PPR); the member's own number is `projection.points` | none in the API; a component |
| An expandable value × rate explanation | `parts` and `bonuses` are served | no component (the only disclosure is a native `<details>` in `MoversStrip`) |
| Availability badge, limited-history note | `availability.status` and `history` are served | no components |
| ⓘ popover: method, what it ignores, its typical miss | `source.summary` and `source.typicalError` are served | **one figure; two were decided**. Popover: only `ComingMenu.tsx` exists, hard-wired to nav items |
| A panel "at the top of the workspace" | the player page is a grid; below `lg` the sidebar (identity card, at least 34 rem while loading) comes first | on a phone the panel lands below the first screen |
| Typical errors on the board's rows | n/a: "the number is in the popover, not on rows" (§11) | none |

Also found, which the spec predates: **the "Daylight" skin no longer exists** (one night ground since 2026-10-19,
`DESIGN.md`), so the spec's skin remarks do not apply; **the landing page and `PRODUCT.md` both say projections are
not built** and must change when this ships (`app/(site)/page.tsx` ~157, `PRODUCT.md` ~54).

## 2. Scope

**In:** the contract change (6.0); the data layer and the pure logic with tests; the "Next week" scope and its table;
the player page's projection panel; the method popover; every state the API can answer, honestly; browser
verification at 390 and 1440; the docs and copy that stop being true.

**Out, on purpose:** a list of *who* the board left out (the API serves counts only; each player's own page still
shows his state); a week or season selector (V1 is next week only); trades; league-specific projections (My Teams
has its own path, `/leagues/{id}/needs`); live accuracy (Slice 7); **switching the job on** (the handoff's section 4);
any model change.

## 3. Decisions needed

| # | Question | Options | Recommendation |
|---|---|---|---|
| **D6.1** | What shape does the API give the two error figures? | **(a)** `typicalError: {metric, figures: [{kind, value, population, measured[]}, ...]}` with `kind` one of `IF_HE_PLAYS` (5.5) and `BEFORE_KICKOFF` (5.9); **(b)** keep `typicalError` and add a second sibling field | **(a).** The popover copy keys on `kind`, a third figure later is a list entry and not a new field, and the rename is free while no client reads it. Both figures are pinned to the committed reports, as 5.5 is: `BEFORE_KICKOFF` is the research's P0 (players who then sat count as zeros): 5.845 (2024, n 3,808), 5.887 (2025, n 3,808), 5.934 (2026 weeks 1-4, n 896); the headline 5.9 is within 0.2 of all three |
| **D6.2** | Does the API serve what the UI needs? | **(a)** add `rank` and `positionRank` to each row, `usage {passAttempts, carries, targets}`, and `rowsWeek` to the envelope; **(b)** the client derives them | **(a).** The board's search is server-side (`q`), so a client count restarts at 1 under any search or position filter, and the season board already takes its ranks from the API; `rank` is computed over the week's whole ranked set before the filters. `usage` is stored and never scored, so serving it breaks no invariant. `rowsWeek` is the rows' own week when the board is `STALE` (the envelope's `week` is the target's). **This reverses the "client counts" note in architecture §9 "as built"**, which I wrote on 2026-11-23 without a consumer to test it against |
| **D6.3** | Where does the panel sit on a phone? | **(a)** reorder the grid so the panel precedes the identity card below `lg` (a grid child of its own, `order-first` below `lg`, row 1 of the right column at `lg`); **(b)** leave it under the sidebar and add a jump link; **(c)** a one-line summary above the identity card and the full panel in the main column | **(a)**, and it is the **riskiest piece of the UI**: the aside is `sticky` with its own scroll from `lg`, and a `row-span` must not break that. Prototype and check it in a browser before building the rest of the panel; fall back to (c) if it fights the sticky aside |
| **D6.4** | Does the "Next week" table reuse the season board's columns, tiers and quality band? | **(a)** a new row component and column set, no tiers and no quality band in v1; **(b)** adapt the season board's types so tiers and bands apply | **(a).** `assignTiers` and `band()` are defined over `gamesPlayed`, `pointsPerGame` and a rank, and a "per-game quality band" is not a thing a projected total measures (DESIGN's rule: no metric the product does not compute). Tiers can follow once ranks exist, as their own PR if you want them |
| **D6.5** | When does the UI ship, relative to the job? | **(a)** build now, **deploy after the first production run** (Tue 12-08), so the first thing a member sees is a real board; **(b)** ship as soon as it is ready | **(a).** The UI handles every state anyway (`UNAVAILABLE`, `NO_UPCOMING_WEEK`, `STALE`), but "Next week: not generated yet" for everyone is a poor first impression. The landing page and `PRODUCT.md` change in the same release |
| **D6.6** | How is "who the board left out" shown? | **(a)** the counts in the caption (unavailable, no history, on a bye), no list; **(b)** build a list endpoint now | **(a).** It keeps "no unexplained disappearance" at the level of counts; a player who is Out is findable from the season board, and his page says so. A list is a Slice 7+ item |
| **D6.7** | Which time zone? | **(a)** Eastern, labelled once ("times ET"); **(b)** the member's local time | **(a).** The NFL's own convention and the spec's examples; the caption already says "ET". One formatter, testable with fixed instants |

## 4. The contract change (6.0)

Additive except the error figures, which have no consumer. The delta on the wire (everything else is as built):

```jsonc
// GET /api/v1/projections
{ "source": { "id": "weighted-average-v1", "label": "Fantasy-Kai Baseline", "summary": "...",
              "typicalError": { "metric": "PPR MAE",                                  // D6.1
                                "figures": [
                                  { "kind": "IF_HE_PLAYS",    "value": 5.5, "population": "...", "measured": [ ... ] },
                                  { "kind": "BEFORE_KICKOFF", "value": 5.9, "population": "...", "measured": [
                                      {"season":2024,"mae":5.845,"n":3808}, {"season":2025,"mae":5.887,"n":3808},
                                      {"season":2026,"weeks":"1-4","mae":5.934,"n":896} ] } ] } },
  "rowsWeek": 13,                                                                    // D6.2: null when no rows
  "page": { "content": [ { /* row as built, plus: */
              "rank": 1, "positionRank": 1,                                          // over the whole ranked week
              "usage": { "passAttempts": 0.0, "carries": 12.4, "targets": 3.1 } } ] } }
```

**Tests, each mutation-checked as in Slice 5:** `rank` ignores `q` and `position` and breaks ties as the board does;
`usage` equals the stored columns and is never scored; `rowsWeek` is the rows' week on a stale board and null with
none; both figures equal their rows in the committed reports (the existing `ProjectionSourceTests` pins); the wire
shape is re-pinned. **Files:** `ProjectionsService`, `ProjectionRow`, `ProjectionBoard`, `ProjectionSource`,
`WeightedAverageProjectionProvider`, their tests, architecture §9, this plan. Size **M**, backend only.

## 5. The UI

### 5.1 Data layer (6.1, **S–M**)
- `lib/types.ts`: the interfaces, by hand (the frontend has no runtime validation of responses and says so). To
  keep the types honest, one **captured real response** is committed as a test fixture and a test checks the keys the
  UI reads; it is refreshed when the contract changes.
- `lib/queries.ts`: `useProjectionBoard(profileId, position, q)`, an infinite query at the API's page cap of 200, with
  `q` sent to the server and part of the key; and `usePlayerProjection(id, profileId)`. Both gated on a resolved
  profile, as the existing hooks are.
- Two **pure, import-free** modules (the test runner is `node --test` with no path alias): `lib/kickoff.ts` (ET
  weekday and time, and the "as of" stamp, from fixed instants) and `lib/projections.ts` (state to caption copy,
  availability to badge, the raw-line text from `parts` and `usage`, the preset strip including the member's own
  ruleset, the popover's error copy from the API's figures).

### 5.2 The board (6.2, **M–L**)
- `FilterBar`: a `next_week` entry in `SCOPES`, a prop that hides Season, and **no new imports** in that file (the
  FilterBar test stubs `require` and throws "Unexpected component dependency" on any it does not know).
- `lib/boardParams.ts`: `next_week` in the whitelist; `BoardState`'s shape is unchanged (tests `deepEqual` whole
  objects), and `scope=DROP TABLE` must still fall back to `season`.
- A new table component for this scope (D6.4): rank, player (avatar, name, position chip with `positionRank`), team,
  opponent and kickoff with a lock when `locked`, a Questionable or Doubtful badge, projected points. Rows at most
  44 px, 16 or more visible at 1440 × 900, readable at 390 px with no sideways scroll.
- States, each honest: `READY`; `STALE` (a banner saying which week these rows are for, from `rowsWeek`);
  `UNAVAILABLE` ("not generated yet"); `NO_UPCOMING_WEEK` ("the season has no more weeks"); empty search; error.
- The caption: "Week N · Fantasy-Kai Baseline ⓘ · injury report as of Sat 6:00 AM ET" plus the `excluded` counts (D6.6).
  Movement on a scoring switch reuses `usePreviousRanks`. Row links go to the player with `profileId`; the board
  sequence and return keys are extended, not replaced.

### 5.3 The player panel (6.3, **M–L**)
"Week N projection" in the `Panel` pattern, with: the raw line; points under the selected ruleset (`type-figure`);
the preset strip (three, plus the member's own ruleset when it is not one of them), flat and not boxed; an expandable
explanation (`<details>`: each stat as value × rate = points, then the bonuses, reached or not); the availability
badge with its reason (an Out, reserve or not-on-roster player's page says "not ranked this week" and still shows
his line); the limited-history note; the ⓘ popover. Every non-`PROJECTED` state (`STALE`, `BYE`, `NO_HISTORY`,
`NOT_ROSTERED`, `UNAVAILABLE`, `NO_UPCOMING_WEEK`) has one honest line. Placement is D6.3. Only the four ranked
positions get it (the page already has a note for the others).

### 5.4 The popover and the copy
A new popover on the native `popover` attribute, modelled on `ComingMenu.tsx`. Content: the label "Fantasy-Kai
Baseline" (never "AI" or "model"), the API's summary, **what it ignores** (matchups, game script, injuries beyond the
official report), and the typical miss, from the API's figures and **labelled PPR**: "about 5.5 PPR points per
player-week if he plays, about 5.9 counting surprise absences", with the populations the API states.

### 5.5 Design rules that bind this (`DESIGN.md`, read for this plan)
One night ground and one token set; orange for the one primary action, blue for "you are here"; the amber `q-mid` for
availability notices; movement neutral (never green and red); no box inside a box; no opacity modifier on a text
token (a test scans for it); every new text pair measured on its surface and recorded in `globals.css`; a badge or
lock never colour alone (a visible word or an accessible name); motion only inside
`prefers-reduced-motion: no-preference`.

## 6. Files touched (expected)

| Area | Files |
|---|---|
| 6.0 backend | `api/ProjectionsService`, `ProjectionRow`, `ProjectionBoard`; `projections/ProjectionSource`, `WeightedAverageProjectionProvider`; `ProjectionApiTests`, `ProjectionSourceTests`, `ProjectionOrderingTests`; architecture §9 |
| Data layer | `lib/types.ts`, `lib/queries.ts`, new `lib/kickoff.ts`, `lib/projections.ts`, a fixture, `tests/` |
| Board | `components/rankings/FilterBar.tsx` (and `tests/scoring-select.test.ts` only if its stub must change), `lib/boardParams.ts`, `RankingsWorkspace.tsx`, a new projection table, `ui/Icon.tsx` (a lock), a badge |
| Player | `components/player/PlayerWorkspace.tsx` (the grid, D6.3), a new `ProjectionPanel`, the popover, `lib/playerView.ts` only if a nav entry is wanted |
| Release | `DESIGN.md` (the new parts), `PRODUCT.md`, `app/(site)/page.tsx`, `globals.css` (contrast records) |

**Not touched:** the job, the scoring engine, any migration, the auth chain, the CSP (the new GET goes to the API
origin the policy already allows; no new image origin; no inline script).

## 7. How it is validated

- **Gates:** `./mvnw -B verify` for 6.0; `npm run lint && npm test && npm run build` for the rest; `session-check.sh`.
- **Pure logic is mutation-checked**, as in Slice 5: break the line each assertion protects and the test must fail.
- **Real data in a browser at 390 and 1440 px.** There are no projection rows locally (the job is off), so run the job
  once against the local mirror with the flag on (`PROJECTIONS_JOB_ENABLED=true ./scripts/ingest-once.sh`; local
  rows only), which is also a first rehearsal of the switch-on. Screenshot every state: `READY`, `STALE`,
  `UNAVAILABLE`, `NO_UPCOMING_WEEK`, and the per-player states.
- **Other pages unchanged:** captures of the existing pages at four widths before and after, as the earlier
  frontend slices did.
- **Keyboard and screen-reader pass** on the scope control, the badges, the lock, the disclosure and the popover.

## 8. Order of work

| # | Work | Size | Depends on |
|---|---|---|---|
| 6.0 | The contract change (§4) | M | approval of D6.1, D6.2 |
| 6.1 | The data layer and pure logic, with tests | S–M | 6.0 |
| 6.2 | The "Next week" board | M–L | 6.1 |
| 6.3 | The player panel and popover (prototype D6.3 first) | M–L | 6.1 |
| 6.4 | Browser verification, `DESIGN.md`, `PRODUCT.md`, landing copy; **deploy after the first job run** | S | 6.2, 6.3, the job's first run |

Four to five PRs: 6.0 alone; 6.1 with 6.2 or on its own; 6.3; 6.4. Each ends with the review pass and a fresh-context
reviewer, as Slice 5's did. About **L–XL** in all.

## 9. The argument against this plan

- **It builds a UI against a job that has never run in production.** The first real week may show things the local
  run does not (more `STALE` or `UNAVAILABLE` than expected; the `excluded` hole for players whose game kicked off
  before the first run). The reply is D6.5: ship after the first run, and develop against a local run of the job.
  What would falsify the order: the first production week looks nothing like the local one.
- **"Next week" as a *scope* of the season board conflates a forecast with a record.** It shares a URL state, a
  filter bar, a return stack and a sequence key with a table whose every column means something else. A separate
  `/projections` page would be cleaner and the spec chose the scope for continuity and for the scoring switch. If the
  board's types and tests keep fighting the projection data during 6.2, stop and reconsider: that is the signal.
- **Two error figures in a popover is a lot of copy for a number the board does not show.** It is the owner's
  decision (2026-11-27); the plan builds it and keeps it to two sentences.
- **6.0 changes a contract that merged four days ago and whose docs say "as built".** No consumer exists, which is
  the whole argument, but the architecture doc's §9 must be corrected in the same PR so it does not say two things.

## 10. Not verified

- **How any of it looks.** There is no mock; compliance with `DESIGN.md` is by reading, and the contrast of the new
  text pairs is unmeasured.
- **That `Intl` with `America/New_York` formats identically in the Node test runner and in the browsers**; the plan
  tests it with fixed instants, nothing has run it.
- **The size of a real week's board** (an estimate: a few hundred ranked rows, so two pages of 200) and the board
  request's cost at production size (the handoff's R3 times it).
- **D6.3** (the grid reorder against the sticky aside) is a hypothesis until prototyped.
- **Whether server-side `q` feels right** against the season board's client-side filter over loaded rows.
- Every frontend fact above is from one exploration of the tree, with eight spot-checks; line numbers drift.
