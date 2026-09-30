---
version: 1
slug: "frontend-src-app-site-page-tsx"
primary_target: "frontend/src/app/(site)/page.tsx"
related_targets: ["frontend/src/app/(site)/login/page.tsx","frontend/src/app/(site)/register/page.tsx","frontend/src/app/(site)/layout.tsx"]
---

# Surface brief: the public pages in Daylight, and the product they lead into

Scope: `/` (Persuade), plus `/login` and `/register`, which share its frame. The member surfaces
(`/rankings`, `/players/[id]`, `/profiles`) are Operate and stay Prime time, as DESIGN.md records.
Audience: fantasy managers deciding whether to join. Job: make "your rules decide the order"
intelligible in one viewport, by showing the real board re-sort; one action, join with an email.
Constraints: no real player's likeness on the landing (owner, 2026-09-28, kept 2026-09-29); captured
real data only; nothing unbuilt presented as working; CSP unchanged (fonts self-hosted, images from
a.espncdn.com only); the app's look must not change.

## Direction contract

THESIS: Two modes of one broadcast package. The public pages are Daylight, the pre-game studio:
bright, editorial, the argument made in type and real numbers. The product is Prime time, the night
game under lights. The landing's one dark object is the product itself: the top of the real 2025
board, which re-sorts when the rule changes. Refuses illustration heroes, phone mockups, card grids,
cream-and-serif editorial, and SaaS white with a blue gradient.

OWN-WORLD: Chalk canvas (#f1f4f9, cool, never cream), deep navy ink (#0b1530), white sheets for what
floats or is typed into, electric-blue hairlines at low alpha as structure, energy blue for state,
links, the switch, the followed row and the drawn route, ki orange only for the one action, and a
deeper ki-text where orange is read as a figure. Sofia Sans in three widths, unchanged. Radii
0 / 2 / 4 / 24 / pill. The island: where the product appears it keeps its own dark skin, once.

STORY: A visitor reads RANKED BY YOUR RULES and watches the board beside it re-sort from a quarterback
board (0 PPR) into McCaffrey, Nacua, Robinson (PPR), with arrows saying how far each moved; the card
for McCaffrey reads RB1, 416.6, top-24 in 16 of 17 weeks, his season drawn as a route. They flip to
Half PPR themselves, scroll to Nacua 20th to 2nd drawn out, the receipt that makes 416.6, the tiered
board, a player's weeks and seasons, and join with an email. Signed in, they land in the dark board:
the same plate, now the whole screen.

FIRST VIEWPORT: 1440x900. Header 64px, translucent chalk, wordmark, four section links, Sign in,
secondary Join. Left 6/12: the headline in two lines at ~8.25rem ink italic caps; a 19px lead
between blue hairlines; one joined 56px email control (white field, the orange "Join the Kai" seated
inside it) with a line saying the password comes next, then the fine print. Right 6/12: a black
24px-radius plate ~540x470; a navy console strip with the 0 PPR / Half PPR / PPR switch (sliding
lift thumb, blue dot) and "The top of the 2025 board, by season total."; eight 44px rows (rank,
name and team, position badge, points, movement), McCaffrey's with a 2px blue edge. A white readout
card ~280x170 overlaps the plate's lower-left corner and spills onto the chalk: name, RB1 at display
size, points, per game, starter weeks, 18 mini bars with the blue route drawn through them. On load,
once, the rows stand in 0 PPR order, hold, and travel into PPR order as the thumb slides; numbers
and arrows land last, then the card, then the route. Reduced motion shows the final state.

FORM: Brief-pinned by the owner's 2026-09-30 brief (light public pages, the product as the hero, no
illustration), so no concept seed was rolled: a brief-pinned direction beats the roll. It extends the
2026-09-29 contract's broadcast package (seed 9928ac74, form #1 broadcast graphics, #5 playbook
routes binding the open dimensions) into a second register, and keeps its ritual: the route is drawn
on, and redrawn on every ruleset switch. Code-led: no image generation on this machine.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Open decisions
- ESPN image rights for in-app headshots and cut-outs (owner call, docs/map.md §5).
- The wordmark orb's red star (owner call; the 2026-09-29 critique read it as a Dragon Ball).
