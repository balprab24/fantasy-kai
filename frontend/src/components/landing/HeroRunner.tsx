import { HERO_IMAGE } from "./heroImage";

/**
 * The runner who crosses the hero once as the page loads, then holds the
 * pose -- the page's one piece of motion nobody asked for, so it happens once
 * and stops. `prefers-reduced-motion` (globals.css) cuts it to its last frame.
 *
 * With a licensed photo configured (`heroImage.ts`) that is what runs;
 * without one, a figure drawn here: a pictogram in the product's own ink,
 * with no face, number or team colours, so it is nobody in particular. The
 * ball is the one ki-orange thing in the drawing.
 */
export function HeroRunner() {
  return (
    <div aria-hidden={HERO_IMAGE ? undefined : true} className="relative flex justify-center lg:justify-end">
      <div className="runner-cross relative w-[min(78vw,340px)] lg:w-[min(38vw,440px)]">
        <div className="runner-bob">
          {HERO_IMAGE ? (
            // A local, fixed-size cut-out: nothing for next/image to optimise
            // that a sized <img> does not already do.
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={HERO_IMAGE.src}
              alt={HERO_IMAGE.alt}
              width={HERO_IMAGE.width}
              height={HERO_IMAGE.height}
              className="h-auto w-full"
            />
          ) : (
            <RunnerFigure />
          )}
        </div>
      </div>
      {HERO_IMAGE && (
        <p className="absolute right-0 -bottom-6 text-xs text-faint">{HERO_IMAGE.credit}</p>
      )}
    </div>
  );
}

/**
 * Drawn as thick round-capped strokes -- limbs as lines, the way a stadium
 * pictogram is -- in a 240x220 box, running right. The far arm and leg sit
 * behind the body in a darker grey, which is all the depth a pictogram needs.
 * Proportioned from a head of ~32 units in a ~200-unit figure; torso pitched
 * ~27 degrees forward, near knee driven forward, far leg extended behind on
 * the push-off, and the free arm out ahead -- opposite the back leg, the way
 * a runner's limbs cross, and a stiff-arm, which is a running back's move.
 */
function RunnerFigure() {
  const near = "var(--color-ink)";
  const far = "#5d667c";
  return (
    <svg viewBox="0 0 240 230" className="h-auto w-full overflow-visible" fill="none" strokeLinecap="round" strokeLinejoin="round">
      <defs>
        <linearGradient id="streak-ki" x1="-220" x2="110" y1="0" y2="0" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#ff8a3d" stopOpacity="0" />
          <stop offset="1" stopColor="#ff8a3d" stopOpacity="0.85" />
        </linearGradient>
        <linearGradient id="streak-energy" x1="-220" x2="110" y1="0" y2="0" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#4da3ff" stopOpacity="0" />
          <stop offset="1" stopColor="#4da3ff" stopOpacity="0.7" />
        </linearGradient>
      </defs>

      {/* Speed lines, trailing off to the left of the frame. */}
      <g className="runner-streaks">
        <path d="M-150 66H112" stroke="url(#streak-ki)" strokeWidth="3" />
        <path d="M-220 98H100" stroke="url(#streak-ki)" strokeWidth="2" />
        <path d="M-140 150H78" stroke="url(#streak-energy)" strokeWidth="3" />
        <path d="M-200 188H40" stroke="url(#streak-energy)" strokeWidth="2" />
      </g>

      {/* Where the turf meets the cleats. */}
      <ellipse cx="118" cy="216" rx="78" ry="4" fill="#fff" opacity="0.05" />

      {/* Far side, behind the body: the leg extended on the push-off, and
          the free arm out ahead in a stiff-arm. */}
      <path d="M120 126L96 170L56 200" stroke={far} strokeWidth="19" />
      <path d="M56 200L46 212" stroke={far} strokeWidth="10" />
      <path d="M152 62L182 70L206 58" stroke={far} strokeWidth="12" />

      {/* Torso, leaning ~27 degrees into the run. */}
      <path d="M156 60L124 122" stroke={near} strokeWidth="27" />

      {/* Near leg: knee driven forward, shin folded back beneath it. */}
      <path d="M124 124L170 142" stroke={near} strokeWidth="21" />
      <path d="M170 142L154 190" stroke={near} strokeWidth="16" />
      <path d="M154 192L170 196" stroke={near} strokeWidth="10" />

      {/* Helmet, with a facemask and one ki stripe. */}
      <circle cx="168" cy="33" r="16" fill={near} />
      <path d="M154 24a16 16 0 0 1 23 -6" stroke="#ff8a3d" strokeWidth="4" />
      <path d="M181 37h9M181 45h8M189 35v12" stroke={near} strokeWidth="2.5" />

      {/* The ball, tucked high and tight, and the arm wrapped over it. */}
      <g transform="rotate(-30 178 86)">
        <ellipse cx="178" cy="86" rx="15" ry="9" fill="#ff8a3d" />
        <path d="M171 86h14M174 83v6M178 83v6M182 83v6" stroke="#1a0f05" strokeWidth="1.5" />
      </g>
      <path d="M158 64L162 98L192 84" stroke={near} strokeWidth="13" />
    </svg>
  );
}
