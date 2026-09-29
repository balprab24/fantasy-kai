import type { Metadata } from "next";
import { Saira_Semi_Condensed, Schibsted_Grotesk } from "next/font/google";
import { connection } from "next/server";
import { Providers } from "./providers";
import "./globals.css";

// Display face for titles and tier letters only. Semi-condensed with a
// slight instrument-panel cut: it reads as sport, not as a cartoon. Not a
// variable font, so the weights in use are named.
const saira = Saira_Semi_Condensed({
  subsets: ["latin"],
  weight: ["600", "700"],
  variable: "--font-saira",
  display: "swap",
});

const schibsted = Schibsted_Grotesk({
  subsets: ["latin"],
  variable: "--font-schibsted",
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
    <html lang="en" className={`${saira.variable} ${schibsted.variable}`}>
      <body className="min-h-dvh bg-paper text-ink antialiased">
        {/* Providers only. The two route groups choose their own frame: the
            landing and auth pages sit under a site header, the product under
            the rail (app/(site)/layout.tsx, app/(app)/layout.tsx). */}
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
