# Projections Slice 6 — plan

**Status: approved by the owner on 2026-11-27, with D6.1 to D6.9 decided as recommended; 6.0 started the same day.**
Written 2026-11-27 against `main` at `395cbe1`, **revised the same day after a fresh-context review**
(see §10 for what it changed). The revision itself had no fresh review. It builds on the approved UX in
[`projections-v1-architecture.md`](projections-v1-architecture.md) §11 and on Slice 5's contract (§9 "as built");
where this plan and either differ, §3 asks for a decision.

**Two owner decisions of 2026-11-27 are inputs, not questions.** They were made in conversation and are recorded in
`HANDOFF.md` on PR #106 (not yet merged; on `main` that file still says 12-01 and leaves the popover open): the
first job run is **Tue 2026-12-08**, and the popover shows **both** error figures, labelled PPR. The 12-08 date is
itself conditional on the handoff's section 4 (R2, R3, R8) being done.

*Sizes are rough: **S** under half a day, **M** one to two days, **L** three or more.*

## 0. In short

- **Slice 6 is the first consumer of the Slice 5 API** (merged 2026-11-24; nothing calls it yet). It is two
  surfaces: a "Week N projection" panel on the player page, and a scope on the rankings board.
- **The approved spec assumes things that are not there** (§1): the API serves no rank and no usage counts, it serves
  one error figure where two were decided, the spec's label **"Next week" is wrong for what the API serves** (the
  target week is the week being played from Thursday to Tuesday), and the player page and the board's controls are
  built in ways the spec does not account for.
