"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useRef, useState } from "react";
import { BUTTON_PRIMARY, LINK_QUIET } from "@/components/ui/buttons";
import { useAuth } from "@/lib/auth";
import { authErrorMessage, isEmailTaken } from "@/lib/authErrors";
import { HOME_FOR_MEMBERS } from "@/lib/landing";

/**
 * Joining, starting from nothing but an email.
 *
 * An account is an email and a password (north-star §2), so this is the same
 * sign-up as `/register`, asked one field at a time: the email first, because
 * it is the only thing a visitor has to decide, and then a password field
 * opens in place. Nothing leaves the page until both exist -- the email is
 * never put in a URL, where it would sit in history and in access logs.
 *
 * Validation is the browser's own (`type="email"`, `minLength`) and then the
 * server's, whose wording is shown as it is (`authErrorMessage`).
 */
export function EmailStart({ fieldId, tone = "hero" }: { fieldId?: string; tone?: "hero" | "footer" }) {
  const router = useRouter();
  const { register } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [step, setStep] = useState<"email" | "password">("email");
  const [error, setError] = useState<string | null>(null);
  const [taken, setTaken] = useState(false);
  const [busy, setBusy] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const uid = useId();
  const emailId = fieldId ?? `${uid}-email`;
  const passwordId = `${uid}-password`;
  const hintId = `${uid}-hint`;

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setTaken(false);
    if (step === "email") {
      // The password field takes focus as it mounts (`autoFocus`), which
      // needs no frame to have been painted first.
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

  return (
    <form onSubmit={submit} className="w-full max-w-md">
      <div className="flex flex-col gap-2 sm:flex-row">
        <label htmlFor={emailId} className="sr-only">
          Email
        </label>
        <input
          ref={emailRef}
          id={emailId}
          type="email"
          required
          autoComplete="email"
          placeholder="you@example.com"
          value={email}
          readOnly={open}
          onChange={(e) => setEmail(e.target.value)}
          className={`h-12 w-full min-w-0 rounded-control border border-line-strong bg-well px-4 text-[15px] text-ink transition-colors placeholder:text-faint hover:border-mute focus-visible:border-energy sm:flex-1 ${
            open ? "text-mute" : ""
          }`}
        />
        {!open && (
          <button type="submit" className={`${BUTTON_PRIMARY} h-12`}>
            Join the Kai
          </button>
        )}
      </div>

      {/* The password row opens in place: 0fr to 1fr is a height animation
          with no measuring. It answers the click, so it is motion that says
          what changed, not decoration. */}
      <div
        className={`grid motion-safe:transition-[grid-template-rows] motion-safe:duration-300 motion-safe:ease-out ${
          open ? "grid-rows-[1fr]" : "grid-rows-[0fr]"
        }`}
      >
        <div className="overflow-hidden">
          {open && (
            <div className="pt-3">
              <div className="flex items-center justify-between text-sm">
                <label htmlFor={passwordId} className="text-mute">
                  Choose a password
                </label>
                <button
                  type="button"
                  onClick={() => {
                    setStep("email");
                    setPassword("");
                    setError(null);
                    setTaken(false);
                    emailRef.current?.focus();
                  }}
                  className="text-energy-text underline decoration-energy/40 underline-offset-4 hover:decoration-energy-text"
                >
                  Change email
                </button>
              </div>
              <div className="mt-1.5 flex flex-col gap-2 sm:flex-row">
                <input
                  // Only ever mounted by the visitor's own "Join the Kai",
                  // so taking focus here follows their action; it never
                  // fires on page load.
                  autoFocus
                  id={passwordId}
                  type="password"
                  required
                  minLength={12}
                  maxLength={128}
                  autoComplete="new-password"
                  aria-describedby={hintId}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="h-12 w-full min-w-0 rounded-control border border-line-strong bg-well px-4 text-[15px] text-ink transition-colors hover:border-mute focus-visible:border-energy sm:flex-1"
                />
                <button type="submit" disabled={busy} className={`${BUTTON_PRIMARY} h-12`}>
                  {busy ? "Creating…" : "Create account"}
                </button>
              </div>
              <p id={hintId} className="mt-1.5 text-sm text-mute">
                At least 12 characters.
              </p>
            </div>
          )}
        </div>
      </div>

      {error && (
        <p
          role="alert"
          className="mt-3 rounded-control bg-danger/[0.08] px-3 py-2 text-sm text-danger"
        >
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

      <p className="mt-3 text-sm text-mute">
        {/* The band at the foot of the page says "free" in its own words. */}
        {tone === "hero" && "Free, with no ads and nothing to upgrade to. "}
        Already a member?{" "}
        <Link href="/login" className={`whitespace-nowrap ${LINK_QUIET}`}>
          Sign in
        </Link>
      </p>
    </form>
  );
}
