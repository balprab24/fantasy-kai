import { AppShell } from "@/components/shell/AppShell";
import { RequireAccount } from "@/components/shell/RequireAccount";

/**
 * The product: the bar, and behind it an account. Everything under this group
 * needs one to be seen -- every read needs a token since 2026-09-29.
 *
 * `modal` is the overlay slot (`@modal`): a player opened from the board shows
 * there, over the page, while `children` keeps the board exactly as it was.
 * Both sit behind the account gate.
 */
export default function ProductLayout({
  children,
  modal,
}: {
  children: React.ReactNode;
  modal: React.ReactNode;
}) {
  return (
    <AppShell>
      <RequireAccount>
        {children}
        {modal}
      </RequireAccount>
    </AppShell>
  );
}
