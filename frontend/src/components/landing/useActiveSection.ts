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
          // Scrolling back above the first section leaves nothing current.
          else if (entry.target === targets[0] && entry.boundingClientRect.top > 0) setActive(null);
        }
      },
      { rootMargin: "-45% 0px -54% 0px" },
    );
    targets.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [ids]);

  return active;
}
