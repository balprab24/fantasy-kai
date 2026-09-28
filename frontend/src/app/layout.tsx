import type { Metadata } from "next";
import { Saira_Semi_Condensed, Schibsted_Grotesk } from "next/font/google";
import { Providers } from "./providers";
import { AppShell } from "@/components/shell/AppShell";
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

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${saira.variable} ${schibsted.variable}`}>
      <body className="min-h-dvh bg-paper text-ink antialiased">
        <Providers>
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}
