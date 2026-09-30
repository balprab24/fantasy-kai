---
version: 1
slug: "frontend-src-app-site-page-tsx"
primary_target: "frontend/src/app/(site)/page.tsx"
related_targets: ["frontend/src/app/(app)/rankings/page.tsx","frontend/src/app/(app)/players/[id]/page.tsx","frontend/src/app/(app)/layout.tsx"]
---

# Surface brief: the landing page, and the product world it sets

Scope: `/` (Persuade). The same world governs the member surfaces (`/rankings`, `/players/[id]`,
`/profiles`, sign-in), which are Operate: dense, familiar, brand only in precise details.
Audience: fantasy managers deciding whether to join (landing) and working their board (app).
Job: make "your rules decide the order" intelligible in one viewport; one action, join with an email.
Constraints: no real player's likeness on the landing (owner, 2026-09-29); captured real data only;
nothing unbuilt presented as working; CSP (fonts self-hosted, images from a.espncdn.com only).

## Direction contract

THESIS: A prime-time broadcast package where the analysis is drawn live. Your rules decide the
order; the page proves it by re-drawing a real season when the rules change. Refuses the category
default: rounded-card dashboards, a rail of dead links, a phone mockup beside feature paragraphs.

OWN-WORLD: True black stage; blue-black steps only where separation works (surface, well, lift).
One ki-orange plate per page as the focal field. Electric blue is "you are here" and the one drawn
route. Sofia Sans in three widths: extra-condensed heavy italic caps lead; semi-condensed labels
and ranks; regular UI with clean tabular figures. Hairlines, not boxes. Radii: 0 / 4 / 24 / pill.

STORY: A visitor sees McCaffrey's 2025 rise in black bars on an orange plate, reads RANKED BY YOUR
RULES, flips the ruleset chip and watches the route re-draw and his rank move 7th to 1st, trusts the
arithmetic below, and joins with an email. A member works a board of player lines, switches scoring,
watches rows settle, and opens a scouter report led by the cut-out.

FIRST VIEWPORT: 1440x900. Minimal bar. Left ~7/12: two-line display headline (orb full stop) at
~8rem, hairline, 19px lead, hairline, email field + "Get started free" (the only orange button),
fine print. Right ~5/12: 24px-radius ki plate ~420x540, player line in black at top, 18 week slots
of black bars from the base (bye as a gap), the electric-blue route through the bar tops running
past both edges with an arrowhead at season's end; four pill chips straddling the plate edges:
live ruleset switch, RB rank + points, overall rank with movement, starter weeks.

FORM: brief-pinned broadcast graphics package (my #1 of: broadcast graphics, draft-board war room,
trading card, the field, playbook routes, scouter HUD, box-score agate). Seed 9928ac74 assigned #5,
playbook routes; the owner's approved plan pins #1, so #5 binds the open dimensions: the energy line
is a telestrated route drawn on, re-drawn on every ruleset switch (ritual), and rank movement is
shown as routes from one rank to another (state vocabulary).
Raises (verdicts: all six challengers declined, losing both audience identification and clarity):
- Teletext (declined), raise: one lattice. Every figure sits on a shared tabular column grid; no
  free-floating numbers, and one reserved colour for live figures (energy).
- WebGL portal (declined), raise: total commitment to one focal element per first viewport; nothing
  else competes with the plate and the headline.
- Dark developer console (declined), raise: one primary accent per view, and destructive actions
  isolated by space and kept quiet until focused (Delete on the scoring page).
- Silkscreen loft (declined), raise: show the variations side by side. The hero's switch carries all
  three season totals at once, the chosen one lit, so comparison is visible, not only sequential.
- Jacquard brocade (declined), raise: full traceability. The hero's total leads to the receipt that
  derives it; every in-app total leads to the games that produced it.
- Kiln glaze shelf (declined), raise: every ranked row keeps its recipe beside it; the board always
  says which ruleset, season and window produced it.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Open decisions
- ESPN image rights for in-app headshots and cut-outs (owner call, docs/map.md §5).

## Amended by the build

The contract above is what was planned. Four things were built differently, and DESIGN.md records
the built system:
- The headline ends on a plain full stop. The orb as its full stop was dropped in the refinement
  pass; the wordmark's orb stays and is an open owner call (the critique flagged its red star).
- The hero button reads "Join the Kai", the same words as every other join, not "Get started free".
- A fifth radius exists: 2px, for a control nested inside a control (a segment in its track).
- Below lg the ruleset switch sits inside the plate's top band and the readouts sit under the
  plate, rather than straddling its edges.
