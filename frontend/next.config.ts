import type { NextConfig } from "next";
import { API_BASE } from "./src/lib/apiBase";
import { apiOriginOf } from "./src/lib/csp";

// Parsed here, at build time, so a malformed NEXT_PUBLIC_API_URL (no scheme, a
// typo) fails the build instead of shipping: proxy.ts parses the same value on
// every request, and a throw there is a 500 on every page. An EMPTY value still
// passes -- that is F11 (DEPLOY-STEPS.md), owed its own refusal.
apiOriginOf(API_BASE);

/**
 * Sent on every path, including built assets. The Content-Security-Policy is
 * not here: it carries a per-request nonce, so proxy.ts sets it (lib/csp.ts).
 * HSTS is not here either -- Vercel already sends it for the domain.
 */
const SECURITY_HEADERS = [
  // Never guess a type: a JSON or text response must not be run as script.
  { key: "X-Content-Type-Options", value: "nosniff" },
  // Full URLs stay on this site; another origin sees only the origin. The
  // board's filters live in the URL, and a player id is nobody else's business.
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  // frame-ancestors 'none' in the CSP says this to current browsers; this says
  // it to the ones that predate it. The sign-in form must not be frameable.
  { key: "X-Frame-Options", value: "DENY" },
  // Nothing here uses them, so nothing loaded here may ask.
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  // Next writes its own AGENTS.md and CLAUDE.md into this directory on every
  // dev start. The repo already has one CLAUDE.md at the root and it is the
  // project's operational memory -- a generated second one nested here shadows
  // it for anything working in frontend/, which is the opposite of useful.
  agentRules: false,
  async headers() {
    return [{ source: "/:path*", headers: SECURITY_HEADERS }];
  },
};

export default nextConfig;
