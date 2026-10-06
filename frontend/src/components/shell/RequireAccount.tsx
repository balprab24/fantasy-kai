"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Skeleton } from "@/components/ui/StatusMessage";
import { useAuth } from "@/lib/auth";

/**
 * The product is for members. A signed-out visitor is sent to sign in, with
 * the page they asked for carried along so signing in lands them on it.
 *
 * `window.location`, read inside the effect, and not `useSearchParams`: these
 * routes are prerendered, and `useSearchParams` there needs a Suspense boundary
 * that `next dev` does not ask for and `npm run build` does. The effect only
 * ever runs in a browser, where the location is simply there.
 *
 * Nothing under the gate renders until the session is known, so a signed-out
 * visitor never sees a board start to load and then vanish -- and no query
 * goes out on their behalf in the meantime.
 */
export function RequireAccount({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (status !== "signed-out") return;
    const here = window.location.pathname + window.location.search;
    router.replace(`/login?next=${encodeURIComponent(here)}`);
  }, [status, router]);

  if (status === "signed-in") return <>{children}</>;
  return (
    <div className="mx-auto max-w-[1320px] px-4 pt-7 sm:px-6 lg:px-8" aria-busy="true">
      <span className="sr-only">Checking your account</span>
      <Skeleton className="h-9 w-56" />
      <Skeleton className="mt-3 h-4 w-96 max-w-full" />
      <Skeleton className="mt-6 h-11 w-full" />
      <Skeleton className="mt-6 h-4 w-full" />
      <Skeleton className="mt-5 h-4 w-full" />
      <Skeleton className="mt-5 h-4 w-full" />
    </div>
  );
}
