"use client";

import { useRef } from "react";
import { Icon } from "@/components/ui/Icon";
import { AccountButton } from "./AccountButton";
import { COMING } from "./nav";

/**
 * Below md the bar keeps the wordmark and the two live sections; this menu
 * carries the rest -- what is coming, and the account.
 *
 * A native `<dialog>` opened with `showModal()`, which is what makes it
 * correct for free: focus moves into it and cannot Tab out, Escape closes it,
 * the page behind becomes inert, and focus returns to the menu button on
 * close. Every one of those is a bug to write by hand.
 */
export function MobileNav() {
  const dialog = useRef<HTMLDialogElement>(null);
  const close = () => dialog.current?.close();

  return (
    <>
      <button
        type="button"
        onClick={() => dialog.current?.showModal()}
        aria-haspopup="dialog"
        className="-mr-2 flex size-11 items-center justify-center rounded-control text-mute hover:text-ink"
      >
        <Icon name="menu" />
        <span className="sr-only">Open menu</span>
      </button>

      <dialog
        ref={dialog}
        aria-label="Menu"
        // A click on the backdrop lands on the dialog element itself; a click
        // on anything inside it lands on a descendant.
        onClick={(e) => {
          if (e.target === e.currentTarget) close();
        }}
        className="m-0 ml-auto h-dvh max-h-none w-[min(320px,86vw)] max-w-none flex-col bg-surface text-ink backdrop:bg-black/70 open:flex"
      >
        <div className="flex h-14 items-center justify-between px-4">
          <span className="type-heading text-ink">Menu</span>
          <button
            type="button"
            onClick={close}
            className="-mr-2 flex size-11 items-center justify-center rounded-control text-mute hover:text-ink"
          >
            <Icon name="close" />
            <span className="sr-only">Close menu</span>
          </button>
        </div>
        <section aria-labelledby="coming-title" className="px-4 pt-2">
          <h2 id="coming-title" className="type-label text-mute">
            Planned. None of these is built yet.
          </h2>
          <ul className="mt-2">
            {COMING.map((item) => (
              <li key={item.label} className="flex h-11 items-center gap-3 text-base text-mute">
                <Icon name={item.icon} size={20} className="text-faint" />
                {item.label}
              </li>
            ))}
          </ul>
        </section>
        <div className="mt-auto border-t border-line p-2">
          <AccountButton block onNavigate={close} />
        </div>
      </dialog>
    </>
  );
}
