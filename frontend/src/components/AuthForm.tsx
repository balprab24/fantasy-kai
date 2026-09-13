"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";

/**
 * Register and sign in differ by one call and one sentence, so they share a
 * component rather than duplicating a form and drifting apart.
 */
export function AuthForm({ mode }: { mode: "sign-in" | "register" }) {
  const router = useRouter();
  const { signIn, register } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const registering = mode === "register";

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await (registering ? register(email, password) : signIn(email, password));
      router.push("/profiles");
    } catch (e) {
      // The API sends RFC 7807 and its `detail` is written for a person, so
      // show that. 429 is the one case worth rephrasing: "too many requests"
      // does not tell you the limit is per IP across the whole auth surface.
      if (e instanceof ApiError && e.status === 429) {
        setError("Too many attempts from this address. Wait a minute and try again.");
      } else if (e instanceof ApiError) {
        setError(e.message);
      } else {
        setError("Could not reach the server. Check that the API is running.");
      }
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm px-4 py-16">
      <h1 className="font-display text-2xl font-bold tracking-tight">
        {registering ? "Create an account" : "Sign in"}
      </h1>
      <p className="mt-2 text-sm leading-relaxed text-mute">
        {registering
          ? "An account is only needed to save scoring rulesets of your own. Rankings are public."
          : "Rankings are public — sign in to reach the rulesets you saved."}
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
            className="mt-1 w-full rounded border border-line-strong bg-raised px-3 py-2"
          />
        </label>

        <label className="block">
          <span className="text-sm text-mute">Password</span>
          <input
            type="password"
            required
            minLength={registering ? 12 : undefined}
            autoComplete={registering ? "new-password" : "current-password"}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 w-full rounded border border-line-strong bg-raised px-3 py-2"
          />
          {registering && (
            <span className="mt-1 block text-sm text-mute">At least 12 characters.</span>
          )}
        </label>

        {error && (
          <p role="alert" className="rounded border border-stat-loss/30 bg-stat-loss/5 px-3 py-2 text-sm text-stat-loss">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={busy}
          className="w-full rounded bg-field px-4 py-2.5 font-medium text-white disabled:opacity-60"
        >
          {busy ? "Working…" : registering ? "Create account" : "Sign in"}
        </button>
      </form>

      <p className="mt-6 text-sm text-mute">
        {registering ? "Already have an account? " : "No account yet? "}
        <Link
          href={registering ? "/login" : "/register"}
          className="text-ink underline decoration-line-strong underline-offset-2"
        >
          {registering ? "Sign in" : "Create one"}
        </Link>
      </p>
    </div>
  );
}
