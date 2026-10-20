/**
 * The overlay slot when nothing is shown over the page: a direct load of any
 * product URL, including `/players/[id]` itself, which then renders as a full
 * page (`app/(app)/players/[id]/page.tsx`).
 */
export default function NoOverlay() {
  return null;
}
