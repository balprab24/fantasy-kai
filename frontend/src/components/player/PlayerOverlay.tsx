"use client";

import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useRef } from "react";

const CloseContext = createContext<() => void>(() => {});

/** How the view inside the overlay asks to be closed. */
export function useOverlayClose() {
  return useContext(CloseContext);
}

/**
 * The room off the board: a player's view over the rankings, which stay where
 * they were underneath, dimmed. On a phone it is the whole screen.
 *
 * A native `<dialog>` opened with `showModal()`, which is what makes it
 * correct for free: focus moves into it and cannot Tab out, the page behind is
 * inert to pointer and keyboard alike, and closing it returns focus to the row
 * that opened it. Closing is always the browser's back -- the overlay was
 * reached by a navigation, so back is what undoes it, and the URL and the
 * screen never disagree: Escape, the close button, a click on the dimmed
 * board and the browser's own back button all land on the same board.
 */
export function PlayerOverlay({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const dialog = useRef<HTMLDialogElement>(null);
  const close = useCallback(() => router.back(), [router]);

  useEffect(() => {
    const el = dialog.current;
    if (!el) return;
    // Whatever opened the overlay -- a row's name, a mover -- gets focus back
    // when it closes. The dialog's own return-focus cannot do it: by the time
    // this cleanup runs the dialog has left the document.
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (!el.open) el.showModal();
    // The board under the overlay must not scroll with the wheel meant for the
    // overlay; the gutter keeps the board from shifting sideways when its
    // scrollbar goes.
    const root = document.documentElement;
    const before = { overflow: root.style.overflow, gutter: root.style.scrollbarGutter };
    root.style.scrollbarGutter = "stable";
    root.style.overflow = "hidden";
    return () => {
      root.style.overflow = before.overflow;
      root.style.scrollbarGutter = before.gutter;
      if (el.open) el.close();
      requestAnimationFrame(() => {
        if (opener?.isConnected) opener.focus({ preventScroll: true });
      });
    };
  }, []);

  return (
    <CloseContext.Provider value={close}>
      <dialog
        ref={dialog}
        aria-labelledby="player-name"
        // Escape: let the navigation close it, so history stays in step.
        onCancel={(e) => {
          e.preventDefault();
          close();
        }}
        // A click on the dimmed board lands on the dialog element itself; a
        // click anywhere inside the view lands on a descendant.
        onClick={(e) => {
          if (e.target === e.currentTarget) close();
        }}
        className="player-overlay fixed inset-0 m-0 h-dvh max-h-none w-full max-w-none overflow-y-auto overscroll-contain bg-canvas text-ink backdrop:bg-scrim md:inset-x-0 md:top-6 md:mx-auto md:h-[calc(100dvh-1.5rem)] md:w-[min(1600px,calc(100vw-5rem))] md:rounded-t-plate md:border md:border-b-0 md:border-line md:shadow-float"
      >
        <div className="min-h-full">{children}</div>
      </dialog>
    </CloseContext.Provider>
  );
}
