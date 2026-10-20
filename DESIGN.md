---
name: fantasy-kai
description: Fantasy football rankings scored by your league's own rules -- one broadcast package on one night ground, for the public pages and the product alike.
colors:
  canvas: "#060a12"
  surface: "#0b1120"
  well: "#121a2c"
  lift: "#1b2640"
  ink: "#eef2fa"
  mute: "#9aa6bd"
  faint: "#7d8aa3"
  line: "rgb(148 170 210 / 0.12)"
  line-strong: "#64718c"
  ki: "#ff8a3d"
  on-ki: "#140a02"
  ki-text: "#ff8a3d"
  energy: "#3d8bff"
  energy-text: "#8ab8ff"
  danger: "#f28b8b"
  pos-qb: "#c3a6ff"
  pos-rb: "#ff6b6b"
  pos-wr: "#5cc8ff"
  pos-te: "#ff8fd4"
  q-good: "#6fcf97"
  q-mid: "#d9c162"
  chart-starter: "#3fa877"
  chart-rest: "#5d667c"
  heat-green: "#36b37e"
  heat-yellow: "#e3c04b"
  heat-orange: "#ee8a3c"
  heat-red: "#e5574f"
  track: "rgb(255 255 255 / 0.06)"
  highlight: "rgb(255 255 255 / 0.07)"
  veil: "rgb(6 10 18 / 0.82)"
  scrim: "rgb(2 4 9 / 0.72)"
typography:
  display:
    fontFamily: "Sofia Sans Extra Condensed, ui-sans-serif, system-ui, sans-serif"
    fontSize: "clamp(3.75rem, 10vw, 7.25rem)"
    fontWeight: 850
    lineHeight: 0.86
    letterSpacing: "-0.004em"
  headline:
    fontFamily: "Sofia Sans Extra Condensed, ui-sans-serif, system-ui, sans-serif"
    fontSize: "clamp(2.75rem, 6vw, 3.75rem)"
    fontWeight: 850
    lineHeight: 0.86
  title:
    fontFamily: "Sofia Sans Extra Condensed, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 800
    lineHeight: 1
  body:
    fontFamily: "Sofia Sans, ui-sans-serif, system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: 1.55
  label:
    fontFamily: "Sofia Sans Semi Condensed, ui-sans-serif, system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: 1.3
  figure:
    fontFamily: "Sofia Sans Semi Condensed, ui-sans-serif, system-ui, sans-serif"
    fontSize: "32px"
    fontWeight: 750
    fontStyle: "italic"
    lineHeight: 1
  rank:
    fontFamily: "Sofia Sans Semi Condensed, ui-sans-serif, system-ui, sans-serif"
    fontSize: "17px"
    fontWeight: 750
    fontStyle: "italic"
    fontFeature: "tnum"
rounded:
  nested: "2px"
  control: "8px"
  panel: "16px"
  plate: "24px"
  pill: "9999px"
spacing:
  gutter-sm: "16px"
  gutter-md: "24px"
  gutter-lg: "32px"
  section: "112px"
components:
  button-primary:
    backgroundColor: "{colors.ki}"
    textColor: "{colors.on-ki}"
    rounded: "{rounded.control}"
    padding: "0 20px"
    height: "44px"
  button-join:
    backgroundColor: "{colors.ki}"
    textColor: "{colors.on-ki}"
    rounded: "{rounded.control}"
    padding: "0 32px"
    height: "56px"
  button-secondary:
    backgroundColor: "{colors.well}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "0 16px"
    height: "40px"
  input:
    backgroundColor: "{colors.well}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "0 16px"
    height: "40px"
  panel:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.panel}"
  plate:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.plate}"
  format-bar-selected:
    backgroundColor: "{colors.lift}"
    textColor: "{colors.ink}"
    rounded: "6px"
---

# Design System: fantasy-kai

## Overview

**Creative North Star: "Prime Time", rebuilt (2026-10-19)**

A prime-time broadcast package for one idea: your rules decide the order. Night-game navy is the
stage, for the product and the public pages alike. Depth comes from four blue-black steps. A page's
sections are rounded panels, so the pieces read as deliberate and separate; the rows inside them stay
lines. Each page earns one focal plate: the landing's rule mixer, the player's card. The voice is a
heavy extra-condensed italic that leans forward like the logo's two strokes, and the logo's slant is
cut into every mark that says "this one".

**What changed on 2026-10-19, and why.** The owner asked for a full redesign: the landing read as
separate reports, the player page as a plain stack, and the scoring switch was buried in a console.
Three decisions follow from that brief and are recorded here because each reverses an earlier one:

- **One ground.** The public pages' light "Daylight" mode is gone. The landing stands on the
  product's own night ground, so it is the product's front room rather than a different building,
  and there is one set of tokens to keep honest.
