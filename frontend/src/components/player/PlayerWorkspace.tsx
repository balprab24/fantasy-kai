"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useId, useMemo, useOptimistic, useRef, useState, useSyncExternalStore, useTransition } from "react";
import { ApiError } from "@/lib/api";
import { clearBoardReturn, readBoardReturn } from "@/lib/boardReturn";
import { boardSequenceSnapshot, neighbours, parseBoardSequence, type SequenceEntry } from "@/lib/boardSequence";
import { pickSeason } from "@/lib/player";
import { parseTab, type PlayerTab } from "@/lib/playerView";
import { profileLabel } from "@/lib/profiles";
import { useCareer, useCareers, useGamelog, useOverallRank, usePlayer, useSelectedProfile } from "@/lib/queries";
import { FIRST_SEASON, currentSeason } from "@/lib/season";
import { PlayerAvatar } from "../rankings/PlayerAvatar";
import { BUTTON_ICON } from "../ui/buttons";
import { Icon } from "../ui/Icon";
import { SegmentedControl } from "../ui/SegmentedControl";
import { Skeleton, StatusMessage } from "../ui/StatusMessage";
import { PerformanceChart } from "./PerformanceChart";
import { Panel } from "./PlayerPanels";
import { IdentityCard, ScoringCard } from "./PlayerSidebar";
import { CareerError, CareerTable, GameLog, HeatLegend, TableSkeleton, type CareerMode } from "./PlayerTables";
import { ScoringCompare } from "./ScoringCompare";

const RANKED = ["QB", "RB", "WR", "TE"];

/** The sequence only changes when the board writes it, before this view mounts. */
const noSubscription = () => () => {};

function int(raw: string | null): number | null {
  return raw !== null && /^[1-9][0-9]{0,9}$/.test(raw) ? Number(raw) : null;
}

/**
 * A player, as a fantasy asset: who he is, what his season is worth under your
 * scoring, how it went week by week, how every ruleset you can use prices the
 * same games, and every season before it.
 *
 * Two shapes, one component. `page` is `/players/[id]` opened directly -- a
 * shared link, a refresh -- with a way back to the board. `overlay` is the same
 * URL opened from the board (an intercepted route, `PlayerOverlay`), shown over
 * the board without leaving it, with the previous and next player on the
 * board one step away.
 *
 * The layout is a sidebar and a column. The sidebar is who he is and what his
 * season is worth (`IdentityCard`), and the two choices behind every number,
 * ruleset and season (`ScoringCard`). The column is the evidence, one panel
 * under another -- the game log, his games charted, his career, and the same
 * season under every ruleset -- with a bar of section links pinned above it
 * that follows the scroll and, once the sidebar has scrolled away, carries
 * his face and name.
 *
 * Requests: the player (a primary-key read); the career under the chosen
 * ruleset (feeds the sidebar, the chart and the career); the season's game
 * log; the board's first page for his overall rank (shared with the board's
 * cache); and, only once the Scoring panel is near the screen, the career
 * under every other ruleset. Season, scoring and section live in the URL,
 * replaced rather than pushed: a refresh or a shared link opens the same view,
 * and back goes to wherever you came from rather than through every season
 * you tried. The writes go through `router.replace` with `scroll: false`: after
 * a bare `replaceState` Next scrolled the view back to its top (measured: a
 * section link clicked halfway down landed at 2px instead of on the career),
 * and a scoring change halfway down now keeps its place.
 */
