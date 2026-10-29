import { Attribution } from "@/components/Attribution";
import { Sidebar } from "./Sidebar";
import { TopBar } from "./TopBar";

/**
 * The frame every product page sits in: the navigation, one content column,
 * and the attribution footer -- which is here, and not on any one page,
 * because the licence requires it everywhere. Pages own their own width
 * inside it.
 *
 * Below md the navigation is the sticky `TopBar`; from md it is the fixed
 * `Sidebar`, and the column steps right by the sidebar's width. Each hides
 * itself, so the top bar stays a direct child of this column and its sticky
 * position has the whole page to travel.
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col md:pl-44">
      <Sidebar />
      <TopBar />
      <main className="flex-1">{children}</main>
      <Attribution />
    </div>
  );
}
