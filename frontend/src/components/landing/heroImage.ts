/**
 * The hero's photograph of a real player -- empty until one is licensed.
 *
 * The owner wants a real running back here (McCaffrey or Robinson). That
 * takes two permissions, and a photo that clears only one is not usable:
 *
 *  1. Copyright. An NFL game photo belongs to whoever shot it (Getty, AP,
 *     Imagn, the team). Their standard licence is *editorial*, which excludes
 *     exactly this -- promoting a product. A Creative Commons photo clears
 *     copyright and nothing else.
 *  2. Publicity. A player's likeness selling sign-ups implies the player
 *     endorses the product. That needs the player's consent (NFLPA group
 *     licensing through OneTeam, or the player's representation).
 *
 * Until both exist, the hero shows no one: its focal object is a real season
 * drawn as data (`HeroPlate`), which needs no likeness at all. Supplying one is
 * the whole swap: put a cut-out (transparent background) in `public/hero/`,
 * fill this in, and it stands in front of the plate's bars; `credit` is owed
 * wherever the licence requires it shown. docs/map.md §5 carries the call.
 */
export interface HeroImage {
  /** Under `public/`, e.g. "/hero/runner.webp". */
  src: string;
  /** Who it is and what they are doing, for a screen reader. */
  alt: string;
  /** Photographer and agency, exactly as the licence requires them shown. */
  credit: string;
  width: number;
  height: number;
}

export const HERO_IMAGE: HeroImage | null = null;