export function PlayerWorkspace({
  playerId,
  variant = "page",
  onClose,
}: {
  playerId: number;
  variant?: "page" | "overlay";
  onClose?: () => void;
}) {
  // What the URL says, or is about to say: a write shows at once, and gives
  // way to the real URL when the router has caught up.
  const liveParams = useSearchParams();
  const [query, showQuery] = useOptimistic(liveParams.toString());
  const params = useMemo(() => new URLSearchParams(query), [query]);
  const [, startTransition] = useTransition();
  const pathname = usePathname();
  const router = useRouter();
  const now = currentSeason();
  const sectionsId = useId();

  const requestedSeason = int(params.get("season"));
  const validSeason =
    requestedSeason !== null && requestedSeason >= FIRST_SEASON && requestedSeason <= now
      ? requestedSeason
      : null;
  const tab = parseTab(params.get("tab"));
  const { profiles, profileId } = useSelectedProfile(int(params.get("profileId")));

  const player = usePlayer(playerId);
  const career = useCareer(playerId, profileId);
  const seasons = career.data?.seasons ?? [];
  const regular = seasons.map((s) => s.season);
  // Every season with a game, so one reached only in January still has a
  // switch -- its log shows the playoff games, its line says "no regular season".
  const played = career.data?.seasonsPlayed ?? regular;
  const season = career.data
    ? pickSeason(validSeason, regular.length ? regular : played, now)
    : validSeason;
  const gamelog = useGamelog(playerId, profileId, season);

  const position = player.data?.position ?? "";
  const ranked = RANKED.includes(position);
  const overall = useOverallRank(playerId, profileId, season, ranked && !!player.data);
  const allProfiles = profiles.data ?? [];
  // The same season under every ruleset: fetched once the Scoring panel comes
  // near the screen (or the URL opens on it), not for every player opened.
  const [scoringNear, setScoringNear] = useState(tab === "scoring");
  const careers = useCareers(
    playerId,
    allProfiles.map((p) => p.id),
    scoringNear && ranked,
  );
  const [careerMode, setCareerMode] = useState<CareerMode>("total");

  const selected = seasons.find((s) => s.season === season) ?? null;
  const profile = profiles.data?.find((p) => p.id === profileId);
  const scoringLabel = profile ? profileLabel(profile) : "…";
  const careerStale = career.isPlaceholderData;

  // The board's order when this player was opened, for previous and next.
  // sessionStorage does not exist on the server, so the server's answer is
  // "none" and the browser's is read after hydration, without a mismatch.
  const rawSequence = useSyncExternalStore(noSubscription, boardSequenceSnapshot, () => null);
  const sequence = useMemo(() => parseBoardSequence(rawSequence), [rawSequence]);
  // Only while the view is on the board's own rules: "next on the board" means
  // the board the member left, and once the season or scoring here differs
  // from it, that order no longer describes what is on screen.
  const onBoardRules =
    sequence !== null && sequence.search === `?season=${season ?? ""}&profileId=${profileId ?? ""}`;
  const { previous, next } = onBoardRules ? neighbours(sequence, playerId) : { previous: null, next: null };

  // Leaving the site from here (another origin, a closed tab, a reload) means
  // the board is no longer the page before this one, so its "come back to me"
  // note is dropped. In-app navigation never fires pagehide.
  useEffect(() => {
    window.addEventListener("pagehide", clearBoardReturn);
    return () => window.removeEventListener("pagehide", clearBoardReturn);
  }, []);

  // Merged into the newest write, not this render's: two quick changes (a
  // section link, then a ruleset) must not undo each other while the first is
  // still on its way.
  const latest = useRef<string | null>(null);
  const committed = liveParams.toString();
  useEffect(() => {
    if (latest.current === committed) latest.current = null;
  }, [committed]);
  const write = (change: Record<string, string | null>) => {
    const nextParams = new URLSearchParams(latest.current ?? committed);
    for (const [key, value] of Object.entries(change)) {
      if (value === null) nextParams.delete(key);
      else nextParams.set(key, value);
    }
    const next = nextParams.toString();
    latest.current = next;
    startTransition(() => {
      showQuery(next);
      router.replace(`${pathname}${next ? `?${next}` : ""}`, { scroll: false });
    });
  };
  const setTab = (t: PlayerTab) => write({ tab: t === "overview" ? null : t });

  // The section links follow the scroll: the current section is the last one
  // whose top has passed just under the pinned bar. The same pass notes when
  // the sidebar's card has scrolled away (the bar then carries his name) and
  // when the Scoring panel is near (its requests go out then). Measured on the
  // overlay's own scroll, or the window's on a page.
  const mainRef = useRef<HTMLDivElement>(null);
  const identityRef = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState<PlayerTab>(tab === "overview" ? "log" : tab);
  const [pastIdentity, setPastIdentity] = useState(false);
  const scrollMargin = variant === "page" ? "scroll-mt-[7.5rem]" : "scroll-mt-16";
  useEffect(() => {
    const main = mainRef.current;
    if (!main) return;
    const dialog = main.closest("dialog");
    const scroller: HTMLElement | Window = dialog ?? window;
    let frame = 0;
    const measure = () => {
      frame = 0;
      const top = dialog ? dialog.getBoundingClientRect().top : 0;
      const height = dialog ? dialog.clientHeight : window.innerHeight;
      const barLine = top + (variant === "page" ? 140 : 90);
      let current: PlayerTab = "log";
      for (const key of ["log", "performance", "career", "scoring"] as const) {
        const el = document.getElementById(`${sectionsId}-${key}`);
        if (el && el.getBoundingClientRect().top <= barLine) current = key;
      }
      setActive(current);
      const card = identityRef.current?.getBoundingClientRect();
      setPastIdentity(!!card && card.bottom < top + (variant === "page" ? 112 : 56));
      const scoring = document.getElementById(`${sectionsId}-scoring`);
      if (scoring && scoring.getBoundingClientRect().top < top + height + 480) setScoringNear(true);
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(measure);
    };
    measure();
    scroller.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      if (frame) cancelAnimationFrame(frame);
      scroller.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
    };
  }, [sectionsId, variant, career.data, gamelog.data]);

  const pick = (key: PlayerTab) => {
    setTab(key);
    if (key === "scoring") setScoringNear(true);
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    document.getElementById(`${sectionsId}-${key}`)?.scrollIntoView({ block: "start", behavior: reduce ? "auto" : "smooth" });
  };

  // A link that names a section opens scrolled to it, once its panels have
  // something in them to scroll to.
  const opened = useRef(false);
  const ready = !!career.data && (season === null || !!gamelog.data);
  useEffect(() => {
    if (opened.current || !ready) return;
    opened.current = true;
    if (tab !== "overview" && tab !== "log") {
      document.getElementById(`${sectionsId}-${tab}`)?.scrollIntoView({ block: "start" });
    }
  }, [ready, tab, sectionsId]);

  // Previous and next keep the view's own rules and tab: stepping through the
  // board is for comparing players under the same scoring.
  const step = (to: SequenceEntry) => {
    const q = new URLSearchParams();
    if (season !== null) q.set("season", String(season));
    if (profileId !== null) q.set("profileId", String(profileId));
    if (tab !== "overview") q.set("tab", tab);
    router.replace(`/players/${to.playerId}?${q}`, { scroll: false });
  };

  const chrome = (
    <ViewBar
      variant={variant}
      season={validSeason}
      profileId={profileId}
      now={now}
      onClose={onClose}
      previous={previous}
      next={next}
      onStep={step}
    />
  );

  if (player.error || playerId <= 0) {
    const missing = playerId <= 0 || (player.error instanceof ApiError && player.error.status === 404);
    return (
      <Frame variant={variant}>
        {chrome}
        <div className="mt-4">
          <StatusMessage
            tone={missing ? "info" : "error"}
            title={missing ? "No player with that id." : "The player could not be loaded."}
            action={missing ? undefined : { label: "Try again", onClick: () => void player.refetch() }}
          >
            {missing
              ? "The link may be out of date. Every player on the board links to their own page."
              : player.error instanceof ApiError
                ? player.error.message
                : "The API did not answer. Check that it is running."}
          </StatusMessage>
        </div>
      </Frame>
    );
  }

  // Without the profile list no ruleset resolves, and every scored panel would
  // wait on a request that is never sent. Say so, with a way out.
  if (profiles.error) {
    return (
      <Frame variant={variant}>
        {chrome}
        <div className="mt-4">
          <StatusMessage
            tone="error"
            title="The scoring rulesets could not be loaded."
            action={{ label: "Try again", onClick: () => void profiles.refetch() }}
          >
            {profiles.error instanceof ApiError
              ? profiles.error.message
              : "The API did not answer. Check that it is running."}
          </StatusMessage>
        </div>
      </Frame>
    );
  }

  const sections = [
    { key: "log" as const, label: "Game log" },
    ...(ranked ? [{ key: "performance" as const, label: "Performance" }] : []),
    { key: "career" as const, label: "Career" },
    ...(ranked ? [{ key: "scoring" as const, label: "Scoring" }] : []),
  ];

  return (
    <Frame variant={variant}>
      {chrome}

      <div className="mt-2 grid grid-cols-[minmax(0,1fr)] gap-4 lg:grid-cols-[19.5rem_minmax(0,1fr)] lg:items-start lg:gap-5 xl:grid-cols-[21rem_minmax(0,1fr)]">
        {/* From lg the sidebar stays put while the column scrolls -- who he is
            and how he is scored never leave the screen -- and scrolls on its
            own if a long list of rulesets makes it taller than the window. */}
        <aside
          aria-label="Player"
          className={`grid min-w-0 grid-cols-[minmax(0,1fr)] gap-4 lg:sticky lg:overflow-y-auto lg:overscroll-contain lg:[scrollbar-width:thin] ${
            variant === "page" ? "lg:top-[4.5rem] lg:max-h-[calc(100dvh-5.5rem)]" : "lg:top-3 lg:max-h-[calc(100dvh-3rem)]"
          }`}
        >
          <div ref={identityRef}>
            {player.data ? (
              <IdentityCard
                player={player.data}
                season={season}
                selected={selected}
                careerLoading={career.isLoading || (profileId === null && !career.data)}
                careerFailed={!!career.error && !career.data}
                scoringLabel={scoringLabel}
                stale={careerStale}
                scored={ranked}
                overall={overall}
                current={now}
              />
            ) : (
              <Skeleton className="h-[34rem] rounded-panel" />
            )}
          </div>
          <ScoringCard
            profiles={profiles.data}
            profileId={profileId}
            onProfile={(id) => write({ profileId: String(id) })}
            seasons={played}
            season={season}
            onSeason={(s) => write({ season: String(s) })}
          />
        </aside>

        <div ref={mainRef} className="min-w-0">
          <SectionNav
            id={sectionsId}
            sections={sections}
            active={active}
            variant={variant}
            onPick={pick}
            mini={pastIdentity && player.data ? player.data : null}
          />

          {!ranked && player.data && (
            <p className="mt-4 text-sm text-mute">
              fantasy-kai scores and ranks QB, RB, WR and TE. {position} lines are shown as recorded, without
              points or positional ranks.
            </p>
          )}

          <div className="mt-4 grid grid-cols-[minmax(0,1fr)] gap-4">
            <div id={`${sectionsId}-log`} className={scrollMargin}>
              <Panel
                title="Game log"
                meta={season !== null ? `${season}, every game, playoffs included` : undefined}
                bodyClassName="pb-3"
              >
                {ranked && (
                  <div className="-mt-1 mb-3 px-5 sm:px-6">
                    <HeatLegend position={position} per="game" />
                  </div>
                )}
                <GameLog
                  position={position}
                  scored={ranked}
                  // No season to show once the career is back means no games at all:
                  // an empty log, not a skeleton waiting on a request never sent.
                  weeks={season === null && career.data ? [] : gamelog.data?.weeks}
                  loading={gamelog.isLoading}
                  error={gamelog.error}
                  onRetry={() => void gamelog.refetch()}
                  season={selected}
                  stale={gamelog.isPlaceholderData}
                />
              </Panel>
            </div>

            {ranked && (
              <div id={`${sectionsId}-performance`} className={scrollMargin}>
                <Panel title="Performance" meta={`Fantasy points by game, ${scoringLabel}`}>
                  {career.error ? (
                    <CareerError error={career.error} onRetry={() => void career.refetch()} />
                  ) : !career.data ? (
                    <Skeleton className="h-[300px]" />
                  ) : (
                    <PerformanceChart
                      key={`${playerId}-${profileId}`}
                      seasons={seasons}
                      position={position}
                      scoringLabel={scoringLabel}
                      stale={careerStale}
                    />
                  )}
                </Panel>
              </div>
            )}

            <div id={`${sectionsId}-career`} className={scrollMargin}>
              <Panel
                title="Career"
                meta="Regular season, newest first"
                action={
                  <SegmentedControl
                    legend="Career figures"
                    hideLegend
                    options={[
                      { value: "game", label: "Per game" },
                      { value: "total", label: "Totals" },
                    ]}
                    value={careerMode}
                    onChange={(v) => setCareerMode(v as CareerMode)}
                  />
                }
                bodyClassName="pb-3"
              >
                {career.error ? (
                  <div className="px-5 pb-2"><CareerError error={career.error} onRetry={() => void career.refetch()} /></div>
                ) : !career.data ? (
                  <div className="px-5 pb-2"><TableSkeleton rows={4} /></div>
                ) : seasons.length === 0 ? (
                  <p className="px-5 pb-2 text-[15px] text-mute sm:px-6">No regular-season games on record.</p>
                ) : (
                  <>
                    {ranked && (
                      <div className="-mt-1 mb-3 px-5 sm:px-6">
                        <HeatLegend position={position} per="season" />
                      </div>
                    )}
                    <CareerTable
                      position={position}
                      scored={ranked}
                      seasons={seasons}
                      selected={season}
                      onSelect={(s) => write({ season: String(s) })}
                      stale={careerStale}
                      mode={careerMode}
                    />
                  </>
                )}
              </Panel>
            </div>

            {ranked && season !== null && (
              <div id={`${sectionsId}-scoring`} className={scrollMargin}>
                <ScoringCompare
                  season={season}
                  position={position}
                  scored={allProfiles.map((p, i) => ({
                    profile: p,
                    data: careers[i]?.data,
                    loading: careers[i]?.isLoading ?? true,
                    error: !!careers[i]?.error,
                  }))}
                  selectedId={profileId}
                  onSelect={(id) => write({ profileId: String(id) })}
                />
              </div>
            )}
          </div>
        </div>
      </div>
    </Frame>
  );
}

