"use client";

import { useEffect, useRef, useState } from "react";
import { HERO_BEATS, HERO_LOOP_MS, HERO_MAX_LOOPS, HERO_POSTER } from "@/lib/heroDemo";
import { HeroBoard, topFive } from "./HeroBoard";
import { HERO_BOARDS_2026, HERO_LEAGUE_DETAIL, HERO_SEASON, HERO_THROUGH_WEEK, type HeroRuleset } from "./heroData";

/**
 * A switch, timed as the board does it: the sweep runs while the answer is
 * "fetched", the rows travel (850ms) dimmed for the first part of it so two
 * crossing rows never print over each other at full strength, and the moved
 * rows flash once they have landed.
 */
const SWEEP_MS = 700;
const FLIGHT_MS = 550;
const LANDED_MS = 870;
const FLASH_MS = 1300;


function ordinal(n: number) {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) return `${n}th`;
  return `${n}${({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th"}`;
}

/** A player's place on a board, by id. */
const rankOn = (r: HeroRuleset, id: number) => HERO_BOARDS_2026[r].find((x) => x.playerId === id)?.rank ?? 0;

/** The caption's example, read off the boards: My league's top two, and where PPR's leader lands under it. */
const LEAGUE_TOP = HERO_BOARDS_2026["My league"].slice(0, 2);
const PPR_LEADER = HERO_BOARDS_2026.PPR[0];

/** What the board shows, in words, for anyone who cannot see it -- the scoring it is under now, not the one it opened on. */
function captionFor(ruleset: HeroRuleset) {
  const board = `The fantasy-kai rankings board for the ${HERO_SEASON} season, through week ${HERO_THROUGH_WEEK}`;
  if (ruleset === "My league") {
    return (
      `${board}, scored under a member's own ruleset, My league: ${HERO_LEAGUE_DETAIL.replace(" · ", ", ")}. Under it ` +
      LEAGUE_TOP.map((r) => `${r.name} is ${ordinal(r.rank)} (${ordinal(rankOn("PPR", r.playerId))} under PPR)`).join(" and ") +
      `, and ${PPR_LEADER.name}, 1st under PPR, is ${ordinal(rankOn("My league", PPR_LEADER.playerId))}. Its scoring switch works.`
    );
  }
  return `${board}, scored under ${ruleset}: ${topFive(ruleset)}. Its scoring switch works.`;
}

/**
 * The hero: the real 2026 rankings board, in the product's own dark skin on
 * the daylight page, making the product's own claim -- your league's exact
 * rules rewrite the rankings.
 *
 * It opens on "My league", a member's own ruleset (Half PPR with six-point
 * passing touchdowns), and says so. That is the server's render, all reduced
 * motion ever sees, and where the loop rests. The loop is the same claim in
 * motion: the board switches to PPR, the baseline, and back to My league --
 * Allen 3rd to 1st, Purdy 7th to 2nd, Smith-Njigba 1st to 10th -- then holds.
 * The header and the switch never move; only what the scoring changes does,
 * and there is no cursor: the switch visibly changing and the rows moving are
 * the explanation. The beats are data (`lib/heroDemo.ts`); this turns them into
 * state and CSS -- transitions and the board's own keyframes, no video.
 *
 * The switch is real: a visitor's click, tap or arrow key sorts the board to
 * that captured state and ends the loop for good, and so does focusing it.
 * Only a visitor's own choice is announced; the loop announces nothing. It
 * loops three times at most, only while half of it is on screen and the tab
 * is visible; the media control sits under the window, on the caption's line.
 */
