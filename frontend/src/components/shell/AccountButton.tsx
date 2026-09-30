"use client";

import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import { useAuth } from "@/lib/auth";

/**
 * Sign out, or sign in. `block` is the mobile menu's full-width row; otherwise
 * it is a quiet control at the end of the bar.
 */
export function AccountButton({ block = false, onNavigate }: { block?: boolean; onNavigate?: () => void }) {
  const { status, signOut } = useAuth();
  const shape = block
    ? "flex h-12 w-full items-center gap-3 px-3 text-base"
    : "flex h-9 items-center gap-2 px-2 text-sm";
  const look = `${shape} rounded-control text-mute transition-colors hover:text-ink`;

  if (status === "restoring") {
    // Deliberately blank rather than "Sign in": the refresh cookie is still
    // being traded for a token, and flashing the signed-out state at a
    // signed-in user on every reload is a worse lie than a gap.
    return <div className={block ? "h-12" : "h-9 w-24"} aria-hidden />;
  }

  if (status === "signed-in") {
    // A full load of `/`, not a router push: the account gate is already
    // replacing this page with `/login` the moment status flips, and two
    // client navigations race. A hard load also leaves nothing of the session
    // in memory -- the refresh cookie was revoked by the call that just returned.
    // `finally`: signOut clears local state even when the logout call fails,
    // and then rethrows -- which must not strand a signed-out user on a
    // members' page waiting for a redirect the throw skipped.
    const leave = async () => {
      try {
        await signOut();
      } finally {
        window.location.replace("/");
      }
    };
    return (
      <button type="button" onClick={() => void leave()} className={look}>
        <Icon name="account" size={block ? 22 : 18} />
        <span>Sign out</span>
      </button>
    );
  }

  return (
    <Link href="/login" onClick={onNavigate} className={look}>
      <Icon name="account" size={block ? 22 : 18} />
      <span>Sign in</span>
    </Link>
  );
}