/**
 * The pinned bar over the column: a link to each section, the current one
 * marked as the scroll passes it. Links, not tabs -- every panel is on the
 * page, and a link scrolls to its panel and names it in the URL. Once the
 * sidebar's card has scrolled away, the bar's left end carries his face,
 * name and position, so a long scroll never loses whose page it is.
 */
function SectionNav({
  id,
  sections,
  active,
  variant,
  onPick,
  mini,
}: {
  id: string;
  sections: { key: PlayerTab; label: string }[];
  active: PlayerTab;
  variant: "page" | "overlay";
  onPick: (key: PlayerTab) => void;
  mini: { name: string; position: string; espnId: string | null; team: string | null } | null;
}) {
  return (
    <div
      className={`sticky z-20 -mx-4 border-b border-line bg-surface px-4 sm:mx-0 sm:rounded-control sm:border sm:px-2 ${variant === "page" ? "top-14" : "top-0"}`}
    >
      <div className="flex items-center gap-2 sm:gap-4">
        {/* Below lg the sidebar is the top of the page and scrolls away; his
            face then rides at the bar's left end. From lg the sidebar stays. */}
        {mini && (
          <span title={mini.name} className="flex shrink-0 items-center gap-2 pl-0.5 lg:hidden">
            <PlayerAvatar name={mini.name} espnId={mini.espnId} size="sm" />
            <span className="sr-only">{mini.name}</span>
          </span>
        )}
        <nav aria-label="Player sections" className="-mb-px flex min-w-0 gap-1 overflow-x-auto">
          {sections.map((sec) => {
            const current = sec.key === active;
            return (
              <a
                key={sec.key}
                href={`#${id}-${sec.key}`}
                aria-current={current ? "location" : undefined}
                onClick={(e) => {
                  e.preventDefault();
                  onPick(sec.key);
                }}
                className={`relative flex h-11 shrink-0 items-center px-2.5 text-[14px] whitespace-nowrap transition-colors sm:px-3 ${
                  current
                    ? "font-semibold text-ink after:slash after:absolute after:inset-x-2 after:bottom-0 after:h-[3px] after:bg-energy"
                    : "text-mute hover:text-ink"
                }`}
              >
                {sec.label}
              </a>
            );
          })}
        </nav>
      </div>
    </div>
  );
}

