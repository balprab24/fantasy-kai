import Link from "next/link";
import { LANDING_SECTIONS } from "@/lib/landing";

/**
 * One section of the pitch. Its id comes from `LANDING_SECTIONS`, so the
 * header's link and the section cannot disagree. Tall enough on a wide screen
 * that the phone has a moment on each one before the next takes over.
 */
export function FeatureSection({
  id,
  title,
  children,
}: {
  id: string;
  title: string;
  children: React.ReactNode;
}) {
  if (!LANDING_SECTIONS.some((s) => s.id === id)) {
    throw new Error(`FeatureSection "${id}" is not in LANDING_SECTIONS`);
  }
  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      className="scroll-mt-28 border-t border-line py-16 first:border-t-0 md:scroll-mt-20 lg:flex lg:min-h-[78vh] lg:flex-col lg:justify-center lg:py-24"
    >
      <h2
        id={`${id}-title`}
        className="font-display max-w-[20ch] text-[clamp(2rem,3.6vw,3rem)] leading-[1] font-bold tracking-[-0.01em] text-balance"
      >
        {title}
      </h2>
      <div className="mt-6 max-w-[62ch] space-y-4 text-[17px] leading-[1.6] text-mute [&_strong]:font-medium [&_strong]:text-ink">
        {children}
      </div>
    </section>
  );
}

/** The ask at the end of each section: the form at the foot of the page, or sign in. */
export function SectionAsk() {
  return (
    <p className="pt-4 text-[15px]">
      <a
        href="#join"
        className="inline-flex rounded-md bg-ki px-4 py-2 font-semibold text-on-ki transition-[filter] hover:brightness-110"
      >
        Join the Kai
      </a>
      <span className="mx-3 text-faint">or</span>
      <Link href="/login" className="text-ink underline decoration-line-strong underline-offset-4 hover:decoration-energy">
        sign in
      </Link>
    </p>
  );
}
