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
hero plate. Navy ink, energy-blue hairlines at low alpha, blue for state, data, the story line and
the lit row; ki orange only for the one action in view, and as ki-text for a season total. Sofia
Sans in three widths; the extra-condensed italic caps as the voice. Radii 0 / 2 / 4 / 24 / pill.

STORY: A visitor reads RANKED BY YOUR RULES and "fantasy football rankings scored by the settings
your league actually uses", and beside it one player's season scored three ways, big enough to read
in a glance: McCaffrey #7 / #1 / #1, 314.6 / 365.6 / 416.6 under 0 PPR / Half PPR / PPR. The three
formats are also the switch, and the top of the board under them turns from five quarterbacks (0
PPR) into McCaffrey, Nacua, Robinson (PPR). One rule, three boards: the three boards side by side,
and one blue line carrying Nacua from 20th to 11th to 2nd. Then the cut to the product: the tiered
board with Nacua's row lit, and his season opened beside it. Then "your league pays [0 | 0.5 | 1] a
catch": week 7 as 32.1 + 7 x the rate, and the season as seventeen bars whose blue caps grow and
shrink with it, 416.6. What is coming, in a short rundown. Join.

FIRST VIEWPORT: 1440x900. Header 64px on the paper veil: wordmark, Scoring, Rankings, Players,
Sign in, a secondary Join. Left 5/12: the headline in two lines at ~7.5rem navy italic caps; a 19px
lead naming fantasy football; a 56px orange "Join the Kai" and "Free. No ads, no paywall." Right
7/12: the black 24px-radius plate, 695x643 measured -- McCaffrey's name in the display voice, the three
stops (format, rate, overall rank at display size, points, RB rank, the gain), and six 40px rows of
the board with movement. At 390: headline, lead, full-width CTA, then the plate's name and its
three stops inside the first screen.

FORM: Brief-pinned by the owner's 2026-10-05 brief (light, product-led, rhythm through bands, one or
two dark product moments, both accents in the identity), so no concept seed was rolled: a
brief-pinned direction beats the roll. It extends the 2026-09-30 Daylight contract (itself from seed
9928ac74, broadcast graphics) and keeps its ritual: the board re-sorts once on load and the route
is redrawn on every switch. Code-led: no image generation on this machine.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Open decisions
- ESPN image rights for in-app headshots and cut-outs (owner call, docs/map.md §5).
- The wordmark orb's red star (owner call; the 2026-09-29 critique read it as a Dragon Ball).
