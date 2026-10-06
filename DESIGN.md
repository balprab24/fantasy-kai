---
name: fantasy-kai
description: Fantasy football rankings scored by your league's own rules -- one broadcast package in two modes, Daylight for the public pages and Prime time for the product.
colors:
  canvas: "#000000"
  surface: "#0a0f1c"
  well: "#111a2c"
  lift: "#1a2540"
  ink: "#eef2fa"
  mute: "#9aa6bd"
  faint: "#76839c"
  line: "rgb(148 170 210 / 0.13)"
  line-strong: "#5b6781"
  ki: "#ff8a3d"
  on-ki: "#140a02"
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
  ki-text: "#ff8a3d"
  track: "rgb(255 255 255 / 0.06)"
  highlight: "rgb(255 255 255 / 0.08)"
  veil: "rgb(0 0 0 / 0.8)"
  band: "#0a0f1c"
  daylight-canvas: "#fbfaf7"
  daylight-band: "#edf1f7"
  daylight-surface: "#ffffff"
  daylight-well: "#ffffff"
  daylight-lift: "#e2e9f6"
  daylight-ink: "#0b1530"
  daylight-mute: "#46526a"
  daylight-faint: "#5c6780"
  daylight-line: "rgb(36 104 255 / 0.16)"
  daylight-line-strong: "#7a8599"
  daylight-ki-text: "#bd4c08"
  daylight-energy: "#2468ff"
  daylight-energy-text: "#1c52d0"
  daylight-danger: "#c2362c"
  daylight-pos-qb: "#6a44d8"
  daylight-pos-rb: "#c92d3a"
  daylight-pos-wr: "#0a7684"
  daylight-pos-te: "#b3237c"
  daylight-q-good: "#1b7a47"
  daylight-q-mid: "#866600"
  daylight-chart-starter: "#1f5fe0"
  daylight-chart-rest: "#7e889f"
  daylight-track: "rgb(11 21 48 / 0.08)"
  daylight-veil: "rgb(251 250 247 / 0.85)"
typography:
  display:
    fontFamily: "Sofia Sans Extra Condensed, ui-sans-serif, system-ui, sans-serif"
    fontSize: "clamp(4.25rem, 9vw, 8.25rem)"
    fontWeight: 850
    lineHeight: 0.86
    letterSpacing: "-0.004em"
  headline:
    fontFamily: "Sofia Sans Extra Condensed, ui-sans-serif, system-ui, sans-serif"
    fontSize: "2.25rem"
    fontWeight: 800
    lineHeight: 1
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
  stat:
    fontFamily: "Sofia Sans Semi Condensed, ui-sans-serif, system-ui, sans-serif"
    fontSize: "2.5rem"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.01em"
  rank:
    fontFamily: "Sofia Sans Semi Condensed, ui-sans-serif, system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 600
    fontFeature: "tnum"
rounded:
  nested: "2px"
  control: "4px"
  plate: "24px"
  pill: "9999px"
spacing:
  gutter-sm: "16px"
  gutter-md: "24px"
  gutter-lg: "32px"
  section: "96px"
  section-lg: "128px"
components:
  button-primary:
    backgroundColor: "{colors.ki}"
    textColor: "{colors.on-ki}"
    rounded: "{rounded.control}"
    padding: "0 20px"
    height: "44px"
  button-secondary:
    backgroundColor: "{colors.well}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "0 16px"
    height: "36px"
  input:
    backgroundColor: "{colors.well}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "0 16px"
    height: "48px"
  segment-selected:
    backgroundColor: "{colors.lift}"
    textColor: "{colors.ink}"
    rounded: "{rounded.nested}"
    height: "32px"
  console:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.control}"
    padding: "6px"
  button-join:
    backgroundColor: "{colors.ki}"
    textColor: "{colors.on-ki}"
    rounded: "{rounded.control}"
    padding: "0 28px"
    height: "56px"
  input-daylight:
    backgroundColor: "{colors.daylight-well}"
    textColor: "{colors.daylight-ink}"
    rounded: "{rounded.control}"
    padding: "0 16px"
    height: "48px"
  stops-thumb:
    backgroundColor: "{colors.lift}"
    textColor: "{colors.ink}"
    rounded: "{rounded.nested}"
  plate-hero:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    rounded: "{rounded.plate}"
  plate-player:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.plate}"