/** The page's own width on a page; the overlay sets its own frame. */
function Frame({ variant, children }: { variant: "page" | "overlay"; children: React.ReactNode }) {
  return variant === "page" ? (
    <div className="mx-auto max-w-[1440px] px-4 pt-5 pb-10 sm:px-6 lg:px-8">{children}</div>
  ) : (
    <div className="px-4 pt-3 pb-10 sm:px-6 lg:px-8">{children}</div>
  );
}

/**
 * The bar above the stage: back to the board (a page) or close (the overlay),
 * and the previous and next player on the board, named, when the view was
 * opened from it.
 */
function ViewBar({
  variant,
  season,
  profileId,
  now,
  onClose,
  previous,
  next,
  onStep,
}: {
  variant: "page" | "overlay";
  season: number | null;
  profileId: number | null;
  now: number;
  onClose?: () => void;
  previous: SequenceEntry | null;
  next: SequenceEntry | null;
  onStep: (to: SequenceEntry) => void;
}) {
  return (
    <div className="flex min-h-12 items-center justify-between gap-3">
      {variant === "overlay" ? (
        <button type="button" onClick={onClose} className="inline-flex h-10 items-center gap-2 rounded-control pr-3 pl-1.5 text-[15px] text-mute transition-colors hover:bg-well hover:text-ink">
          <Icon name="arrowLeft" size={18} />
          Rankings
        </button>
      ) : (
        <BackToBoard season={season} profileId={profileId} now={now} />
      )}
      <div className="flex items-center gap-1">
        {(previous || next) && (
          <nav aria-label="Players on the board" className="flex items-center gap-1">
            <StepButton dir="previous" to={previous} onStep={onStep} />
            <StepButton dir="next" to={next} onStep={onStep} />
          </nav>
        )}
        {variant === "overlay" && (
          <button type="button" onClick={onClose} className={`${BUTTON_ICON} max-md:hidden`}>
            <Icon name="close" />
            <span className="sr-only">Close player view</span>
          </button>
        )}
      </div>
    </div>
  );
}

