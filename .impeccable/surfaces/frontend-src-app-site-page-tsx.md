---
version: 2
slug: "frontend-src-app-site-page-tsx"
primary_target: "frontend/src/app/(site)/page.tsx"
related_targets: ["frontend/src/app/(site)/login/page.tsx","frontend/src/app/(site)/register/page.tsx","frontend/src/app/(site)/layout.tsx"]
---

# Surface brief: the public pages, on the product's own ground

Scope: `/` (Persuade), plus `/login` and `/register`, which share its frame. Since 2026-10-19 they
stand on the product's night ground (DESIGN.md, "One ground"); the Daylight brief this replaces is in
git history.
Audience: fantasy managers deciding whether to join. Job: make "your league's rules decide the
order" intelligible in one viewport by letting the visitor turn two of a league's settings and watch
the real board re-sort, then show why (one game priced three ways), then what a member opens (one
player's season), then that a saved ruleset scores everything; ask once more. One action, "Join the
Kai".
Constraints: no real player's likeness (owner, 2026-09-28, kept); captured real data only
(`heroData.ts`, `scripts/capture-landing.mjs`); nothing unbuilt presented as working; CSP unchanged.

## Direction contract

THESIS: The product's front room. The hero is the product -- the season's board with two dials --
and every section below is a different piece of the same argument in its own composition (5:7,
7:5 mirrored, a heading row over one full-width showcase, 5:6, full width). Refuses illustration
heroes, phone mockups, feature-card grids, social proof, stat tiles floating over the product, and
one long undifferentiated sheet.

OWN-WORLD: Night (#060a12) ground, surface panels with one hairline, the logo's two strokes enormous
and nearly the colour of the night behind the hero; Sofia Sans in three widths, the extra-condensed
heavy italic as the voice; ki orange only for the one action in view; energy blue for what is chosen.

MOTION: the mixer's rows travel when a dial turns; it turns one dial by itself, once, when first in
view, never under reduced motion. Nothing else moves on its own.