---

# Design System: fantasy-kai

## Overview

**Creative North Star: "Prime Time"**

A prime-time broadcast graphics package for one idea: your rules decide the order. True black is the
stage. Separation comes from three blue-black steps, never from grey cards. Each page earns at most one
large focal plate. The imagery is real: a player's cut-out, a season's weeks, a board's rows. The
voice is a heavy extra-condensed italic that leans forward like the logo's two strokes. Hairlines do
the structural work that boxes used to do.

Two modes, one grammar. **Prime time** is the product: the board under the lights, true black, dense,
lines instead of cards, a single console of controls, tabular figures in columns. **Daylight** is the
public pages -- the landing, sign-in, register -- where a visitor decides in daylight whether to
trust the tool: the pre-game studio. A paper ground (a white with the faintest warmth, never cream),
one cool band for the section that alternates, navy ink, white for what floats or is typed into,
and blue risen into the hairlines. The type, the components and the two accents are the same; only
the tokens change value (`.daylight` in `globals.css`), so the two read as one product. Where a
Daylight page shows the product, the product keeps its own dark skin (`.primetime`), at most twice:
the landing's hero is one player's season under each of the three presets, with the top of the
board each one produces re-sorting under it, and once down the page the studio cuts to the game
feed -- a full-width band of the product as it looks, the board and a player opened -- and comes
back. Signing in is the moment the whole screen turns to Prime time. The anime influence is the
orb for the wordmark's dot, and the lean of the italic; the drawn route that was its other half
left with the hero's lower-third on 2026-10-06. Someone who has never watched anime should see a
sports product.

Rejected on purpose:

- rounded-card dashboards;
- a sidebar of dead links;
- phone mockups beside feature paragraphs;
- tracked-caps eyebrow labels;
- charcoal grey.

