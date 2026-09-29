"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { AuthProvider } from "@/lib/auth";

export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // A ranking is a pure function of (ruleset, season, scope) over a
            // table that changes once a day. Refetching it on every window
            // focus would re-run a full-season scan on the server -- the
            // measured 88% of request cost -- to get the same answer back.
            staleTime: 5 * 60_000,
            refetchOnWindowFocus: false,
            // One retry for a network blip or a 5xx, none for a 4xx: an unknown
            // player or a ruleset you cannot see is the same answer the second
            // time, and waiting on it keeps a "not found" behind a skeleton --
            // for as long as the tab is in the background, since retries pause
            // while the window is not focused.
            retry: (failures, error) =>
              failures < 1 && !(error instanceof ApiError && error.status < 500),
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={client}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
}