export function HeroDemo() {
  const figure = useRef<HTMLElement>(null);
  const [ruleset, setRuleset] = useState<HeroRuleset>(HERO_POSTER);
  const [from, setFrom] = useState<HeroRuleset>(HERO_POSTER);
  const [flash, setFlash] = useState(false);
  const [sweep, setSweep] = useState(false);
  const [inFlight, setInFlight] = useState(false);
  const [touched, setTouched] = useState(false);
  const [paused, setPaused] = useState(false);
  const [loops, setLoops] = useState(0);
  const [canMove, setCanMove] = useState(false);
  const [inView, setInView] = useState(false);
  const [visible, setVisible] = useState(true);
  const [announced, setAnnounced] = useState("");
  const timers = useRef<number[]>([]);
  const current = useRef(ruleset);

  const done = loops >= HERO_MAX_LOOPS;
  const running = canMove && inView && visible && !paused && !touched && !done;

  // Motion is opt-out, and read only in the browser: the server renders the poster for everyone.
  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: no-preference)");
    const update = () => setCanMove(query.matches);
    update();
    query.addEventListener("change", update);
    return () => query.removeEventListener("change", update);
  }, []);

  useEffect(() => {
    const el = figure.current;
    if (!el) return;
    const io = new IntersectionObserver(([entry]) => setInView(entry.isIntersecting), { threshold: 0.5 });
    io.observe(el);
    const onVisibility = () => setVisible(document.visibilityState === "visible");
    onVisibility();
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      io.disconnect();
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, []);

  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  /** Sort the board to a scoring, as the board does after a switch: the sweep, the travel, the moved rows' flash. */
  const sortTo = (next: HeroRuleset) => {
    if (next === current.current) return;
    setFrom(current.current);
    current.current = next;
    setRuleset(next);
    setFlash(false);
    setSweep(true);
    setInFlight(true);
    timers.current.push(
      window.setTimeout(() => setInFlight(false), FLIGHT_MS),
      window.setTimeout(() => setSweep(false), SWEEP_MS),
      window.setTimeout(() => setFlash(true), LANDED_MS),
      window.setTimeout(() => setFlash(false), LANDED_MS + FLASH_MS),
    );
  };

  // One loop: every beat at its time, then the next loop. Whatever stops it, the board stays where it is.
  useEffect(() => {
    if (!running) return;
    const loop = HERO_BEATS.map((step) => window.setTimeout(() => sortTo(step.picks ?? HERO_POSTER), step.at));
    loop.push(window.setTimeout(() => setLoops((n) => n + 1), HERO_LOOP_MS));
    return () => loop.forEach(clearTimeout);
  }, [running, loops]);

  // A visitor's own choice: sort to it, end the loop, and say so.
  const choose = (next: HeroRuleset) => {
    setTouched(true);
    sortTo(next);
    setAnnounced(`Under ${next}: ${topFive(next)}.`);
  };

  const toggle = () => {
    if (running) {
      setPaused(true);
    } else {
      setPaused(false);
      setTouched(false);
      setLoops(0);
    }
  };

  return (
    <>
      <figure
        ref={figure}
        // The loop's state, for the page's own checks and anyone debugging it.
        data-demo={
          running
            ? "running"
            : `still:${[!canMove && "motion", !inView && "view", !visible && "tab", paused && "paused", touched && "touched", done && "done"].filter(Boolean).join(",")}`
        }
        className="primetime relative h-[34rem] overflow-hidden bg-canvas text-ink select-none sm:h-[38rem] sm:rounded-plate lg:rounded-r-none xl:h-[39rem]"
      >
        <figcaption className="sr-only">{captionFor(ruleset)}</figcaption>
        <p className="sr-only" aria-live="polite">
          {announced}
        </p>

        <div className="h-full [mask-image:linear-gradient(to_bottom,black_84%,transparent)]">
          <HeroBoard
            ruleset={ruleset}
            from={from}
            flash={flash}
            sweep={sweep && canMove}
            inFlight={inFlight && canMove}
            onSelect={choose}
            onConsoleFocus={() => setTouched(true)}
          />
        </div>

      </figure>

      {/* The caption's line, with the media control at its end: under the window, never over the data. */}
      {/* The control's height held from the first paint, so its arrival after hydration moves nothing. */}
      <div className="mt-3 flex min-h-8 items-center justify-between gap-4 px-4 sm:px-0 lg:pr-[max(2rem,calc((100vw-1320px)/2+2rem))]">
        <p className="text-[13px] text-faint">
          {HERO_SEASON} rankings &middot; through Week {HERO_THROUGH_WEEK}
        </p>
        {canMove && (
          <button
            type="button"
            onClick={toggle}
            className="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-control px-2.5 text-[13px] text-mute shadow-[inset_0_0_0_1px_var(--color-line-strong)] transition-colors hover:text-ink"
          >
            <svg aria-hidden width="10" height="10" viewBox="0 0 10 10" fill="currentColor">
              {running ? <path d="M1.5 1h2.5v8H1.5zM6 1h2.5v8H6z" /> : <path d="M2 1l7 4-7 4z" />}
            </svg>
            {running ? "Pause" : "Play"}
            <span className="sr-only"> the demonstration</span>
          </button>
        )}
      </div>
    </>
  );
}