**Key Characteristics:**
- Prime time: a true black (#000) canvas; depth from blue-black steps, blue rising with elevation.
- Daylight: a paper (#fbfaf7) ground, never cream, and one cool band (#edf1f7); the product
  appears on it in its own dark skin at most twice -- the hero's plate and one full-width band.
- One superfamily in three widths, hierarchy from width, weight, case and slant.
- Four radii, each earned; hairlines instead of containers.
- Orange means "the one thing to do". Blue means "you are here". They never trade jobs.
- Motion only says what changed. The landing gets one orchestrated entrance: the board re-sorting.

## Colors

Two grounds, one black and one paper, with the same two signal colours from the logo, and data
colours that only ever colour letters. Everything below is Prime time; Daylight's own values follow
under "Daylight".

### Primary
- **Ki Orange** (#ff8a3d), on which text is set in **Ki Ink** (#140a02). The brand and the one thing
  to do. It marks:
  - the primary button (one per view);
  - the wordmark's middle dot and the orb;
  - the S tier letter and a derived season total, as **Ki Text** -- the same #ff8a3d in Prime time,
    deepened in Daylight, where #ff8a3d as text on paper is 2.25:1.

### Secondary
- **Energy Blue** (#3d8bff), with **Energy Text** (#8ab8ff) for text. "You are here" and "live". It
  marks:
  - a selected segment's 2px base line and the active nav item's bar;
  - the focus ring;
  - links;
  - the board's fetching sweep;
  - the story line across the landing's three boards, and the followed row on its hero board (a
    full-row 15% tint);
  - in Daylight, the data: a chart's key series (a starter week) is blue.

### Tertiary
- **Position hues**: QB Violet (#c3a6ff), RB Red (#ff6b6b), WR Cyan (#5cc8ff), TE Magenta (#ff8fd4).
  They are semantic data colours. They only ever colour the letters of a position ("RB1").
- **Starter Green** (#6fcf97): the single quality colour, meaning inside a 12-team league's starters at
  that position.
- **Notice Amber** (#d9c162): a roster status, a playoff round.
- **Danger** (#f28b8b): errors.
- **Chart pair**: Chart Green (#3fa877) for a top-line week and Chart Slate (#5d667c) for any other
  week. This is an emphasis pair, validated as fills on the black canvas.

### Neutral
- **Stage Black** (#000000): every product page background, the board's rows, and the landing's
  product plate.
- **Deep Navy Surface** (#0a0f1c): the one separation layer. The control console, the sticky table
  header, popovers, the player plate.
- **Well** (#111a2c): things you type into or pick from. Inputs, segmented tracks, row hover.
- **Lift** (#1a2540): the chosen one. A selected segment, a tooltip.
- **Ink** (#eef2fa): primary text.
- **Mute** (#9aa6bd): secondary text.
- **Faint** (#76839c): tertiary text, and the "below the bench" figure.
- **Hairline** (rgb(148 170 210 / 0.13)): structure.
- **Edge** (#5b6781): the 3:1 boundary of an input or secondary button.

### Daylight
The public pages' values for the same roles, measured (WCAG 2) on canvas (paper) / band / surface /
lift and recorded in `globals.css` (re-measured 2026-10-05):

- **Paper** (#fbfaf7) canvas: the page; **Band** (#edf1f7): the one section that alternates, and the
  footer; **White** (#ffffff) surface and well -- what floats and what is typed into; **Lift**
  (#e2e9f6), the chosen one, a hint of blue. Paper to band is 1.09:1: a change of ground, not a box.
- **Navy Ink** (#0b1530) 17.3 / 15.9 / 18.0 / 14.8; **Mute** (#46526a) 7.5 / 6.9 / 7.9 / 6.4;
  **Faint** (#5c6780) 5.4 / 5.0 / 5.7 / 4.65.
- **Hairline** is energy blue at 16%, so the structure itself is faintly electric; **Edge**
  (#7a8599) 3.6 / 3.3 / 3.7 / 3.05.
- **Energy** (#2468ff) 4.5 / 4.1 / 4.7 / 3.8, marks and large text; **Energy Text** (#1c52d0) 6.4
  / 5.9.
- **Ki Text** (#bd4c08) 4.8 / 4.4 / 5.0 / 4.1 -- on the band only at stat size. Ki Orange stays
  #ff8a3d as a fill, with Ki Ink on it (8.3).
- **Positions** QB #6a44d8, RB #c92d3a, WR #0a7684 (teal, so a receiver is never read as energy's
  "you are here"), TE #b3237c: 5.1 and up on paper, 4.7 on the band. Letters only, as everywhere:
  on the landing's three boards they carry the story's other half (quarterbacks fill the 0 PPR top
  and drain out of the PPR one) while blue carries the one player followed.
- **Chart pair** Chart Blue (#1f5fe0) for a starter week and Chart Slate (#7e889f) for any other:
  dataviz validator on chalk, CVD dE 17.5, normal-vision dE 19.7, both >= 3:1 (5.3 and 3.4 on
  paper). Prime time keeps its green pair; the brief gave the public pages' data to blue. On the
  landing's season bars the same pair says what a catch adds (blue) against everything else (slate),
  re-validated on the band 2026-10-06: CVD dE 17.5, normal-vision dE 19.7, both >= 3:1; the slate's
  low chroma is the "not this" colour, as before.
- **Veil** (paper at 85%): the site header's see-through ground, a token rather than `bg-canvas/85`.
  Tailwind compiles an opacity modifier to a literal Prime time colour and uses the variable only
  inside `@supports (color-mix)`, so a browser without color-mix (older than about 2023) shows
  Prime time's value for any `/NN` colour in Daylight. The header was fixed; the week chart's
  average line and faded axis labels remain on that fallback, and are minor.

### Named Rules
**The Island Rule.** A Daylight page shows the product in its own Prime time skin (`.primetime`) at
most twice: as its focal plate, and once as a full-width band where the page cuts to the product
(owner brief, 2026-10-05). Everything else on the page speaks Daylight. `tests/lib.test.ts`
holds `.primetime` equal to `@theme`, value for value, and `.daylight` to overriding every colour
except the shared orange fill.

**The One Orange Rule.** At most one orange action per view. The header's "Join the Kai" is secondary
while the page's own ask -- the hero's button, the closing form -- is on screen, and becomes the orange
primary once neither is. Orange never marks a link, a selection or decoration.

**The Letters Carry Position Rule.** A position hue never fills, never marks selection, and never
encodes a chart series on its own. As marks the four hues fail colour-blind separation: WR against QB
is ΔE 0.5 under deuteranopia (dataviz validator).

**The One Quality Hue Rule.** Quality is green, ink or faint, never red or yellow. Red already means
RB, so a red "RB68" would mean two things at once.

**The Faint-Never-On-Lift Rule.** Faint text measures 3.98:1 on lift, so it never sits there. Every
other text pair is at least 4.5:1, measured and recorded in `frontend/src/app/globals.css`.

## Typography

**Display Font:** Sofia Sans Extra Condensed, italic only (ui-sans-serif fallback)
**Body Font:** Sofia Sans (ui-sans-serif fallback)
**Label Font:** Sofia Sans Semi Condensed, normal and italic

**Character:** one superfamily used at three widths. The extra-condensed heavy italic is the
broadcast voice: headlines, page titles, player names, tier letters, all in capitals. The
semi-condensed width is the instrument panel: labels, ranks, stat figures. The regular width is
everything you read and operate. All three are self-hosted through `next/font`, because the CSP
allows fonts from the site only.

### Hierarchy
- **Display** (850 italic caps, clamp(5rem, 8.2vw, 8rem) for the landing's h1 from lg and its closing
  ask, and clamp(2.5rem, 5.2vw, 4.5rem) for a player's name, line-height 0.86): headlines and names.
  The brief's call for large editorial type overrides the craft floor's 6rem cap on the landing only.
- **Headline** (800 italic caps, 36px, 1): a page's name, such as RANKINGS or SCORING RULESETS.
- **Title** (800 italic caps, 24px, 1): a segment inside a page, such as PERFORMANCE or GAME LOG.
  Section headings on the landing use the display voice at clamp(2.75rem, 4.6vw, 3.75rem), broken
  by phrase, one line per thought; only the h1 and the closing ask are set at display size.
- **Body** (400, 15-17px, 1.55-1.65): prose, with the landing lead at 19px. Measures stay near 36rem
  or 52ch. `ch` is the width of a zero, which is wider than this face's average letter, so 72ch sets
  96 characters to a line.
- **Label** (600 semi-condensed, 12-13px, sentence case): column headers, captions and meta. It is
  never set in tracked capitals.
- **Stat** (700 semi-condensed, 40px, proportional figures): a number standing alone, with its unit
  small after it ("416.6 pts").
- **Rank** (600 semi-condensed italic, 15px, tabular): a rank in a column.

### Named Rules
**The Tabular-In-Columns Rule.** Use tabular figures wherever numbers stack in a column, and
proportional figures for a number that stands alone. Never set `tnum` on body text.

**The No Eyebrow Rule.** Put no label above a heading. The heading carries its own weight, and a
page's recipe is said in a sentence ("Scored under Half PPR for the 2025 season, by season total").

## Layout

- **Container:** one container, at most 1320px wide.
- **Gutters:** 16px on phones, 24px from 640px, 32px from 1024px.
- **Breakpoints:** 640 (sm), 768 (md), 1024 (lg), 1280 (xl) and 1536 (2xl).
- **Product type is fixed-size.** Fluid type is used on the landing only.

**Product pages**

- **Top bar:** sticky, 56px tall, translucent black with a backdrop blur and no border.
- **Opening:** a page opens with its title, then its recipe line, then the console.
- **The board:** a table whose header row sticks at 56px.
  - Rows are 44px tall, and 48px below 640px.
  - Columns step away by importance, and what leaves a phone folds into a second line under the name.
  - At 390px the board fits without sideways scroll.

**The landing (Daylight)**

- **Pace:** the studio show, and the ground changes with the argument (owner brief 2026-10-05):
  - the hero, on paper;
  - one rule, three boards, on paper: a heading and its line over the three boards side by side;
  - the cut to the product, on the stage: a full-width Prime time band, the board and one player;
  - your rates, every game, on the band, mirrored -- the working part left, the words right;
  - what is coming, a six-across strip on paper; the last ask at full width; the footer on the band.
  The compositions change on purpose: no two sections in a row share the same split.
- **Spacing:** 64px of section padding on phones, 80px from 640px, 112px from lg; a hairline opens
  the rule section and the last ask, on the content's edges.
- **Hero:** 5/12 and 7/12 from lg -- the headline, the lead and one orange "Join the Kai" on the
  left, the plate, the larger half, on the right. Below lg it reads headline, lead, a full-width
  button, then the plate, so a phone's first screen holds the action and the three formats.
- **Sign-in and register:** the form on the content edge, and from lg a still five-row plate of the
  real board beside it.

## Elevation & Depth

Flat at rest. Depth is tonal: the canvas, surface, well and lift steps.

One shadow exists, for things that float (popovers, tooltips). It has an offset and a soft blur, so
it reads as height, never as a glow. Daylight redefines it as a soft navy shadow, never black. The
landing's plate is flat: it is ground, not a floating card.

No glow exists. The landing's drawn route had one (a 5px energy-blue drop-shadow) until 2026-10-05,
when the brief ruled out glows and a reviewer read it as the one neon-dashboard element on the page:
the line is data, and a halo around it is decoration. The route itself left the landing on
2026-10-06; the three boards' story line keeps the rule.

### Shadow Vocabulary
- **Float** (`box-shadow: 0 18px 40px -16px rgb(0 0 0 / 0.95), 0 2px 8px -2px rgb(0 0 0 / 0.7)`):
  the Coming popover, chart tooltips. Daylight: `0 22px 44px -22px rgb(11 21 48 / 0.34), 0 2px 8px
  -2px rgb(11 21 48 / 0.1)`.

### Named Rules
**The No Glow Rule.** Nothing glows. The S tier letter is marked by orange alone, the story line by
its blue stroke alone.

## Shapes

- **Radius:**
  - 0 for structure (rows, tables, sections, rules);
  - 4px for anything you operate (buttons, inputs, the console);
  - 2px for a control nested inside a control;
  - 24px for the one focal plate on a page (the landing's product plate, the player plate -- which
    the landing's product band also shows, as the app does);
  - a full pill only for circular marks (avatars, chart end markers).
- **Chart bars:** 4px rounded data ends, square at the baseline, at most 24px wide.
- **Borders:** hairlines only where they are structure (row separators, a segment's rule, the sticky
  header's base). An input keeps one 3:1 edge.
- **Imagery:** a player's photo is ESPN's whole cut-out, stood on the plate so its flat crop line is
  the plate's edge, and never a circle cropped from it (except the 32px row avatar). Below 768px the
  image fades out instead of ending mid-plate.

## Components

### Buttons
- **Shape:** a gently squared rectangle (4px).
- **Primary:** Ki Orange with Ki Ink text, 44px tall with 20px sides, 600 weight. Hover brightens it
  (`filter: brightness(1.1)`). One per view.
- **Secondary:** a Well fill with a 1px Edge ring, 36px tall. The ring turns Mute on hover. It covers
  retry, show more, cancel, and the site header's join while the page's own ask is in view.
- **Quiet link:** Ink text with an Edge underline 4px below; the underline turns blue on hover.
  Destructive actions stay grey until hovered or focused, and then turn danger red.

### The Stops (the landing's switch)
- **Style:** `SegmentedControl`'s `stops` variant: a radio group of equal columns on the Well step
  (4px), filling the width it is given, each option a whole figure rather than a word -- the hero's
  three formats, the catch rate's three values.
- **State:** the chosen option sits on a Lift thumb (2px) with the 2px energy base line, the
  console's selected-segment grammar at landing scale, and the thumb slides to the next choice. It
  rests by `left`, not a transform: resting on a fractional translateX it showed a 1px seam partway
  down its edge in Chrome captures (tile rasterisation, most likely; not proven), and by `left` it
  does not. Every option stays in full ink -- the options are figures to compare, and the thumb
  alone says which is chosen. A face's height comes from the caller as `min-h-*`, which the face's
  own `h-full` cannot override (an `h-12` lost to it and rendered a 30px control, 2026-10-06).

### Cards / Containers
None. The product has no cards. The only containers are the console strip, the focal plates and the
tonal status block.

### Inputs / Fields
- **Style:** a Well fill, a 1px Edge border, 4px corners, 48px tall on forms and 40px (32px from md)
  in the console.
- **Focus:** the border turns Energy Blue, plus the global 2px blue focus ring with a 2px offset. The
  caret is blue.
- **Error:** a tonal danger block (7-8% danger over the page) with danger text. There is no border.
- **Joining (Daylight):** the landing's last ask only (the hero carries a button to `/register`).
  Two labelled fields, email and password, the password one disabled until the email is in ("after
  your email"), and the orange "Join the Kai" -- one row from lg, the fields 56px to meet the
  button, stacked below that with the button full width on a phone. A lone email field beside a
  button is the shape of a newsletter signup, and this is an account.

### Navigation
- **Top bar:** the wordmark, then Rankings and Scoring in 14px Mute (the active item in Ink with a 2px
  blue bar at the bar's bottom edge), then "Coming". Coming opens a native popover listing the six
  unbuilt sections; they are never links, and the popover closes on Escape, an outside click, or Tab.
- **Mobile:** below 768px the bar keeps the wordmark and the two live sections, and a `<dialog>` menu
  holds Coming and the account.
- **Site header (Daylight):** the wordmark, then Scoring, Rankings and Players, links that scroll to
  the landing's sections (the landing only; what is coming has a section, not a link), Sign in, and
  the join, which turns orange only while neither of the page's own asks is on screen. The section
  being read is underlined in blue, and nothing is once the page has gone past the last one named.
  It rests on the page with no edge, on the Veil; a blue hairline appears once the page scrolls under
  it.

### The Console (signature)
Every board control sits on one Surface strip:

- scoring first, since it is the product's argument;
- then season, window, position and find, with hairline seams between groups.

Segments have no track of their own. The selected one sits on Lift with a 2px blue base line. The
strip wraps rather than scrolls.

### The Plate (signature)
- **On the landing:** the product in Prime time on the paper page, as a broadcast graphic of one
  player: CHRISTIAN McCAFFREY in the display voice, then his 2025 season under 0 PPR, Half PPR and
  PPR as three stops -- each a format, its rate per catch, the overall rank at display size (#7, #1,
  #1), the points at stat size (314.6, 365.6, 416.6), the RB rank and what the step bought ("+51.0
  from catches") -- which are also the switch, and the line under his name says so. Under them, the
  top of the board the chosen format produces: the top five and a sixth slot that follows McCaffrey
  (7th under 0 PPR, under a dashed rule that says places were skipped), with a fixed rank column the
  players travel past, as on a broadcast leaderboard, and each move counted from 0 PPR -- the same
  board always shows the same moves, and the 0 PPR board shows none. Under 0 PPR the five are
  quarterbacks; under PPR the letters turn RB, WR, RB, RB, QB. In flight every row but McCaffrey's
  steps back to 20%, so his climb is the move that reads.
  On load it plays the rule change once: the chosen step stands on 0 PPR and the board in 0 PPR
  order with 0 PPR figures, holds 600ms, and travels into PPR as the step slides across and every
  figure turns into its PPR value; rows in flight dim; the moves land last. The server renders the
  finished board, so reduced motion or no script shows it at once.
- **On a player page:** the cut-out stands on a Surface plate, lit from behind by a tight stage light
  in the position's hue (30%). The name is set at display size and the season line as type, not tiles.

### The Product Band (signature)
Where the landing cuts to the game feed: full width, the stage's black, the app's own parts and
nothing drawn to look like them -- the real console with WR chosen, the WR board's top ten across
three tiers with the followed receiver's row lit (energy at 15%), and beside it that player opened
(the plate without a cut-out, the weekly chart, his finished seasons). It shows the product doing
something the hero's board does not -- a filter -- rather than the same names again. On a phone it
stays about one screen (owner decision 2026-10-05): three rows across the first tier break, and the
plate with the weeks said in one line ("a top-36 receiver in 15 of 16 weeks") and where the rest
lives; the weekly chart and the finished seasons appear from sm up.

### The Three Boards (landing)
One rule, three boards, side by side on paper: the 0 PPR, Half PPR and PPR boards under a 2px ink
rule, each running to 20th -- the depth the story starts from. The top eight in ink (rank, name,
position letters, points from md), 9th to 20th named in Faint, so the reader sees who the story
passes rather than an empty ruler (a reviewer read the first, unnamed version as a half-loaded
table). Every row is one place, the same height on all three, so how high a row stands is its rank.
The story is one player -- Puka Nacua, 20th, 11th, 2nd -- lit on each board (Lift; his rank in Energy
Text and his letters in ink, since no position hue reaches 4.5:1 on Lift) and carried across the
gaps by one 3px energy line. No other lines and no dots: the other players' moves are in the boards
themselves. Lists in Daylight, not the product's table, under the Island Rule. Below sm (three ~105px
columns) a board names only its top five and the story's row -- sixty names there read as a report --
and keeps the rest of its depth as a rail ticked at 10, 15 and 20, so the climb is still drawn true.

### The Catch Rate (landing)
The one rate the three presets disagree on, as a sentence with a control in it -- "Your league pays
[0 | 0.5 | 1] a catch", the stops at 48px -- on the band. It re-prices three things together:
- one game as an equation, 32.1 (yards and TDs, which every preset scores alike) + 7 × the rate =
  the week, the rate in Energy Text and the result at display size. From sm it is one line, the
  captions under the terms carrying the two chart colours and no width of their own, so a long
  caption never spreads the terms; on a phone it is written as a column sum, each term beside its
  name. The 32.1's own parts are printed small under it;
- the season as 18 week bars, each a slate base (the week under 0 PPR) and a blue cap (what catches
  add at the chosen rate, `catchCaps`), on one fixed scale so 0 shrinks every week and 1 grows them
  back. No legend: the equation's captions are the key. Week 7 is labelled with its two parts
  ("32.1 + 7.0") and the rest on hover. A cap is laid out at its PPR height and scaled from its foot,
  so a switch moves the bars by transform, never by re-laying them out;
- the season total at display size in Ki Text, with how much of it came from catches.
All from captured data. The other rates are printed, never drawn as fields: a field that cannot be
typed into is a lie about the page.

## Do's and Don'ts

### Do:
- **Do** put every product page on true black (#000000) and separate with Surface, Well and Lift
  only, rising in that order; put every public page on paper, in `.daylight`, with one cool band.
- **Do** show the product on a Daylight page in its own dark skin, at most twice: the page's plate
  and one full-width band.
- **Do** set page names and segments in the extra-condensed italic caps, and everything operable in the
  regular width at fixed sizes.
- **Do** state a board's recipe in a sentence wherever a ranking appears: ruleset, season, window.
- **Do** keep one orange action per view: the header's join is secondary while the page's own ask is
  in view.
- **Do** colour a position's letters only, and keep quality to green, ink and faint.
- **Do** measure every new text pair against the surface it sits on, and record it in `globals.css`.

### Don't:
- **Don't** put rows, panels or stat tiles in rounded bordered cards. Rows are lines; a page's
  segments are a heading, a hairline and content.
- **Don't** add a label above a heading, or set labels in tracked capitals.
- **Don't** add a glow, a gradient wash, glass, or a zero-offset coloured halo.
- **Don't** crop a player's cut-out into a circle at display size, or put any real player's likeness
  on the landing without a promotional licence and the player's consent.
- **Don't** show a metric the product does not compute.
- **Don't** use orange for links or selection, or blue for fills and headings -- except Daylight's
  chart blue, which is data, a step deeper than energy so a bar never reads as a control.
- **Don't** use an opacity modifier (`bg-canvas/85`) for a colour that must be right in both modes on
  every browser; give it a token.
- **Don't** float a stat tile over the landing's plate, or put a monogram where a face would be.
- **Don't** give two sections in a row the same split, or draw a control that cannot be used.
