import { ApiError } from "./api";

/**
 * What a failed sign-in or sign-up says, in one place, so the auth pages and
 * the landing page's form cannot drift apart.
 *
 * The API sends RFC 7807 and its `detail` is written for a person, so that is
 * shown as it is. 429 is the one case worth rephrasing: "too many requests"
 * does not tell you the limit is per address across the whole auth surface.
 */
export function authErrorMessage(e: unknown): string {
  if (e instanceof ApiError && e.status === 429) {
    return "Too many attempts from this address. Wait a minute and try again.";
  }
  if (e instanceof ApiError) return e.message;
  return "Could not reach the server. Check your connection and try again.";
}

/** A sign-up refused because the email already has an account. */
export function isEmailTaken(e: unknown): boolean {
  return e instanceof ApiError && e.status === 409;
}
