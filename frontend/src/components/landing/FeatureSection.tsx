import { LANDING_SECTIONS } from "@/lib/landing";

/**
 * What a section stands on. The page keeps a rhythm by changing ground, not by
 * boxing content: paper is the page itself; the band is the cool grey of one
 * alternating section; the stage is the product's own black (`.primetime`),
 * full width, where the page cuts to the product as it really looks.
 */
export type SectionTone = "paper" | "band" | "stage";

const TONE: Record<SectionTone, string> = {
  paper: "",
  band: "bg-band",
  stage: "primetime bg-canvas text-ink",
};

/**
 * One section of the pitch. Its id comes from `LANDING_SECTIONS`, so the
 * header's link and the section cannot disagree. The layout is the caller's:
 * each section has its own composition, so the page changes pace instead of
 * repeating one heading-paragraph-button template down the scroll.
 */
export function FeatureSection({
  id,
  tone = "paper",
  className = "",
  children,
}: {
  id: string;
  tone?: SectionTone;
  className?: string;
  children: React.ReactNode;
}) {
  if (!LANDING_SECTIONS.some((s) => s.id === id)) {
    throw new Error(`FeatureSection "${id}" is not in LANDING_SECTIONS`);
  }
  return (
    <section id={id} aria-labelledby={`${id}-title`} className={`scroll-mt-16 ${TONE[tone]} ${className}`}>
      <div className="mx-auto max-w-[1320px] px-4 sm:px-6 lg:px-8">{children}</div>
    </section>
  );
}

/**
 * A section's heading, in the display voice at section scale: only the page's
 * h1 and the closing ask are set larger. Callers break it by phrase, one `<span
 * className="block">` per line, so a line never ends mid-thought.
 */
export function SectionTitle({
  id,
  className = "",
  children,
}: {
  id: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <h2
      id={`${id}-title`}
      className={`type-display text-[clamp(2.75rem,4.6vw,3.75rem)] text-ink ${className}`}
    >
      {children}
    </h2>
  );
}

/** A section's prose: a readable measure, the figures that matter in ink. */
export function SectionCopy({ className = "", children }: { className?: string; children: React.ReactNode }) {
  return (
    <div
      className={`max-w-[36rem] space-y-4 text-[17px] leading-[1.65] text-pretty text-mute [&_strong]:font-semibold [&_strong]:text-ink ${className}`}
    >
      {children}
    </div>
  );
}
