import type { Problem } from "./types";

/**
 * One fetch wrapper, and the access token never leaves this module.
 *
 * north-star §5c: the access token lives in memory only, never in
 * `localStorage`. The refresh token is the persistence mechanism and it is an
 * `HttpOnly` cookie precisely so that script cannot read it -- putting the
 * access token in `localStorage` would hand back the XSS exposure that choice
 * was made to avoid. A page reload therefore starts with no access token and
 * silently re-earns one from the cookie, which is the intended cost.
 */

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function hasAccessToken() {
  return accessToken !== null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem;

  constructor(status: number, problem: Problem) {
    // Prefer the server's own words. It sends RFC 7807 on every error path, and
    // its `detail` is written for a person -- restating it here would be worse.
    super(problem.detail || problem.title || `Request failed (${status})`);
    this.status = status;
    this.problem = problem;
  }
}

async function toProblem(response: Response): Promise<Problem> {
  try {
    return (await response.json()) as Problem;
  } catch {
    return { status: response.status, title: response.statusText };
  }
}

/**
 * Refresh is de-duplicated. Three queries can 401 in the same tick, and three
 * concurrent rotations would race: the refresh token rotates on every use and
 * replay of a consumed one revokes the whole family, so an unsynchronised
 * retry storm would log the user out rather than recover them.
 */
function refreshOnce(): Promise<boolean> {
  refreshing ??= (async () => {
    try {
      const response = await fetch(`${BASE}/api/v1/auth/refresh`, {
        method: "POST",
        credentials: "include",
      });
      if (!response.ok) {
        accessToken = null;
        return false;
      }
      const body = (await response.json()) as { accessToken: string };
      accessToken = body.accessToken;
      return true;
    } catch {
      accessToken = null;
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

interface Options {
  method?: string;
  body?: unknown;
  /** Skip the refresh-and-retry dance; used by the auth calls themselves. */
  anonymous?: boolean;
}

export async function api<T>(path: string, options: Options = {}): Promise<T> {
  const { method = "GET", body, anonymous = false } = options;

  const send = () =>
    fetch(`${BASE}${path}`, {
      method,
      credentials: "include",
      headers: {
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(accessToken && !anonymous ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });

  let response = await send();

  // A 401 on a read is normal for a logged-out visitor -- reads are public and
  // the row filter does the work -- so only retry when there was a session to
  // restore in the first place.
  if (response.status === 401 && !anonymous && accessToken !== null) {
    if (await refreshOnce()) {
      response = await send();
    }
  }

  if (response.status === 204) {
    return undefined as T;
  }
  if (!response.ok) {
    throw new ApiError(response.status, await toProblem(response));
  }
  return (await response.json()) as T;
}

/** Called once on load: turns the refresh cookie back into an access token. */
export async function restoreSession(): Promise<boolean> {
  return refreshOnce();
}
