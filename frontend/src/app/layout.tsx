import type { Metadata } from "next";
import {
  Sofia_Sans,
  Sofia_Sans_Extra_Condensed,
  Sofia_Sans_Semi_Condensed,
} from "next/font/google";
import { connection } from "next/server";
import { Providers } from "./providers";
import "./globals.css";

// One superfamily in three widths, self-hosted by next/font (the CSP allows
// fonts from this site only). Chosen by setting the real copy and the real
// numbers side by side: its tabular figures keep "1,202" and "365.6" tight,
// where the previous face opened a gap around every comma and point.
// All three are variable on weight, so no weight is named.

// The voice: extra-condensed, heavy, italic -- headlines, titles, the name.
const sofiaExtra = Sofia_Sans_Extra_Condensed({
  subsets: ["latin"],
  style: ["italic"],
  variable: "--font-sofia-extra",
  display: "swap",
});

// Labels, column headers, ranks and stat figures.
const sofiaSemi = Sofia_Sans_Semi_Condensed({
  subsets: ["latin"],
  style: ["normal", "italic"],
  variable: "--font-sofia-semi",
  display: "swap",
});

// Everything you read and operate: body, controls, table data.
const sofia = Sofia_Sans({
  subsets: ["latin"],
  style: ["normal"],
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
    <html lang="en" className={`${sofiaExtra.variable} ${sofiaSemi.variable} ${sofia.variable}`}>
      <body className="min-h-dvh bg-canvas text-ink antialiased">
        {/* Providers only. The two route groups choose their own frame: the
            landing and auth pages sit under a site header, the product under
            the rail (app/(site)/layout.tsx, app/(app)/layout.tsx). */}
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
