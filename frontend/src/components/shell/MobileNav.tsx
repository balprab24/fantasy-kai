"use client";

import { useRef } from "react";
import { Icon } from "@/components/ui/Icon";
import { AccountBlock, NavLists, Wordmark } from "./Sidebar";

/**
 * Below md: a slim top bar, and the full navigation in a drawer.
 *
 * The drawer is a native `<dialog>` opened with `showModal()`, which is what
 * makes it correct for free: focus moves into it and cannot Tab out, Escape
 * closes it, the page behind becomes inert, and focus returns to the menu
 * button on close. Every one of those is a bug to write by hand.
 */
export function MobileNav() {
  const dialog = useRef<HTMLDialogElement>(null);
  const close = () => dialog.current?.close();

  return (
    <>
      <div className="sticky top-0 z-20 flex h-13 items-center justify-between border-b border-line bg-void/95 px-4 backdrop-blur md:hidden">
        <Wordmark href="/rankings" />
        <button
          type="button"
          onClick={() => dialog.current?.showModal()}
          aria-haspopup="dialog"
          className="-mr-2 flex size-11 items-center justify-center rounded-md text-mute hover:text-ink"
        >
          <Icon name="menu" />
          <span className="sr-only">Open navigation</span>
        </button>
      </div>

      <dialog
        ref={dialog}
        aria-label="Navigation"
        // A click on the backdrop lands on the dialog element itself; a click
        // on anything inside it lands on a descendant.
        onClick={(e) => {
          if (e.target === e.currentTarget) close();
        }}
        className="m-0 h-dvh max-h-none w-[min(280px,85vw)] max-w-none flex-col bg-void text-ink backdrop:bg-black/60 open:flex"
      >
        <div className="flex h-13 items-center justify-between border-b border-line px-4">
          <Wordmark href="/rankings" />
          <button
            type="button"
            onClick={close}
            className="-mr-2 flex size-11 items-center justify-center rounded-md text-mute hover:text-ink"
          >
            <Icon name="close" />
            <span className="sr-only">Close navigation</span>
          </button>
        </div>
        <NavLists onNavigate={close} />
        <div className="mt-auto border-t border-line p-2">
          <AccountBlock onNavigate={close} />
        </div>
      </dialog>
    </>
  );
}