- **So the first PR is a small backend contract change (6.0)**, before any UI: free while nothing consumes the shape,
  a migration of a screen afterwards (Slice 5's argument again).
- **The value window is small.** If the job first runs 12-08, five regular-season weeks remain (14 to 18) before the
  feature sits dormant until September. So the plan builds the **player panel first** (the smaller surface, and the
  one that explains every state), the board second, and sets a checkpoint (D6.6).
- **Nine decisions** are needed (§3), each with a recommendation.

## 1. What the spec asks, against what exists

The frontend facts were read from the files (an exploration of `frontend/src` on 2026-11-27, with eight spot-checks,
then a second reviewer who checked every claim). "Built" means in `main` today.

| Spec (arch §11) | What exists | Gap |
|---|---|---|
| A fourth scope option, **"Next week"**, that switches the data source and hides the season selector | The scope control is a native `<select aria-label="Window">` built from `SCOPES` in `components/rankings/FilterBar.tsx` (3 entries, each with a `phrase` the caption uses); the URL whitelist is `SCOPES` in `lib/boardParams.ts:25`; the Season `<select>` is unconditional. On a phone the first control group is a 4-column grid with a **64 px third column** (`FilterBar.tsx:31`) | one more entry in both whitelists, a way to hide Season **without** breaking that grid, and a decision about the View and Filters controls, which mean nothing for projections (§5.3). Also the **label** (D6.3) |
| Rows show "@ KC · Sun 1:00", a lock when kicked off, Q/D badges | `matchup(opponent, home)` in `lib/player.ts:53`; `kickoff` and `locked` are served | **no time formatter** (no `Intl` or time-zone code in the frontend), **no lock icon** (`ui/Icon.tsx` has none), **no badge component** |
| One caption line: "Week 6 · Fantasy-Kai Baseline ⓘ · injury report as of Sat 6:00 AM ET" | a one-line caption exists (`RankingsWorkspace.tsx:190-193`, "Data through Week N") | extend it, but **`availabilityAsOf` is the time the last roster and injury pulls ran, not an injury-report time**, and can be null (§5.4) |
| The scoring switch re-ranks live, and movement shows who moved | `profileId` is in the board's query key; `usePreviousRanks` diffs ranks, but it is a **private function typed to the season board's `MemberBoardRow[]`** (`RankingsBoard.tsx:32`) | the projections API serves **no `rank`**; and the hook must be extracted and generalised, not "reused" |
| Player panel: the raw line "6.1 targets · 4.2 rec · 52 yd · 0.4 TD" | the row serves `parts` (stat, value, rate, points) | **targets, pass attempts and carries are stored (`pass_att`, `rush_att`, `targets`) and not served** |
| A "0 PPR · Half · PPR" strip, plus the member's league if custom | `presets` is served (three); the member's own number is `projection.points` | a component |
| An expandable value × rate explanation | `parts` and `bonuses` are served | no component (the only disclosure is a native `<details>` in `MoversStrip`). A projection's bonuses are all-or-nothing at the line's mean, and a decayed average leaves small trace values (§5.2) |
| Availability badge, limited-history note | `availability.status` and `history` are served | no components |
| ⓘ popover: method, what it ignores, its typical miss | `source.summary` and `typicalError` are served | **one figure; two were decided**. Only `ComingMenu.tsx` exists, hard-wired to nav items |
| A panel "at the top of the workspace" | the player page is a grid; below `lg` the sidebar (identity card, at least 34 rem while loading) comes first. **The page is also rendered as a modal `<dialog>` overlay** (`PlayerOverlay.tsx`), which the spec never mentions | on a phone the panel lands below the first screen; the overlay needs the same panel and a popover that works inside a modal dialog |

Also found, which the spec predates: **the "Daylight" skin no longer exists** (one night ground since 2026-10-19);
**the landing page and `PRODUCT.md` both say projections are not built** (`app/(site)/page.tsx:157`, `PRODUCT.md:54`)
while `PRODUCT.md` also says rest-of-season projections are live in My Teams (`RestOfSeason` scores the same
baseline), so the copy needs reconciling, not just deleting.

## 2. Scope

**In:** the contract change (6.0); the data layer and pure logic with tests; the player panel and the method popover;
the board scope and its table; every state the API can answer, honestly, by the calendar; browser verification at 390
and 1440, in the page **and** the overlay; the docs and copy that stop being true.

**Out, on purpose:** a list of *who* the board left out (the API serves counts only); a week or season selector (V1
is the current target week only); trades; league-specific projections (My Teams has its own path); live accuracy
(Slice 7); **switching the job on** (the handoff's section 4); any model change.

## 3. Decisions needed

| # | Question | Options | Recommendation |
|---|---|---|---|
| **D6.1** | What shape does the API give the two error figures, and what does the copy say? | **(a)** `typicalError: {metric, figures: [{kind, value, population, measured[]}, ...]}`; **(b)** a flat `typicalError` plus a sibling | **(a)**, `kind` being `IF_HE_PLAYS` (5.5) and `BEFORE_KICKOFF` (5.9); the rename is free while nothing reads it. The second is the research's P0, pinned like the first: 5.845 (2024, n 3,808) and 5.887 (2025, n 3,808) in their reports' P0 rows, and **5.934** (2026 weeks 1-4, n 896) in `v2-prospective-2026.md`'s *availability diagnostic* table, a different shape, so it needs its own pin. **Copy:** not "surprise absences". P0 counts every player who then sat, **including those the report lists as Out, whom the board already leaves off**, so 5.9 is an upper bound for what a member sees: say "counting every player who then sat as a zero" |
| **D6.2** | What does the API serve for the UI, and where does search happen? | **(a)** add `rank` and `positionRank`, `usage`, `rowsWeek` to the API, and filter by search on the client; **(b)** the client derives ranks and sends `q` to the server | **(a).** `rank` is computed over the week's whole ranked set before the position filter, so it stays right under a position filter and across the 200-row pages, and the movement diff needs it; both are **null for a player the board does not rank** (Out, reserve), on the player route too. `usage` is `{passAtt, rushAtt, targets}`, matching the frontend's existing `Usage` type (minus completions, which are not stored). `rowsWeek` is the rows' own week on a stale board. **Search stays client-side over the loaded rows, as the season board's is**, not a server `q` per debounced keystroke (each runs the whole request: all lines scored, roster and availability read; untimed at production size). The API's `q` stays, unused by the UI. This reverses the "client counts" note in architecture §9 "as built" |
| **D6.3** | What is the scope called? | **(a)** "This week"; **(b)** "Next week" (the spec); **(c)** the week number, "Week 14" | **(a).** The target week is the first week with an unfinished game (`TargetWeekResolver`), so it rolls over on the Tuesday pull: from Thursday's kickoff to Tuesday morning it is the week *being played*, with kicked-off rows locked. "Next week" is false five days of seven (a Sunday afternoon would show "Next week" with half its rows locked). The caption carries the number. Architecture §11's wording is corrected in 6.0 |
| **D6.4** | Where does the player panel sit on a phone, and what does it show when the page is on another season? | **(a)** reorder the grid so it precedes the identity card below `lg`; **(b)** leave it under the sidebar with a jump link; **(c)** a one-line summary above the identity card (name, week, points, badge) that links to the full panel, which is first in the main column at `lg` and below | **(c).** (a) would put a projected number above the player's name and the ruleset switch, and the aside is `sticky` with its own scroll from `lg`. The panel is about the *current target week* whatever the page's `season` says, so it is labelled with its week and is not hidden when the page is on 2024. **Whatever is chosen is prototyped first in both the page and the overlay** (a `popover` inside a modal `<dialog>`, and Escape, whose `onCancel` calls `router.back()`) |
| **D6.5** | Does the new table reuse the season board's tiers and quality band? | **(a)** a new row component and columns, no tiers or band in v1; **(b)** adapt them | **(a), on scope grounds.** `band(rank, position)` needs only a rank and a position, and `assignTiers` only a contiguous list ordered by a value, so (b) would work once ranks exist. But a "per-game quality band" is not what a projected total measures (the rule is *no metric the product does not compute*), and tiers are a separate feature. They can be their own PR |
| **D6.6** | What do we ship this season, and when do we stop? | **(a)** the player panel first, the board second, **deployed after the first production job run**, with a checkpoint on 2026-12-15: if the job is not running by then, hold both for the 2027 preseason; **(b)** everything, as soon as ready | **(a).** Five weeks remain after 12-08 and most fantasy playoffs are weeks 15 to 17 (general knowledge, not measured here). The panel is half the surface and shows the per-player states honestly; a board that opens on "not generated yet" is a poor first impression. The 12-08 date needs the handoff's R2, R3 and R8 first |
| **D6.7** | How is "who the board left out" shown, and what about the hole? | **(a)** counts in the caption, plus a fourth count `notProjected` (a rostered player with history and no line) added in 6.0; **(b)** counts as built | **(a).** The architecture's promise is "no unexplained disappearance", and as built it is false for exactly that player (the handoff's open item 3). It is cheap now: it is the `unprojected` list minus the other two counts. **The caption must never print 0 for a null** (`noHistory` and `bye` are null unless `READY`) |
| **D6.8** | What does the raw line and the explanation show? | **(a)** the stats the position records, each hidden when it rounds to nothing; the explanation says a bonus is "reached" only as the projected line's own average; **(b)** everything in `parts` | **(a).** A decayed average never reaches zero, so a receiver who once carried the ball shows a trivial rushing value on every line; a bonus on a projection is all-or-nothing at the mean, not a probability. The rule (position to stats; a hide threshold) lives in one pure function with tests |
| **D6.9** | Does the frontend wait for the API? | **(a)** 6.0 deploys to production **before** any UI that reads it; the board hook keeps the existing 404-fallback pattern; **(b)** ship together | **(a).** The frontend has shipped before its backend here before (PR #34, the account gate, merged ahead of the backend on 2026-09-29: from my own session notes, not re-derived). 6.0 is additive except `typicalError`, which has no reader |

Not a decision: **times are Eastern**, labelled once. Architecture §11's examples already say "6:00 AM ET" and "Sun 1:00".

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
  "rowsWeek": 13,                                                       // D6.2: the rows' own week; null with no rows
  "excluded": { "unavailable": 31, "noHistory": 12, "bye": 74, "notProjected": 3 },   // D6.7: same null rule as the other two
  "page": { "content": [ { /* row as built, plus: */
              "rank": 1, "positionRank": 1,                             // D6.2: null for a player the board does not rank
              "usage": { "passAtt": 0.0, "rushAtt": 12.4, "targets": 3.1 } } ] } }
// GET /api/v1/players/{id}/projection: the same row fields, rank and positionRank computed over the whole week
```

**Tests, each mutation-checked as in Slice 5:** `rank` ignores `q` and `position`, breaks ties as the board does, and
is null for an unranked player on both routes; `usage` equals the stored columns and is never scored; `rowsWeek` is
the rows' week on a stale board and null with none; `notProjected` agrees with the pages of everyone rostered (the
Slice 5 test that compares the board's counts with every rostered player's page); both figures equal their rows in
the committed reports (the 2026 P0 gets a new pin); the wire shape is re-pinned.

**Files:** `ProjectionsService` (rank needs a second pass over the ranked rows, because `ProjectionRow` is an immutable
record built in `scored()`; both constructors in `ProjectionOrderingTests` change), `ProjectionRow`,
`ProjectionBoard`, `ProjectionSource`, `WeightedAverageProjectionProvider`, `ProjectionApiTests`,
`ProjectionSourceTests`, `ProjectionOrderingTests`; the docs that describe what changes: architecture §9, §11 (the
"Next week" wording and the one figure) and §15 (the open population item, now decided), `docs/map.md`, the Slice 5
plan's `typicalError` text, and `HANDOFF.md` items 1, 3, 5 and 6; and `session-check.sh`'s counts. Size **M**, backend only.

## 5. The UI

### 5.1 Data layer (6.1, **S–M**)
- `lib/types.ts`: the interfaces, by hand (the frontend validates no responses and says so). To keep them honest, one
  **captured real response** is committed as a fixture and a test checks the keys the UI reads.
- `lib/queries.ts`: `useProjectionBoard(profileId, position)`, an infinite query at the API's page cap of 200 (a real
  week is a few hundred ranked rows: an estimate), loading every page so search can be client-side (D6.2); and
  `usePlayerProjection(id, profileId)`. Both are gated on a resolved profile. The board's `Scope` type is **not**
  widened: `useRankingsBoard` sends the scope to `/rankings/board`, which rejects a value it does not know with a 400,
  so the new scope is a separate type and the season board's hook is disabled while it is selected.
- **Pure, import-free** modules (the test runner is `node --test` with no path alias):
  `lib/kickoff.ts` (Eastern weekday and time, and the "checked" stamp, from fixed instants) and `lib/projections.ts`
  (state to copy **by calendar** (§5.4), availability to badge, the raw line and the hide rule (D6.8), the preset strip
  including the member's own ruleset, the popover's copy from the API's figures).
- `usePreviousRanks` extracted from `RankingsBoard.tsx` and generalised to anything with a player id and a rank.

### 5.2 The player panel (6.2, **M–L**)
"Week N projection" in the `Panel` pattern: the raw line (D6.8); points under the selected ruleset (`type-figure`);
the preset strip (three, plus the member's own ruleset when it is not one of them), flat and not boxed; an
expandable explanation (`<details>`: each stat as value × rate = points, then the bonuses); the availability badge
with its reason (an Out, reserve or not-on-roster player's page says "not ranked this week" and **still shows his
line**); the limited-history note; the ⓘ popover. Placement and the overlay are D6.4. Only QB, RB, WR and TE get it.
Every non-`PROJECTED` state has one honest line, by calendar (§5.4), and in `NO_UPCOMING_WEEK` and the offseason the
panel is **not rendered** at all.

### 5.3 The board (6.3, **L**)
- `FilterBar`: a mode for this scope that **hides Season, View and the Filters panel** (none means anything for a
  projection) and keeps a four-column phone grid that still works with one control fewer; **no new imports** in that
  file (its test stubs `require` and throws "Unexpected component dependency" on any it does not know).
  `SCOPES` also carries the caption's `phrase`.
- `lib/boardParams.ts`: the new scope in the whitelist; `BoardState`'s shape is unchanged (tests `deepEqual` whole
  objects), and `scope=DROP TABLE` must still fall back to `season`.
- A new table (D6.5): rank, player (avatar, name, position chip with `positionRank`), team, opponent and kickoff
  with a lock when `locked`, a Questionable or Doubtful badge, projected points. Rows at most 44 px, 16 or more
  visible at 1440 × 900, no sideways scroll from 320.
- States by calendar (§5.4), and the **board sequence and return mechanics, specified now and not "extended"**: the
  player page offers previous and next only when its search equals `?season=…&profileId=…` exactly, so a link without
  `season` silently loses them; `BackToBoard` and `step()` rebuild the board URL without `scope`, so a shared-link
  back path returns the season board; and the step buttons read "ranked N", which for this board is "projected N".
  6.3's first commit defines what the saved `search` and `rank` mean here and carries `scope` through both.
- The caption and the `excluded` counts (D6.7); movement on a scoring switch via the extracted hook.

### 5.4 States, copy and the popover
- **By calendar.** The resolver returns no week "after week 18, before the schedule is published, or out of season"
  (its own javadoc): a week is held open by any game with no stat rows until 48 hours after its kickoff, so the board
  says `NO_UPCOMING_WEEK` from then on. The daily pull **does nothing from March to August**
  (`IngestScheduler`), so I expect `NO_UPCOMING_WEEK` all through the offseason, and `UNAVAILABLE` only if a new
  season's schedule were loaded before the job's first run (nothing in the tree says it would be; **not verified**).
  Either way **the offseason copy must say projections return with the season**, not "not generated yet" (the member
  would wait for something months away). In season, `UNAVAILABLE` says "not generated yet" and `STALE` says which week
  the rows are for (`rowsWeek`). **Week 1** adds a line: its history is last season's. The March boundary is the one
  `lib/season.ts` already mirrors (`getMonth() >= 2`), and the sequence is the one the backend uses (`seasonFor`).
- **The caption** reads "Week 14 · Fantasy-Kai Baseline ⓘ · availability checked Tue 6:00 AM ET": the time is
  `availabilityAsOf` (the older of the last successful roster pull and injury pull), says nothing about which week
  the injury report covers (it usually appears on Wednesday), and is **null** when either feed has never succeeded:
  then it says "availability not checked yet", never a blank.
- **The popover** is new, on the native `popover` attribute, modelled on `ComingMenu.tsx`: the label "Fantasy-Kai
  Baseline" (never "AI" or "model"), the API's summary, **what it ignores** (matchups, game script, injuries beyond
  the official report), and the two figures from the API with their populations, labelled PPR and worded per D6.1.

### 5.5 Design rules that bind this (`DESIGN.md`)
One night ground and one token set; orange for the one primary action, blue for "you are here"; the amber `q-mid` for
availability notices; movement neutral (never green and red); no box inside a box; no opacity modifier on a text
token (a test scans for it); every new text pair measured on its surface and recorded in `globals.css`; a badge or
lock never colour alone; motion only inside `prefers-reduced-motion: no-preference`.

## 6. Files touched (expected)

| Area | Files |
|---|---|
| 6.0 backend and docs | as §4 |
| Data layer | `lib/types.ts`, `lib/queries.ts`, new `lib/kickoff.ts`, `lib/projections.ts`, a fixture, `tests/`; `RankingsBoard.tsx` (extract `usePreviousRanks`) |
| Player panel | `components/player/PlayerWorkspace.tsx` (the summary strip and the panel's place), a new `ProjectionPanel`, the popover, an availability badge, `ui/Icon.tsx` (a lock) |
| Board | `components/rankings/FilterBar.tsx` (and `tests/scoring-select.test.ts` only if its stub must change), `lib/boardParams.ts`, `RankingsWorkspace.tsx`, `lib/boardSequence.ts` and `lib/boardReturn.ts`, a new projection table |
| Release | `DESIGN.md` (the new parts), `PRODUCT.md`, `app/(site)/page.tsx`, `globals.css` (contrast records) |

**Not touched:** the job, the scoring engine, any migration, the auth chain, the CSP (the new GET goes to the API
origin the policy already allows; no new image origin; no inline script).

## 7. How it is validated

- **Gates:** `./mvnw -B verify` for 6.0; `npm run lint && npm test && npm run build` for the rest; `session-check.sh`.
- **Pure logic is mutation-checked**, as in Slice 5: break the line each assertion protects and the test must fail.
- **Real data in a browser at 390 and 1440 px, in the player page and in the overlay.** There are no projection rows
  locally (the job is off), so run the job once against the local mirror with the flag on
  (`PROJECTIONS_JOB_ENABLED=true ./scripts/ingest-once.sh`; local rows only), which also rehearses the switch-on.
  Screenshot every state, and for the offseason states change the clock, not the data.
- **Other pages unchanged:** captures of the existing pages at four widths before and after.
- **Keyboard and screen-reader pass** on the scope control, the badges, the lock, the disclosure and the popover,
  including Escape inside the overlay.

## 8. Order of work

| # | Work | Size | Depends on |
|---|---|---|---|
| 6.0 | The contract change (§4), **deployed first** (D6.9) | M | approval of D6.1, D6.2, D6.7 |
| 6.1 | The data layer and pure logic, with tests | S–M | 6.0 |
| 6.2 | The player panel and popover (prototype D6.4 first, in the page and the overlay) | M–L | 6.1 |
| 6.3 | The "This week" board | L | 6.1, and the extracted hook |
| 6.4 | Browser verification, `DESIGN.md`, `PRODUCT.md`, landing copy; **deploy after the first job run** | S | 6.2 (and 6.3 if it is ready), the job's first run |

Four to five PRs, each ending with the review pass and a fresh-context reviewer, as Slice 5's did. About **L–XL** in
all. **Checkpoint 2026-12-15 (D6.6):** if the job is not running by then, hold the UI for the preseason.

## 9. The argument against this plan

The strongest case, which the first draft under-weighted:

- **The value window is small and the prerequisites are all still owed.** The job's first run needs the production
  backfill, a rehearsal and your sign-off (the handoff's R2, R3, R8). If it first runs 12-08, five regular-season
  weeks remain, of which the UI is ready for perhaps three; fantasy playoffs are mostly decided by week 17. Then the
  feature goes dormant, and for about eight months of the year its honest state is `NO_UPCOMING_WEEK` or
  `UNAVAILABLE`. Slice 7's live-accuracy record needs the job on, not this UI. **A defensible alternative is to build
  nothing in the UI this season** and spend the weeks on getting the job on and measuring it. The plan's answer is the
  panel first and the 12-15 checkpoint; if you would rather wait for the preseason, 6.0 alone is still worth doing now.
- **"This week" as a *scope* of the season board conflates a forecast with a record.** It shares a URL state, a
  filter bar, a return stack and a sequence key with a table whose every column means something else, and §5.3
  already lists seven places where the board's machinery does not fit. A separate `/projections` page would be
  cleaner; the spec chose the scope for continuity and for the scoring switch. **If 6.3's first commit finds the
  board's types and tests fighting the projection data, stop and move to a separate page.**
- **The headline figures describe a research population, not the board.** 5.5 is the top 224 by opportunity who
  played; the board ranks everyone with history (about 4.5). Showing 5.5 and 5.9 labelled is honest, but neither is
  what Slice 7 will measure over the board's own rows, and the popover and that report will need reconciling.
- **6.0 changes a contract that merged on 11-24 and whose docs say "as built".** No consumer exists, which is the
  whole argument, but §9 of the architecture doc must be corrected in the same PR so it does not say two things.

What would falsify the plan: the first production board looks nothing like the local rehearsal; the job is not on by
mid-December; or 6.3 keeps fighting the season board's machinery.

## 10. What the review changed, and what is not verified

A fresh-context reviewer checked every claim in the first draft against the tree. No file path was wrong. It changed:
the scope's name (D6.3: "Next week" is false five days of seven); D6.2's reasoning (the season board's search is
client-side, so a server `q` was my choice and an untimed one) and its field names; D6.4 (the phone placement and the
overlay); D6.5's premise (`band()` does not need `gamesPlayed`); the order of work (panel before board) and the
value-window argument; the offseason states; the caption's wording; the sequence and return mechanics; the `excluded`
hole (D6.7); and the copy for the second figure. It also caught that the first draft cited decisions as "recorded in
`HANDOFF.md`" when they were only on an unmerged branch.

**Still not verified:**
- **How any of it looks.** There is no mock; compliance with `DESIGN.md` is by reading, and the contrast of the new
  text pairs is unmeasured.
- **That `Intl` with `America/New_York` formats identically in the Node test runner and in the browsers.**
- **The size and request cost of a real week's board** (the handoff's R3 times it) and whether loading all pages up
  front is the right call.
- **D6.4** (the placement, and a `popover` inside a modal `<dialog>`) is a hypothesis until prototyped.
- **Whether production runs Slice 5 at all** (the last recorded deploy is 2026-11-17).
- Every frontend fact is from one exploration plus two rounds of checks; line numbers drift.
