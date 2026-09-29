"use client";

import { useSyncExternalStore } from "react";
import { safeNext } from "./landing";

/** The location never changes under a mounted sign-in page, so there is nothing to subscribe to. */
const subscribe = () => () => {};

/**
 * The sign-in page's `?next=`, already through `safeNext`.
 *
 * Read from `window.location` rather than `useSearchParams`, which on a
 * prerendered route needs a Suspense boundary that only `npm run build`
 * notices is missing. The server has no location, so it renders with none and
 * the browser fills it in -- `useSyncExternalStore`'s server snapshot is
 * exactly that, without a setState in an effect.
 */
export function useNextParam(): string | null {
  const search = useSyncExternalStore(
    subscribe,
    () => window.location.search,
    () => "",
  );
  return safeNext(new URLSearchParams(search).get("next"));
}
