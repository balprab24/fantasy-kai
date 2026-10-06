"use client";

import { useEffect, useState } from "react";

/**
 * Which of the landing page's sections is under the middle of the screen, or
 * null above the first one. The header underlines it and the phone shows it.
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

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) setActive(entry.target.id);
          // Scrolling back above the first section leaves nothing current, and so
          // does scrolling on past the last: the page has gone on to parts the
          // header does not name, and a lit link there points at the wrong place.
          else if (entry.target === targets[0] && entry.boundingClientRect.top > 0) setActive(null);
          else if (entry.target === targets[targets.length - 1] && entry.boundingClientRect.top < 0)
            setActive(null);
        }
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
