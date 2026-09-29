import { NextResponse, type NextRequest } from "next/server";
import { API_BASE } from "@/lib/apiBase";
import { apiOriginOf, contentSecurityPolicy } from "@/lib/csp";
import { hasMemberHint, routeFor } from "@/lib/memberHint";

/**
 * Runs before every page request. First the member hint's routing
 * (`lib/memberHint.ts`): a member skips the landing page and a never-signed-in
 * visitor skips the product's loading skeleton, before either renders. Then it
 * mints the nonce and sets the Content-Security-Policy (`lib/csp.ts` says why a
 * nonce).
 *
 * The header goes on the REQUEST as well as the response, and that is not
 * redundant: Next.js finds the nonce by parsing the request's policy while it
 * renders, and stamps it on its own scripts. On the response alone, the browser
 * would enforce a nonce that no script carries, and the page would not hydrate.
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const target = routeFor(pathname, search, hasMemberHint(request.headers.get("cookie") ?? ""));
  if (target) {
    return NextResponse.redirect(new URL(target, request.url), 307);
  }

  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const policy = contentSecurityPolicy({
    nonce,
    apiOrigin: apiOriginOf(API_BASE),
    dev: process.env.NODE_ENV === "development",
  });

  const requestHeaders = new Headers(request.headers);
  // For a server component that renders its own <Script nonce>; none does yet.
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", policy);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", policy);
  return response;
}

export const config = {
  matcher: [
    {
      // Pages only. Built assets and images carry no script to protect, and a
      // link prefetch fetches an RSC payload, not a document. The static
      // headers in next.config.ts still cover every path.
      source: "/((?!_next/static|_next/image|favicon.ico).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
