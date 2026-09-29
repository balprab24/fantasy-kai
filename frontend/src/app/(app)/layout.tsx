import { AppShell } from "@/components/shell/AppShell";
import { RequireAccount } from "@/components/shell/RequireAccount";

/**
 * The product: the rail, and behind it an account. Everything under this group
 * needs one to be seen -- a decision about the website, not the data, since the
 * read endpoints still answer anonymously (north-star §2, 2026-09-28).
 */
export default function ProductLayout({ children }: { children: React.ReactNode }) {
  return (
    <AppShell>
      <RequireAccount>{children}</RequireAccount>
    </AppShell>
  );
}
