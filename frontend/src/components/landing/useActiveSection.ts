"use client";

import { useEffect, useState } from "react";

/**
 * Which of the landing page's sections is under the middle of the screen, or
 * null when none is. The header underlines it and the phone shows it.
 *
 * One thin band across the middle of the viewport, not a visibility ratio: a
 * tall section and a short one are then judged the same way, and exactly one
 * of them is ever current -- the one being read.
 */
export function useActiveSection(ids: readonly string[]): string | null {
  const [active, setActive] = useState<string | null>(null);

  useEffect(() => {
    const targets = ids
      .map((id) => document.getElementById(id))
      .filter((el): el is HTMLElement => el !== null);
    if (targets.length === 0) return;

    // Any section crossing the band is the cue to look again at all of them,
    // rather than trusting the one entry that crossed: a jump (an anchor, a
    // fast fling) can carry the page past a section without it ever touching
    // the band, and the old per-entry reading then left the hero's link lit
    // three sections down (seen 2026-10-20). Nothing under the band -- above
    // the first section, between named ones, past the last -- is nothing lit:
    // a lit link there points at the wrong place.
    const observer = new IntersectionObserver(
      () => {
        const line = window.innerHeight * 0.45;
        const under = targets.find((el) => {
          const box = el.getBoundingClientRect();
          return box.top <= line && box.bottom > line;
        });
        setActive(under ? under.id : null);
      },
      { rootMargin: "-45% 0px -54% 0px" },
    );
    targets.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [ids]);

  return active;
}

/**
 * Whether any of these elements is on screen, under the sticky header. The
 * site header asks it of the page's own asks: while one of them is in view,
 * the header's join stays secondary. True until measured, so the server's
 * render and the first paint never show two orange buttons at once.
 */
export function useAnyOnScreen(ids: readonly string[]): boolean {
  const [onScreen, setOnScreen] = useState(true);

  useEffect(() => {
    const targets = ids
      .map((id) => document.getElementById(id))
      .filter((el): el is HTMLElement => el !== null);
    if (targets.length === 0) return;

    const visible = new Set<Element>();
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) visible.add(entry.target);
          else visible.delete(entry.target);
        }
        setOnScreen(visible.size > 0);
      },
      // The top 64px is the header itself: an ask scrolled under it is gone.
      { rootMargin: "-64px 0px 0px 0px" },
    );
    targets.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [ids]);

  return onScreen;
}
