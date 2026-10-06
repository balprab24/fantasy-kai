"use client";

import { useId, useRef } from "react";
import { Icon } from "@/components/ui/Icon";
import { COMING } from "./nav";

/**
 * Everything not built yet, behind one entry in the bar. A native popover:
 * the browser gives it the top layer (so no container clips it), Escape and a
 * click outside to close, and focus management -- every one of those is a bug
 * to write by hand. Anchored under its button where the browser supports CSS
 * anchor positioning; elsewhere it opens as a small centred panel, which still
 * works.
 */
export function ComingMenu() {
  const id = useId();
  const popover = `coming-${id.replace(/:/g, "")}`;
  const button = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);

  // A popover closes on Escape and on a click outside, but not when Tab moves
  // focus past it -- it stayed open over the controls the keyboard had moved
  // on to. Close it when focus lands anywhere else. (A null target is a click
  // on the panel's own text or the window losing focus: light dismiss already
  // covers the outside click, so those leave it alone.)
  const closeIfLeft = (next: EventTarget | null) => {
    const el = panel.current;
    if (!el?.matches(":popover-open") || !(next instanceof Node)) return;
    if (el.contains(next) || button.current?.contains(next)) return;
    el.hidePopover();
  };

  return (
    <>
      <button
        ref={button}
        type="button"
        popoverTarget={popover}
        onBlur={(e) => closeIfLeft(e.relatedTarget)}
        className="coming-anchor flex h-14 items-center gap-1.5 px-3 text-sm text-mute transition-colors hover:text-ink"
      >
        Coming
        <Icon name="chevronDown" size={14} className="text-faint" />
      </button>
      <div
        ref={panel}
        id={popover}
        popover="auto"
        onBlur={(e) => closeIfLeft(e.relatedTarget)}
        className="coming-popover w-64 rounded-control bg-surface p-2 text-ink shadow-[inset_0_1px_0_rgb(255_255_255/0.06),var(--shadow-float)]"
      >
        <p className="px-2 pt-1 pb-2 text-sm text-mute">Planned. None of these is built yet.</p>
        <ul>
          {COMING.map((item) => (
            <li key={item.label} className="flex h-9 items-center gap-3 px-2 text-sm text-mute">
              <Icon name={item.icon} size={18} className="text-faint" />
              {item.label}
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
