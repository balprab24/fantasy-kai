# Product

<!-- impeccable:product-schema 1 -->

A design-facing digest of product truth, written for design work (Impeccable reads it before any
visual change). It owns no facts: scope and decisions live in
[`docs/north-star.md`](docs/north-star.md), engineering rationale in
[`docs/fantasy-platform-handoff.md`](docs/fantasy-platform-handoff.md). Where this file and those
disagree, they win and this file is what gets fixed.

## Platform

web

## Users

People who play fantasy football and want a ranking that is right for *their* league: managers
checking a board and a player's week-by-week record during the NFL season, most often in the
evening and on the weekend, on a laptop or a phone. An account is an email and a password; the whole
product sits behind one (north-star §2).

## Product Purpose

A free fantasy football tool that tells you why, not just what, and computes it for your league
rather than a generic 12-team PPR (north-star §1). Today that means: every player's box scores since
2020, scored against any ruleset (presets or your own), refreshed every morning of the season, shown
as a tiered rankings board and a per-player workspace. Success is a manager trusting the board
enough to act on it, because every number can be traced to the stat lines that produced it.

## Positioning

- **Store raw stat lines, never fantasy points; compute points on demand against a ruleset.** One
  season has several correct answers and the ruleset decides which, so switching scoring re-orders
  the board rather than filtering it (CLAUDE.md, "The one idea").
- **Explained, not asserted.** Every ranked number carries its parts (north-star §1 claim 2).
- **No expert rankings, ever.** Consensus means the market: real drafts, real rosters, real lines
  (north-star §4).
- **Free with no tier.** No paywall, no ads, no sportsbook links, no affiliate (north-star §1).

## Operating Context

- Data: nflverse's open data (CC BY 4.0), pulled every morning at 06:00 ET; attribution is required
  wherever the data appears, which is why every page carries the footer.
- Scoring presets are shown as 0 PPR, Half PPR, PPR and TE Premium; members can build their own.
- A board is tiered by natural breaks over its top 60; positional ranks and a per-game quality band
  (against a 12-team league's starters) are derived in the browser from the rows the API returns.
- Fantasy leagues score the regular season only; the game log still lists playoff games.

## Capabilities and Constraints

- Live: rankings board, player workspace (weekly chart, game log, career), scoring ruleset builder,
  landing page with email-first sign-up.
- Not built, and never presented as working: projections, league import (ESPN and Sleeper), start/sit
  and waivers, trades, market consensus, draft tools, a player index, the iPhone app.
- Every page renders per request under a nonce-based CSP: images only from the site and
  `a.espncdn.com`, fonts only self-hosted, no third-party script.
- v1 scores and ranks QB, RB, WR and TE only; every position is stored.

## Brand Commitments

- Name `fantasy-kai`; the call to action is "Join the Kai".
- The logo mark is two slanted strokes, ki orange and energy blue; the wordmark's ı carries an orb
  for its dot. Orange means the brand and the one thing to do; blue means "you are here" and
  anything interactive. The two never trade jobs.
- Position colours are semantic data colours (QB purple, RB red, WR blue/cyan, TE pink/magenta),
  never decoration.
- Two modes (owner decision 2026-09-30): Daylight on the public pages (the landing, sign-in,
  register) -- a cool chalk ground and navy ink -- and Prime time in the product: near-black, with
  deep blue-black where separation is needed. Wherever a public page shows the product, the product
  keeps its own dark skin; signing in is the moment the whole screen turns dark.
- Identity, in this order: premium fantasy analytics, sports broadcast / data terminal, subtle anime
  energy. The anime influence stays subtle enough that someone unfamiliar with anime still reads the
  product as a sports product; no copyrighted characters.
- Owner decisions of 2026-09-29: the landing hero stays free of any real player's likeness until a
  photo is licensed for promotion and the player consents (`components/landing/heroImage.ts`);
  navigation is a top bar with one "Coming" entry for everything unbuilt; the hero headline is
  "Ranked by your rules."

## Evidence on Hand

- Real captured API rows for the landing page, 2025 regular season:
  `frontend/src/components/landing/previewData.ts` (request lines documented there).
- ESPN headshots (600×436 transparent cut-outs) and team logos, hotlinked and never stored, inside
  the member area only. Whether that use is acceptable is an open owner call (docs/map.md §5).
- No licensed player photography, no testimonials, no customer logos, no usage numbers. None may be
  invented, and no metric the product does not compute may be shown.

## Product Principles

1. The ruleset is the argument: any screen that shows a number should make it obvious which rules
   produced it, and switching rules should visibly move things.
2. Show the parts. A number with its derivation beats a bigger number.
3. Never claim what the product cannot do yet; unbuilt things are labelled as coming, not linked.
4. Dense where people work, expressive where people decide: the board is a tool, the landing page
   is a pitch.

## Accessibility & Inclusion

WCAG 2.1 AA as the floor: text contrast measured (not estimated) against every surface it sits on,
3:1 for control boundaries, colour never the only cue (position badges carry letters, movement
carries arrows and numbers), full keyboard use including the chart, and `prefers-reduced-motion`
honoured.