- **Panels.** Rounded, hairline-bordered panels now separate a page's sections (the brief asked for
  "rounded, clearly separated sections"). Rows, table cells and rules stay square lines; there is
  never a box inside a box.
- **Chips.** A position's letters may sit on a chip tinted in their hue (14%). The letters are still
  always there, so colour is still never the only cue.

Rejected on purpose:

- a sidebar of dead links;
- phone mockups beside feature paragraphs;
- identical feature cards in a grid;
- tracked-caps eyebrow labels;
- glows, glass and decorative gradients (the two exceptions are named under Elevation).

**Key Characteristics:**
- A night (#060a12) ground; depth from surface, well and lift, blue rising with elevation.
- One superfamily in three widths; hierarchy from width, weight, case and slant.
- Radii by hierarchy: nested 2, control 8, panel 16, plate 24.
- The slash: the logo's slant, clipped into position chips, tier plates and the chosen tab.
- Orange means "the one thing to do". Blue means "you are here". They never trade jobs.
- Motion only says what changed: rows travel to their new places, the player view rises over the
  board, and the landing's mixer turns one dial by itself, once.

## Colors

### Primary
- **Ki Orange** (#ff8a3d), with **Ki Ink** (#140a02) on it (8.3:1). The brand and the one thing to
  do: the primary button (one per view), the wordmark's dot and orb, the S tier plate.

### Secondary
- **Energy Blue** (#3d8bff), with **Energy Text** (#8ab8ff) for text. "You are here" and "live": a
  chosen segment's base line, the chosen tab's slash, the active nav item, the focus ring, links, the
  board's fetching sweep, the open player's row on the board, the chosen ruleset in the Scoring panel.

### Tertiary
- **Position hues**: QB Violet (#c3a6ff), RB Red (#ff6b6b), WR Cyan (#5cc8ff), TE Magenta (#ff8fd4).
  Data colours. They colour a position's letters, and may tint that position's own chip at 14%
  behind them. Never a chart series on their own: as marks they fail colour-blind separation (WR
  against QB is dE 0.5 under deuteranopia, dataviz validator).
- **Starter Green** (#6fcf97): the board's single quality colour -- inside a 12-team league's
  starters at that position.
- **Notice Amber** (#d9c162): a roster status, a playoff round.
- **Danger** (#f28b8b): errors.
- **Chart Slate** (#5d667c): a bar with no rank to colour it.
- **The heat scale** (player view only): Heat Green (#36b37e), Yellow (#e3c04b), Orange (#ee8a3c)
  and Red (#e5574f) -- top band to bottom, for where a figure falls (see Heat tables). Solid on the
  performance chart's bars and their figures; solid as a tile's ground, its figure in canvas. Data colours,
  never a control or the brand. Red and green are not told apart by every eye, so a figure is always
  printed on its colour and a legend names the bands.

### Neutral
- **Night** (#060a12): every page background, and the board's rows on a phone.
- **Surface** (#0b1120): a panel -- a section of a page, the board, the player card, the mixer,
  the sticky table header, popovers.
- **Well** (#121a2c): things you type into or pick from, and row hover.
- **Lift** (#1b2640): the chosen one -- a chosen segment or ruleset, a tooltip.
- **Ink** (#eef2fa), **Mute** (#9aa6bd), **Faint** (#7d8aa3): text, in three steps.
- **Hairline** (rgb(148 170 210 / 0.12)): structure. **Edge** (#64718c): the 3:1 boundary of an
  input or secondary button, on every step including lift.

Every text pair is measured (WCAG 2) on canvas / surface / well / lift and recorded in
`frontend/src/app/globals.css`. The lowest: faint 4.3 on lift, so faint never sits on lift; RB 5.4
on lift; every position hue at least 5.2 over its own chip tint.

### Named Rules
**The One Orange Rule.** At most one orange action per view. The header's "Join the Kai" is
secondary while the page's own ask is on screen and becomes the orange primary once neither is.

**The Letters Carry Position Rule.** A position is always its letters; the hue is the second cue.

**The One Quality Hue Rule.** Quality is green, ink or faint, never red or yellow: red already means
RB.

**The Neutral Movement Rule.** A ruleset switch moves players, and movement is shown as an arrow and
a number -- ink on a lifted chip for up, grey for down -- never green and red. The ruler changed, not
the player.

## Typography

**Display:** Sofia Sans Extra Condensed, italic only. **Body:** Sofia Sans. **Labels and figures:**
Sofia Sans Semi Condensed, normal and italic. All three are self-hosted from files in the repo
(`frontend/src/app/fonts`, SIL OFL) through `next/font/local`, because the CSP allows fonts from the
site only and the build must not depend on reaching Google.

### Hierarchy
- **Display** (850 italic caps, line-height 0.86): the landing's h1 and closing ask at
  clamp(3.75rem, 10vw, 7.25rem); its section heads at clamp(2.75rem, 5.4vw, 4.25rem); a page's name
  (RANKINGS) at clamp(2.75rem, 6vw, 3.75rem); a player's name at clamp(2.75rem, 6.4vw, 5.25rem).
- **Title** (800 italic caps, 24px): a panel's name -- PERFORMANCE, GAME LOG.
- **Body** (400, 15-17px, line-height 1.55-1.65), the landing lead at 19px; measures near 36rem.
- **Label** (600 semi-condensed, 12-13px, sentence case): column headers, captions, a figure's
  name. Never tracked capitals.
- **Figure** (750 semi-condensed italic): a number that leads -- the player card's figures, a rank
  in a column, a tier plate.

### Named Rules
**The Tabular-In-Columns Rule.** Tabular figures wherever numbers stack in a column; proportional
for a number that stands alone.

**The No Eyebrow Rule.** No label above a heading. A board's recipe is said in a sentence ("Scored
under Half PPR for the 2026 season, by season total").

## Layout

- **Container:** at most 1440px, on every page.
- **Gutters:** 16px on phones, 24px from 640px, 32px from 1024px.
- **Breakpoints:** 640 (sm), 768 (md), 1024 (lg), 1280 (xl), 1536 (2xl).
- No sideways scroll at any width from 320; wide tables scroll inside their own panel, first
  column pinned.

### The product
- **Top bar:** sticky, 56px, the veil and a blur, one hairline under it. The active section carries
  the slash.
- **Rankings:** the title and the recipe sentence; then the scoring switch on a row of its own (the
  Format Bar); then position, window, season and find; then, after a switch, the moves it made; then
  the board, one panel, its column header pinned under the bar.
- **The player:** a sidebar and a column. The sidebar -- who he is, his season in figures, and the
  ruleset and season behind every number -- stays put from lg while the column scrolls; the column
  is the evidence, one panel under another (game log, performance, career, every ruleset) under a
  pinned bar of section links. Opened from the board, it is an overlay over the board (below);
  opened cold, a page.

### The landing
- **Pace:** hook, why, depth, everywhere, ask -- and no two sections in a row share a split:
  - the hero, 5 : 7, the words beside the rule mixer, the logo's strokes enormous behind it;
  - the receipt, 7 : 5, mirrored -- the working part on the left;
  - the player, a heading row then one full-width showcase;
  - your league, 5 : 6, the words beside a ruleset's spec sheet;
  - the ask at full width.
- **Phones get their own compositions:** the first screen holds the headline, the lead, the join
  and the mixer's dials; the mixer shows eight rows, not ten; the receipt's controls sit in its
  header; the showcase drops two figures.

## Elevation & Depth

Flat, by tone. One shadow exists, **Float** (`0 24px 48px -20px rgb(0 0 0 / 0.9), 0 2px 8px -2px
rgb(0 0 0 / 0.6)`), for what floats: popovers, tooltips, the player overlay, the landing's mixer
and the sign-in page's still board. Nothing glows.

The two gradients the product draws are light, not decoration: the player card's light behind the
cut-out, in the position's hue at 26%; and the overlay's dimmed board (the scrim).

## Shapes

- **Radius by hierarchy:** 0 for structure (rows, rules); 3px for a heat table's tiles; 2px for a control inside a control;
  8px for anything you operate; 16px for a panel; 24px for the focal plate; a full pill only for
  circular marks (avatars, the chart's zero ring).
- **The slash:** `clip-path` on the logo's slant (`slash` in globals.css), never a skew transform, so
  the letters inside stay upright. Small marks only: position chips, tier plates, the chosen tab's
  and nav item's 3px bar.
- **Imagery:** a player's photo is ESPN's whole cut-out, standing on the card's plate so its flat crop line
  is the floor; never a circle cropped from it at display size (the 36px row avatar excepted). With
  no headshot the plate shows his initials as outlined display type in his hue. The landing shows no
  real player's likeness (owner decision 2026-09-28, kept), and so uses exactly that fallback.

## Components

### Buttons
Primary: orange, 44px (56px for the public pages' "Join the Kai"), one per view. Secondary: Well with
a 1px Edge, 40px. Icon: 40px square, for close, previous and next. Quiet link: Ink with an Edge
underline that turns blue on hover.

### The Format Bar (signature)
The scoring switch as a headline control: every ruleset the member can use side by side, each with
its name and what it pays a catch ("½ per catch", held to V3 by a test; a member's own says "Your
ruleset"). Native radios in a fieldset. The chosen one sits on Lift with the energy slash under it.
Three to a row on a phone; as wide as its options from sm.

### Position chips
`PositionChip`: the letters on the slash, tinted in their hue, with the positional rank after them in
ink ("WR 1").

### Tabs and section links
`Tabs` + `TabPanel`: the WAI-ARIA tablist (roving focus, arrows, Home and End, automatic
activation), for panes that replace each other (the landing's showcase). Where every panel is on the
page -- the player view -- the bar is section links instead: each scrolls to its panel and names it
in the URL, and the current one follows the scroll. Either way the chosen item is ink, semibold and
carries the energy slash.

### Status
`StatusMessage`: a panel with an icon that names the kind (info or alert), a title that says what
happened, a line that says what to do, and at most one action. A failure tints toward danger.

### The board
Rows are lines on the board's panel: an italic rank, a 36px face, the name at 16px, a position chip,
team, games, a neutral gauge, points, per game in the quality hue, and -- once there is a previous
ruleset -- movement. Tier dividers are plates on the slash; S is orange. The open player's row keeps
an energy edge.

### The player card (signature)
The sidebar's first card: the position chip, team and any roster status; the first name small over
the surname at display size; the cut-out on a plate lit in the position's hue in front of the logo's
two strokes; then seven figures in two rows -- overall rank, position rank (quality-coloured), per
game, age; points, games, weeks inside a 12-team league's starters -- and the season and ruleset
they are for. The second card is the scoring: one ruleset a row (the Format Bar's list layout) and
the season, three to a row on a phone.

### Heat tables
The game log and the career are grids of tiles, 2px apart, every figure on its own small ground in
the four-step data scale -- green, yellow, orange, red (`--color-heat-*`, owner decision
2026-10-20) -- solid, with the figure in canvas. Tinted grounds under ink were tried first and
dropped: over the night ground yellow reads olive and orange brown. Fantasy tiles (points, rank) by where the figure finished at his position, in a 12-team
league's terms: 1st-12th green, 13th-24th yellow, 25th-36th orange, below that red. Box-score tiles
against the league's games: the quartiles of every regular-season game, 2023-25, by each season's
fantasy-relevant players by volume (top 24 QBs by attempts, 48 RBs by carries plus catches, 72 WRs
and 24 TEs by targets) -- `lib/heatBands.ts`, written by `scripts/heat-bands.mjs` from the
database. Interceptions run the other way, and none is always the top band. A stat the position has
no cut-off for (a receiver's carries, a quarterback's snaps, fumbles) sits on the table's faint
ground. The career colours by the per-game rate in both its per-game and totals views. A legend over
each table names both scales; every value is printed, so the colour is never the only cue.

### The player overlay
Opened from the board, the player view is an intercepted route on the player's own URL, shown in a
native `<dialog>` over the dimmed board -- up to 1600px wide on a desktop, with the board showing at
its edges, and the whole screen on a phone.
Closing is always the browser's back, so Escape, the close button, a click on the board and the
back button agree with the URL. Previous and next step through the board's visible order.

### The performance chart
Columns with their figures on top: his last 17 regular-season games across seasons, with a rule and
the two seasons' names where one ends, or any of his four latest seasons week by week. Each bar and
its figure take the four-step scale by where the week finished at his position (1-12 green, 13-24
yellow, 25-36 orange, 37+ red); an unranked game is slate. In one season a week with no game
recorded is a dashed empty slot; a zero-point game is a ring on the zero line. A dashed average, labelled (the season's own points per
game, or the mean of the 17 figures shown). One Tab stop; arrows walk the games.

### The rule mixer (landing)
The season's real board under two dials -- points per catch (0, ½, 1) and passing touchdown (4, 6) --
six captured boards from the real engine (`scripts/capture-landing.mjs`). Turning a dial sorts the
board and the rows travel; a line says what the board is scored as and names the biggest move. It
turns one dial by itself, once, when first in view; never under reduced motion.

### The receipt (landing)
One real game's stat line priced under 0 PPR, Half PPR or PPR, line by line, adding up to the API's
own total for that game (held by a test), with the three totals side by side under it.

## Do's and Don'ts

### Do:
- **Do** put every page on the night ground and separate sections with panels.
- **Do** state a board's recipe in a sentence wherever a ranking appears.
- **Do** keep one orange action per view.
- **Do** colour a position's letters, and keep the board's quality to green, ink and faint; the
  four-step heat scale belongs to the player view's tables and chart.
- **Do** measure every new text pair against the surface it sits on, and record it in
  `globals.css`.

### Don't:
- **Don't** put a box inside a box: rows are lines, and a panel's content sits on the panel.
- **Don't** add a label above a heading, or set labels in tracked capitals.
- **Don't** add a glow, glass, or a gradient wash.
- **Don't** put any real player's likeness on the landing.
- **Don't** show a metric the product does not compute, or draw a control that cannot be used.
- **Don't** use an opacity modifier on a text colour token (`text-faint/50`); a test refuses it.
