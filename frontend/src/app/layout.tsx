import type { Metadata } from "next";
import localFont from "next/font/local";
import { connection } from "next/server";
import { Providers } from "./providers";
import "./globals.css";

// One superfamily in three widths, self-hosted from files in this repo
// (app/fonts, SIL OFL 1.1 -- OFL.txt beside them). The CSP allows fonts from
// this site only, and next/font serves these from it. Local files rather than
// next/font/google: the build no longer reaches fonts.googleapis.com, so a
// build in a network-restricted place (CI, a sandbox) sets the real type
// instead of silently falling back to a system face. The files are
// Fontsource's latin subsets of the same variable fonts Google serves.
// Every one is variable on weight (1-1000), so no weight is named below.

// The voice: extra-condensed, heavy, italic -- headlines, titles, the name.
const sofiaExtra = localFont({
  src: "./fonts/SofiaSansExtraCondensed-latin-wght-italic.woff2",
  style: "italic",
  weight: "1 1000",
  variable: "--font-sofia-extra",
  display: "swap",
});

// Labels, column headers, ranks and stat figures.
const sofiaSemi = localFont({
  src: [
    { path: "./fonts/SofiaSansSemiCondensed-latin-wght.woff2", style: "normal", weight: "1 1000" },
    { path: "./fonts/SofiaSansSemiCondensed-latin-wght-italic.woff2", style: "italic", weight: "1 1000" },
  ],
  variable: "--font-sofia-semi",
  display: "swap",
});

// Everything you read and operate: body, controls, table data.
const sofia = localFont({
  src: "./fonts/SofiaSans-latin-wght.woff2",
  style: "normal",
  weight: "1 1000",
  variable: "--font-sofia",
  display: "swap",
});

export const metadata: Metadata = {
  title: "fantasy-kai",
  description:
    "One season, scored against any league's rules. Rankings recomputed on demand, never stored.",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // Every page renders per request, never as build-time HTML. The CSP nonce
  // (proxy.ts, lib/csp.ts) is minted per request and Next.js stamps it on its
  // scripts while rendering; a page prerendered at build time carries scripts
  // with no nonce, which the policy then refuses -- the page would not hydrate.
  // The cost, measured before it shipped, is in docs/map.md §5.
  await connection();
  return (
    <html
      lang="en"
      // Smooth scrolling is for in-page anchors; Next turns it off around route changes when told it is on.
      data-scroll-behavior="smooth"
      className={`${sofiaExtra.variable} ${sofiaSemi.variable} ${sofia.variable}`}
    >
      <body className="min-h-dvh bg-canvas text-ink antialiased">
        {/* Providers only. The two route groups choose their own frame: the
            landing and auth pages sit under a site header, the product under
            the rail (app/(site)/layout.tsx, app/(app)/layout.tsx). */}
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
