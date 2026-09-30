---
name: fantasy-kai
description: Fantasy football rankings scored by your league's own rules -- a prime-time broadcast package for one idea.
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
  chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "10px 16px"
  plate-hero:
    backgroundColor: "{colors.ki}"
    textColor: "{colors.on-ki}"
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

Two registers, one grammar. The landing page is expressive: display type at editorial scale, an
orange plate carrying a real season, readouts floating over its edges. The product is dense: lines
instead of cards, a single console of controls, tabular figures in columns. The anime influence is
one drawn route (a season traced over its bars, like an analyst's telestrator stroke) and an orb for
the wordmark's dot. Someone who has never watched anime should see a sports product.

Rejected on purpose:

- rounded-card dashboards;
- a sidebar of dead links;
- phone mockups beside feature paragraphs;
- tracked-caps eyebrow labels;
- charcoal grey.

**Key Characteristics:**
- True black (#000) canvas; depth from blue-black steps, blue rising with elevation.
- One superfamily in three widths, hierarchy from width, weight, case and slant.
- Four radii, each earned; hairlines instead of containers.
- Orange means "the one thing to do". Blue means "you are here". They never trade jobs.
- Motion only says what changed. The landing gets one orchestrated entrance.

## Colors

A black stage with two signal colours from the logo, and data colours that only ever colour letters.

### Primary
- **Ki Orange** (#ff8a3d), on which text is set in **Ki Ink** (#140a02). The brand and the one thing
  to do. It marks:
  - the primary button (one per view);
  - the wordmark's middle dot and the orb;
  - the S tier letter;
  - the landing's focal plate;
  - a derived season total.

### Secondary
- **Energy Blue** (#3d8bff), with **Energy Text** (#8ab8ff) for text. "You are here" and "live". It
  marks:
  - a selected segment's 2px base line and the active nav item's bar;
  - the focus ring;
  - links;
  - the board's fetching sweep;
  - the landing's drawn route.

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
- **Stage Black** (#000000): every page background and the board's rows.
- **Deep Navy Surface** (#0a0f1c): the one separation layer. The control console, the sticky table
  header, chips, popovers, the player plate, the scoring band on the landing.
- **Well** (#111a2c): things you type into or pick from. Inputs, segmented tracks, row hover.
- **Lift** (#1a2540): the chosen one. A selected segment, a tooltip.
- **Ink** (#eef2fa): primary text.
- **Mute** (#9aa6bd): secondary text.
- **Faint** (#76839c): tertiary text, and the "below the bench" figure.
- **Hairline** (rgb(148 170 210 / 0.13)): structure.
- **Edge** (#5b6781): the 3:1 boundary of an input or secondary button.

### Named Rules
**The One Orange Rule.** At most one orange action per view. The header's "Join the Kai" is secondary
because the page's own form carries the orange. Orange never marks a link, a selection or decoration.

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
- **Display** (850 italic caps, clamp(4.25rem, 9vw, 8.25rem) on the landing and clamp(2.5rem, 5.2vw,
  4.5rem) for a player's name, line-height 0.86): headlines and names. The brief's call for large
  editorial type overrides the craft floor's 6rem cap on the landing only.
- **Headline** (800 italic caps, 36px, 1): a page's name, such as RANKINGS or SCORING RULESETS.
- **Title** (800 italic caps, 24px, 1): a segment inside a page, such as PERFORMANCE or GAME LOG.
  Section headings on the landing use the display role at clamp(2.75rem, 5.4vw, 4.75rem).
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

**The landing**

- **Pace:** it changes pace on purpose. The hero is followed by a dense board slice, a quiet band on
  the surface step, a chart, a list, and one last ask.
- **Spacing:** sections are separated by 96px of space (128px from lg) or a single hairline.
- **Hero:** a ~7/5 two-column grid from lg. Below that it stacks headline, CTA, then plate.

## Elevation & Depth

Flat at rest. Depth is tonal: the canvas, surface, well and lift steps.

One shadow exists, for things that float (chips, popovers, tooltips). It has an offset and a soft
blur, so it reads as height, never as a glow.

One glow exists: the landing's drawn route, a 5px energy-blue drop-shadow. It stands for the ki trail
the brief asked for, and it appears nowhere else.

### Shadow Vocabulary
- **Float** (`box-shadow: 0 18px 40px -16px rgb(0 0 0 / 0.95), 0 2px 8px -2px rgb(0 0 0 / 0.7)`):
  chips over the plate, the Coming popover, chart tooltips. Chips add a 1px top highlight
  (`inset 0 1px 0 rgb(255 255 255 / 0.08)`).

### Named Rules
**The One Glow Rule.** Only the drawn route glows. The S tier letter is marked by orange alone.

## Shapes

- **Radius:**
  - 0 for structure (rows, tables, sections, rules);
  - 4px for anything you operate (buttons, inputs, the console);
  - 2px for a control nested inside a control;
  - 24px for the one focal plate on a page (the landing's orange plate, the player plate);
  - a full pill only for chips that float over imagery, and for circular marks (avatars, chart end
    markers).
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
  retry, show more, cancel, and the header's join.
- **Quiet link:** Ink text with an Edge underline 4px below; the underline turns blue on hover.
  Destructive actions stay grey until hovered or focused, and then turn danger red.

### Chips
- **Style:** a Surface pill with the float shadow and a top highlight, floating over the landing
  plate's edges.
- **State:** the scoring switch is a pill-shaped radio group. The selected option sits on Lift with an
  energy-blue dot, and each option carries its season total so the three rulesets compare at a glance.

### Cards / Containers
None. The product has no cards. The only containers are the console strip, the focal plates and the
tonal status block.

### Inputs / Fields
- **Style:** a Well fill, a 1px Edge border, 4px corners, 48px tall on forms and 40px (32px from md)
  in the console.
- **Focus:** the border turns Energy Blue, plus the global 2px blue focus ring with a 2px offset. The
  caret is blue.
- **Error:** a tonal danger block (7-8% danger over the page) with danger text. There is no border.

### Navigation
- **Top bar:** the wordmark, then Rankings and Scoring in 14px Mute (the active item in Ink with a 2px
  blue bar at the bar's bottom edge), then "Coming". Coming opens a native popover listing the six
  unbuilt sections; they are never links, and the popover closes on Escape, an outside click, or Tab.
- **Mobile:** below 768px the bar keeps the wordmark and the two live sections, and a `<dialog>` menu
  holds Coming and the account.

### The Console (signature)
Every board control sits on one Surface strip:

- scoring first, since it is the product's argument;
- then season, window, position and find, with hairline seams between groups.

Segments have no track of their own. The selected one sits on Lift with a 2px blue base line. The
strip wraps rather than scrolls.

### The Plate (signature)
- **On the landing:** McCaffrey's real 2025 season as black bars rising on orange, with the route
  drawn through their tops. The chips re-score the season live under 0 PPR, Half PPR or PPR.
- **On a player page:** the cut-out stands on a Surface plate, lit from behind by a tight stage light
  in the position's hue (30%). The name is set at display size and the season line as type, not tiles.

### The Drawn Route (signature)
A season traced as a monotone cubic (`lib/trace.ts`):

- it passes through every week;
- it never overshoots between two weeks;
- it breaks at a bye.

It is drawn on at page load and again on every ruleset switch, and ends in an arrowhead past the last
week. It appears on the landing only.

## Do's and Don'ts

### Do:
- **Do** put every page background on true black (#000000) and separate with Surface, Well and Lift
  only, rising in that order.
- **Do** set page names and segments in the extra-condensed italic caps, and everything operable in the
  regular width at fixed sizes.
- **Do** state a board's recipe in a sentence wherever a ranking appears: ruleset, season, window.
- **Do** keep one orange action per view, and make the header's join secondary.
- **Do** colour a position's letters only, and keep quality to green, ink and faint.
- **Do** measure every new text pair against the surface it sits on, and record it in `globals.css`.

### Don't:
- **Don't** put rows, panels or stat tiles in rounded bordered cards. Rows are lines; a page's
  segments are a heading, a hairline and content.
- **Don't** add a label above a heading, or set labels in tracked capitals.
- **Don't** add a second glow, a gradient wash, glass, or a zero-offset coloured halo.
- **Don't** crop a player's cut-out into a circle at display size, or put any real player's likeness
  on the landing without a promotional licence and the player's consent.
- **Don't** show a metric the product does not compute.
- **Don't** use orange for links or selection, or blue for fills and headings.
