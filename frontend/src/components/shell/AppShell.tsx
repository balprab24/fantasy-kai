import { Attribution } from "@/components/Attribution";
import { MobileNav } from "./MobileNav";
import { Sidebar } from "./Sidebar";

/**
 * The frame every page sits in: a persistent rail on the left and one content
 * column. Pages own their own width inside that column; the shell only
 * guarantees the rail, the mobile bar, and the attribution footer -- which is
 * here, and not on any one page, because the licence requires it everywhere.
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-dvh md:flex">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <MobileNav />
        <main className="flex-1">{children}</main>
        <Attribution />
      </div>
    </div>
  );
}
