---
version: 1
slug: "frontend-src-app-site-page-tsx"
primary_target: "frontend/src/app/(site)/page.tsx"
related_targets: ["frontend/src/app/(site)/login/page.tsx","frontend/src/app/(site)/register/page.tsx","frontend/src/app/(site)/layout.tsx"]
---

# Surface brief: the public pages in Daylight, and the product they lead into

Scope: `/` (Persuade), plus `/login` and `/register`, which share its frame and its tokens. The
member surfaces (`/rankings`, `/players/[id]`, `/profiles`) are Operate and stay Prime time, as
DESIGN.md records.
Audience: fantasy managers, often arriving from search, deciding whether to join. Job: make "your
league's rules decide the order" intelligible in one viewport by showing the real board re-sort,
then walk the product itself -- the board, a player, how one number is made -- and ask once more.
One action, "Join the Kai".
Constraints: no real player's likeness on the landing (owner, 2026-09-28, kept since); captured real
data only; nothing unbuilt presented as working; CSP unchanged (fonts self-hosted, images from
a.espncdn.com only); the app's look must not change.

## Direction contract

THESIS: The studio show. Daylight is the pre-game studio -- bright, editorial, the argument made in
type and real numbers -- and it cuts to the game feed once: one full-width band of Prime time where
the real product plays, then back to daylight. Refuses illustration heroes, phone mockups, feature
card grids, social proof, cream-and-serif editorial, and one long undifferentiated sheet.

OWN-WORLD: Paper ground (#fbfaf7, a white with the faintest warmth, never cream); one cool band
(#edf1f7) for the alternating section and the footer; the stage (#000) for the product band and the
hero's product window. Navy ink, energy-blue hairlines at low alpha, blue for state, data and the lit row; ki orange only for the one action in view, and as ki-text for a season total. Sofia
Sans in three widths; the extra-condensed italic caps as the voice. Radii 0 / 2 / 4 / 24 / pill.

STORY (owner brief 2026-10-07: each section a different part of the product story): a visitor
reads RANKED BY YOUR RULES and "fantasy football rankings and player pages, scored by your league's
exact settings", and beside it the product itself -- the 2026 rankings board under a member's own
ruleset, "My league · Half PPR · 6-pt passing TDs", its moves counted vs PPR, its scoring switch
live, re-sorting itself to PPR and back (still under reduced motion): your league's rules rewrite
the rankings. One board, three rulesets: what a catch is worth, alone -- the 2025 board, which the
visitor switches 0 PPR / Half PPR / PPR, Nacua followed from 20th to 11th to 2nd. Then the cut to
the product: Nacua opened -- plate, weekly chart, game log -- with the player page's own scoring
switch live and week 4 (13 catches) lit. Join, with what is coming in one line.

FIRST VIEWPORT: 1440x900. Header 64px on the paper veil: wordmark, Rankings, Players,
Sign in, a secondary Join. Left 37%: the headline in two lines at ~6.6rem navy italic caps; a 19px
lead that names the product; a 56px orange "Join the Kai" with "Free account. No ads, no paywall.";
a quiet anchor to the board. Right 63%: the black product window, 624px tall, its ground to the
browser's edge and its content on the page's right edge -- RANKINGS, "Scored under My league · Half PPR · 6-pt passing TDs", the live scoring switch,
tier S (Allen, Purdy) and the A tier with their moves vs PPR, which is the poster -- and "2026 rankings · through Week 4"
under it, from the rows, with the Pause / Play control at its end. At 1024 the same split, five
rows in view and a sixth under the fade. At 390: headline, lead, full-width CTA, then the window with its first two rows in view.

FORM: Brief-pinned by the owner's 2026-10-05 brief (light, product-led, rhythm through bands, one or
two dark product moments, both accents in the identity), so no concept seed was rolled: a
brief-pinned direction beats the roll. It extends the 2026-09-30 Daylight contract (itself from seed
9928ac74, broadcast graphics) and keeps its ritual: the board re-sorts once on load and the route
is redrawn on every switch. Code-led: no image generation on this machine.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Open decisions
- ESPN image rights for in-app headshots and cut-outs (owner call, docs/map.md §5).
- The wordmark orb's red star (owner call; the 2026-09-29 critique read it as a Dragon Ball).
