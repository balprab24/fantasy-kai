"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { BUTTON_PRIMARY, LINK_QUIET } from "@/components/ui/buttons";
import { useAuth } from "@/lib/auth";
import { authErrorMessage } from "@/lib/authErrors";
import { HOME_FOR_MEMBERS } from "@/lib/landing";
import { useNextParam } from "@/lib/useNextParam";

/**
 * Register and sign in differ by one call and one sentence, so they share a
 * component rather than duplicating a form and drifting apart.
 *
 * `next` is where the account gate was sent from (`RequireAccount`). It comes
 * through `useNextParam`, which reads it without `useSearchParams` (these
 * routes are prerendered) and only ever through `safeNext`, because anyone can
 * write it.
 */
export function AuthForm({ mode }: { mode: "sign-in" | "register" }) {
  const router = useRouter();
  const { status, signIn, register } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const next = useNextParam();

  const registering = mode === "register";

  // Already a member: this page has nothing to offer, so go where they were headed.
  useEffect(() => {
    if (status === "signed-in" && !busy) router.replace(next ?? HOME_FOR_MEMBERS);
  }, [status, busy, next, router]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await (registering ? register(email, password) : signIn(email, password));
      router.push(next ?? HOME_FOR_MEMBERS);
    } catch (e) {
      setError(authErrorMessage(e));
      setBusy(false);
    }
  }

  const carry = next ? `?next=${encodeURIComponent(next)}` : "";

  return (
    <div className="mx-auto max-w-sm px-4 pt-16 pb-8 sm:pt-24">
      <h1 className="type-display text-[3.25rem] text-ink">{registering ? "Join the Kai" : "Sign in"}</h1>
      <p className="mt-3 text-[15px] leading-relaxed text-mute">
        {registering
          ? "Free, with no ads and nothing to upgrade to. An email and a password is all it takes."
          : "Sign in to open the rankings, player pages and the scoring you saved."}
      </p>

      <form onSubmit={submit} className="mt-8 space-y-4">
        <label className="block">
          <span className="text-sm text-mute">Email</span>
          <input
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="mt-1.5 h-12 w-full rounded-control border border-line-strong bg-well px-4 text-[15px] text-ink transition-colors hover:border-mute focus-visible:border-energy"
          />
        </label>

        <label className="block">
          <span className="text-sm text-mute">Password</span>
          <input
            type="password"
            required
            minLength={registering ? 12 : undefined}
            maxLength={128}
            autoComplete={registering ? "new-password" : "current-password"}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1.5 h-12 w-full rounded-control border border-line-strong bg-well px-4 text-[15px] text-ink transition-colors hover:border-mute focus-visible:border-energy"
          />
          {registering && (
            <span className="mt-1 block text-sm text-mute">At least 12 characters.</span>
          )}
        </label>

        {error && (
          <p role="alert" className="rounded-control bg-danger/[0.08] px-3 py-2 text-sm text-danger">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={busy}
          className={`${BUTTON_PRIMARY} h-12 w-full`}
        >
          {busy ? "Working…" : registering ? "Create account" : "Sign in"}
        </button>
      </form>

      <p className="mt-6 text-sm text-mute">
        {registering ? "Already a member? " : "Not a member yet? "}
        <Link
          href={(registering ? "/login" : "/register") + carry}
          className={LINK_QUIET}
        >
          {registering ? "Sign in" : "Join the Kai"}
        </Link>
      </p>
    </div>
  );
}
