import { Attribution } from "@/components/Attribution";
import { TopBar } from "./TopBar";
import { Sidebar } from "./Sidebar";

/**
 * The frame every product page sits in: the bar, one content column, and the
 * attribution footer -- which is here, and not on any one page, because the
 * licence requires it everywhere. Pages own their own width inside it.
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar />
      <Sidebar />
      <main className="min-w-0 flex-1 lg:pl-[88px]">{children}</main>
      <div className="lg:pl-[88px]"><Attribution /></div>
    </div>
  );
}
