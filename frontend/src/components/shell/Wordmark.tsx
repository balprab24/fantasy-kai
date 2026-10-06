import Link from "next/link";

/** Two slanted strokes -- a rising bar and its after-image. Ours, not borrowed. */
export function LogoMark({ size }: { size: number }) {
  return (
    <svg aria-hidden width={size} height={size} viewBox="0 0 26 26" className="shrink-0">
      <path d="M9 3h6L9 23H3z" fill="var(--color-ki)" />
      <path d="M18 3h5l-6 20h-5z" fill="var(--color-energy)" opacity="0.85" />
    </svg>
  );
}

/**
 * The orb of ki: amber glass lit from the upper left, one red star. The
 * wordmark's ı wears it for its dot -- the product's anime nod, small enough
 * to be found on a second look. Drawn here, not taken from anywhere. The glass
 * is CSS (`.orb` in globals.css), not an SVG gradient, so no id is needed and
 * any number of copies can share a page.
 */
export function Orb({ className = "" }: { className?: string }) {
  return (
    <span aria-hidden className={`orb ${className}`}>
      <svg viewBox="0 0 20 20" className="block size-full">
        <path
          d="M10 5.6l1.2 2.7 2.9.3-2.2 1.9.7 2.9L10 11.9l-2.6 1.5.7-2.9-2.2-1.9 2.9-.3z"
          fill="#d42a1c"
        />
      </svg>
    </span>
  );
}

/**
 * The logo and the word, set in the display face's extra-condensed italic so
 * the word leans the way the mark's strokes do. The ı is dotless, with the orb
 * where its dot would be -- so the visible word is not the real word, and
 * screen readers get the real one.
 *
 * `href` is `/` on the landing page and the board inside the product, where `/`
 * would only bounce a member back.
 */
export function Wordmark({ href = "/" }: { href?: string }) {
  return (
    <Link href={href} className="flex shrink-0 items-center gap-2 rounded-control">
      <LogoMark size={26} />
      <span className="font-display text-[25px] leading-none font-extrabold tracking-[0.005em] text-ink italic">
        <span aria-hidden>
          fantasy<span className="text-ki">·</span>ka
          {/* line-height 1 makes the box a property of the font, not the page,
              so the orb's offsets are measured against the glyph itself. */}
          <span className="relative inline-block leading-none">
            ı
            <Orb className="absolute top-[-0.06em] left-[68%] size-[0.33em] -translate-x-1/2" />
          </span>
        </span>
        <span className="sr-only">fantasy·kai</span>
      </span>
    </Link>
  );
}
