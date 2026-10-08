"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { BUTTON_JOIN, LINK_QUIET } from "@/components/ui/buttons";
import { useAuth } from "@/lib/auth";
import { authErrorMessage, isEmailTaken } from "@/lib/authErrors";
import { HOME_FOR_MEMBERS } from "@/lib/landing";

/**
 * Joining, starting from nothing but an email.
 *
 * An account is an email and a password (north-star §2), so this is the same
 * sign-up as `/register`, asked one field at a time: the email first, because
 * it is the only thing a visitor has to decide, and then the password (owner
 * decision, 2026-09-28). Both fields are on screen from the start -- the
 * password one waiting, disabled, until the email is in -- because a lone
 * email field and a button is the shape of a newsletter signup, and this is an
 * account. Nothing leaves the page until both exist -- the email is never put
 * in a URL, where it would sit in history and in access logs.
 *
 * Validation is the browser's own (`type="email"`, `minLength`) and then the
 * server's, whose wording is shown as it is (`authErrorMessage`).
 */
export function EmailStart() {
  const router = useRouter();
  const { register } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [step, setStep] = useState<"email" | "password">("email");
  const [error, setError] = useState<string | null>(null);
  const [taken, setTaken] = useState(false);
  const [busy, setBusy] = useState(false);
  const uid = useId();
  const emailId = `${uid}-email`;
  const passwordId = `${uid}-password`;
  const hintId = `${uid}-hint`;

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setTaken(false);
    if (step === "email") {
      // The password field remounts enabled and takes focus as it mounts
      // (`autoFocus`), which needs no frame to have been painted first.
      setStep("password");
      return;
    }
    setBusy(true);
    try {
      await register(email.trim(), password);
      router.push(HOME_FOR_MEMBERS);
    } catch (e) {
      setTaken(isEmailTaken(e));
      setError(authErrorMessage(e));
      setBusy(false);
    }
  }

  const open = step === "password";
  const field =
    "mt-1.5 h-14 w-full min-w-0 rounded-control border border-line-strong bg-well px-4 text-[16px] text-ink transition-colors placeholder:text-faint hover:border-mute focus-visible:border-energy disabled:cursor-not-allowed disabled:bg-canvas disabled:hover:border-line-strong";

  return (
    <form onSubmit={submit} className="w-full max-w-[60rem]">
      {/* From lg the two fields and the button share one row: one decision, on one line. */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] lg:items-end">
        <div>
          {/* The same label row as the password's, so the two fields share a top edge. */}
          <div className="flex items-baseline justify-between gap-3 text-[13px]">
            <label htmlFor={emailId} className="font-semibold text-ink">
              Email
            </label>
          </div>
          <input
            id={emailId}
            type="email"
            required
            // The account's identifier, for password managers: with a password
            // field in the same form from the start, "username" is what ties the
            // saved credential to this address.
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={field}
          />
        </div>
        <div>
          <div className="flex items-baseline justify-between gap-3 text-[13px]">
            <label htmlFor={passwordId} className="font-semibold text-ink">
              Password
            </label>
            <span id={hintId} className="text-mute">
              {open ? "12 characters or more" : "after your email"}
            </span>
          </div>
          <input
            // Remounted when it opens, so `autoFocus` hands it the focus the
            // visitor's own "Join the Kai" asked for; it never fires on load.
            key={open ? "open" : "waiting"}
            autoFocus={open}
            id={passwordId}
            type="password"
            disabled={!open}
            required={open}
            minLength={12}
            maxLength={128}
            autoComplete="new-password"
            aria-describedby={hintId}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={field}
          />
        </div>
        <button
          type="submit"
          disabled={busy}
          className={`${BUTTON_JOIN} mt-1 w-full sm:w-auto sm:justify-self-start lg:mt-0`}
        >
          {open ? (busy ? "Creating…" : "Create account") : "Join the Kai"}
        </button>
      </div>

      <p className="mt-4 text-sm text-mute">
        Already a member?{" "}
        <Link href="/login" className={`whitespace-nowrap ${LINK_QUIET}`}>
          Sign in
        </Link>
      </p>

      {error && (
        <p role="alert" className="mt-3 rounded-control bg-danger/[0.08] px-3 py-2 text-sm text-danger">
          {error}
          {taken && (
            <>
              {" "}
              <Link href="/login" className="font-medium text-ink underline underline-offset-2">
                Sign in instead
              </Link>
            </>
          )}
        </p>
      )}
    </form>
  );
}