function StepButton({
  dir,
  to,
  onStep,
}: {
  dir: "previous" | "next";
  to: SequenceEntry | null;
  onStep: (to: SequenceEntry) => void;
}) {
  const label = to ? `${dir === "previous" ? "Previous" : "Next"} on the board: ${to.name}, ranked ${to.rank}` : `No ${dir} player`;
  return (
    <button
      type="button"
      disabled={!to}
      onClick={() => to && onStep(to)}
      aria-label={label}
      title={label}
      className="inline-flex h-10 items-center gap-2 rounded-control px-2.5 text-[14px] text-mute transition-colors hover:bg-well hover:text-ink disabled:pointer-events-none disabled:opacity-40"
    >
      {dir === "previous" && <Icon name="chevronLeft" size={18} />}
      {to && (
        <span className="hidden max-w-[12rem] truncate md:inline">
          <span className="type-figure mr-1.5 text-[13px] text-faint">{to.rank}</span>
          {to.name}
        </span>
      )}
      {dir === "next" && <Icon name="chevronRight" size={18} />}
    </button>
  );
}

/**
 * Back to the board. When the board is the page this one was opened from --
 * it noted itself on the way out -- this is the browser's back, so the board
 * comes back exactly as it was left. Otherwise (a shared link, a new tab) it
 * is a plain link to the board for this season and scoring.
 */
function BackToBoard({
  season,
  profileId,
  now,
}: {
  season: number | null;
  profileId: number | null;
  now: number;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const search = new URLSearchParams();
  if (season !== null && season !== now) search.set("season", String(season));
  if (profileId !== null) search.set("profileId", String(profileId));
  const query = search.toString();
  const href = `/rankings${query ? `?${query}` : ""}`;

  return (
    <Link
      href={href}
      onClick={(e) => {
        const saved = readBoardReturn();
        if (saved && saved.player === pathname && window.history.length > 1) {
          e.preventDefault();
          router.back();
        }
      }}
      className="inline-flex h-10 items-center gap-2 rounded-control pr-3 pl-1.5 text-[15px] text-mute transition-colors hover:bg-well hover:text-ink"
    >
      <Icon name="arrowLeft" size={18} /> Rankings
    </Link>
  );
}
