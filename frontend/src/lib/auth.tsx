"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, restoreSession, setAccessToken } from "./api";
import { clearedMemberHintCookie, hasMemberHint, memberHintCookie, type RefreshOutcome } from "./memberHint";
import type { TokenResponse } from "./types";

type Status = "restoring" | "signed-in" | "signed-out";

interface Auth {
  status: Status;
  signIn: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<Auth | null>(null);

// Written before any status change, so a navigation that follows -- sign-in
// landing on ?next=, sign-out landing on / -- already carries the right hint to
// proxy.ts. lib/memberHint.ts says what the hint is and is not.
function markMember(member: boolean) {
  document.cookie = member ? memberHintCookie(location.protocol === "https:") : clearedMemberHintCookie();
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>("restoring");
  const queryClient = useQueryClient();

  // On load there is no access token -- it was deliberately never persisted.
  // The HttpOnly refresh cookie is what survives, so the first thing the app
  // does is trade it for one. "restoring" is a real third state and the header
  // waits for it, otherwise every reload flashes "Sign in" at a signed-in user.
  //
  // Only when this browser has signed in before. A visitor with no hint has no
  // refresh cookie to trade, and asking anyway spent a rate-limited /auth call
  // on every anonymous page load.
  useEffect(() => {
    let cancelled = false;
    const attempt: Promise<RefreshOutcome | "not-a-member"> = hasMemberHint(document.cookie)
      ? restoreSession()
      : Promise.resolve("not-a-member");
    attempt.then((outcome) => {
      if (cancelled) return;
      // Success re-sets the hint's 30 days, in step with the rotated refresh
      // cookie. Only the server's own "no" (a 401) retires it: a reload that
      // aborted this request, a 429 or a 5xx says nothing about the session.
      if (outcome === "restored") markMember(true);
      if (outcome === "rejected") markMember(false);
      setStatus(outcome === "restored" ? "signed-in" : "signed-out");
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const authenticate = useCallback(
    async (path: string, email: string, password: string) => {
      const token = await api<TokenResponse>(path, {
        method: "POST",
        body: { email, password },
        anonymous: true,
      });
      setAccessToken(token.accessToken);
      markMember(true);
      setStatus("signed-in");
      // Which scoring profiles you can see is decided by the JWT subject in
      // the query, so every cached answer was computed for a different user.
      await queryClient.invalidateQueries();
    },
    [queryClient],
  );

  const signIn = useCallback(
    (email: string, password: string) => authenticate("/api/v1/auth/login", email, password),
    [authenticate],
  );

  const register = useCallback(
    (email: string, password: string) => authenticate("/api/v1/auth/register", email, password),
    [authenticate],
  );

  const signOut = useCallback(async () => {
    try {
      await api<void>("/api/v1/auth/logout", { method: "POST" });
    } finally {
      // Local state is cleared even if the call failed: a logout that appears
      // to do nothing is worse than one that leaves a server-side token to
      // expire on its own.
      setAccessToken(null);
      markMember(false);
      setStatus("signed-out");
      queryClient.clear();
    }
  }, [queryClient]);

  return (
    <AuthContext.Provider value={{ status, signIn, register, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): Auth {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
