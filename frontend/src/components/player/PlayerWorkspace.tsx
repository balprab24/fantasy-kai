"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useId, useMemo, useSyncExternalStore } from "react";
import { ApiError } from "@/lib/api";
import { clearBoardReturn, readBoardReturn } from "@/lib/boardReturn";
import { boardSequenceSnapshot, neighbours, parseBoardSequence, type SequenceEntry } from "@/lib/boardSequence";
import { pickSeason } from "@/lib/player";
import { parseTab, type PlayerTab } from "@/lib/playerView";
import { profileLabel } from "@/lib/profiles";
import { useCareer, useCareers, useGamelog, useOverallRank, usePlayer, useSelectedProfile } from "@/lib/queries";
import { FIRST_SEASON, currentSeason } from "@/lib/season";
import { BUTTON_ICON } from "../ui/buttons";
import { Icon } from "../ui/Icon";
import { Skeleton, StatusMessage } from "../ui/StatusMessage";
import { TabPanel, Tabs } from "../ui/Tabs";
import { CareerSnapshot, Panel, RecentGames, SeasonLine } from "./PlayerPanels";
import { PlayerStage } from "./PlayerStage";
import { CareerError, CareerTable, GameLog, TableSkeleton } from "./PlayerTables";
import { ScoringCompare } from "./ScoringCompare";
import { WeeklyChart } from "./WeeklyChart";

const RANKED = ["QB", "RB", "WR", "TE"];

/** The sequence only changes when the board writes it, before this view mounts. */
const noSubscription = () => () => {};

function int(raw: string | null): number | null {
  return raw !== null && /^[1-9][0-9]{0,9}$/.test(raw) ? Number(raw) : null;
}

function games(n: number) {
  return `${n} ${n === 1 ? "game" : "games"}`;
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
 * Requests: the player (a primary-key read); the career under the chosen
 * ruleset (feeds the stage, the chart and the career); the season's game log;
 * the board's first page for his overall rank (shared with the board's cache);
 * and, only while the Scoring tab is open, the career under every other
 * ruleset. Season, scoring and tab live in the URL, written with
 * `replaceState`: a refresh or a shared link opens the same view, and back
 * goes to wherever you came from rather than through every season you tried.
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
  const params = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();
  const now = currentSeason();
  const tabsId = useId();

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
  const careers = useCareers(
    playerId,
    allProfiles.map((p) => p.id),
    tab === "scoring" && ranked,
  );

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

  const write = (change: Record<string, string | null>) => {
    const nextParams = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(change)) {
      if (value === null) nextParams.delete(key);
      else nextParams.set(key, value);
    }
    const query = nextParams.toString();
    window.history.replaceState(null, "", `${pathname}${query ? `?${query}` : ""}`);
  };
  const setTab = (t: PlayerTab) => write({ tab: t === "overview" ? null : t });

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

  const tabs = [
    { key: "overview" as const, label: "Overview" },
    { key: "log" as const, label: "Game log", meta: gamelog.data ? String(gamelog.data.weeks.length) : undefined },
    ...(ranked ? [{ key: "scoring" as const, label: "Scoring" }] : []),
    { key: "career" as const, label: "Career", meta: seasons.length ? String(seasons.length) : undefined },
  ];
  // A position v1 does not score has no Scoring tab; a URL asking for one gets the overview.
  const shownTab: PlayerTab = !ranked && tab === "scoring" ? "overview" : tab;

  return (
    <Frame variant={variant}>
      {chrome}

      <div className="mt-3">
        {player.data ? (
          <PlayerStage
            player={player.data}
            season={season}
            selected={selected}
            careerLoading={career.isLoading || (profileId === null && !career.data)}
            scoringLabel={scoringLabel}
            stale={careerStale}
            scored={ranked}
            overall={overall}
            profiles={profiles.data}
            profileId={profileId}
            onProfile={(id) => write({ profileId: String(id) })}
            seasons={played}
            onSeason={(s) => write({ season: String(s) })}
            current={now}
          />
        ) : (
          <Skeleton className="h-[26rem] rounded-plate" />
        )}
      </div>

      {/* The sections stay in reach while a long log scrolls: pinned under the
          site's bar on a page, at the top of the overlay's own scroll. */}
      <div
        className={`sticky z-20 -mx-4 mt-6 border-b border-line bg-veil px-4 backdrop-blur-md sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 ${variant === "page" ? "top-14" : "top-0"}`}
      >
        <Tabs id={tabsId} label="Player sections" items={tabs} value={shownTab} onChange={setTab} />
      </div>

      {!ranked && player.data && (
        <p className="mt-5 text-sm text-mute">
          fantasy-kai scores and ranks QB, RB, WR and TE. {position} lines are shown as recorded, without
          points or positional ranks.
        </p>
      )}

      <TabPanel tabsId={tabsId} tab={shownTab} className="mt-6 focus-visible:outline-none">
        {shownTab === "overview" && (
          <div className="grid gap-5 lg:grid-cols-12">
            <Panel
              title="Performance"
              meta={selected ? `${selected.season}, ${games(selected.gamesPlayed)}, ${scoringLabel}` : undefined}
              className="lg:col-span-8"
            >
              {career.error ? (
                <CareerError error={career.error} onRetry={() => void career.refetch()} />
              ) : !career.data ? (
                <Skeleton className="h-[270px]" />
              ) : selected && ranked ? (
                <WeeklyChart
                  key={`${selected.season}-${profileId}`}
                  season={selected}
                  position={position}
                  scoringLabel={scoringLabel}
                  stale={careerStale}
                  caption={false}
                />
              ) : (
                <p className="py-8 text-[15px] text-mute">
                  {seasons.length === 0
                    ? "No regular-season games on record."
                    : `No regular-season games in ${season}. Pick another season above.`}
                </p>
              )}
            </Panel>

            <Panel title="Recent games" meta={season !== null ? `${season}, newest first` : undefined} className="lg:col-span-4">
              {gamelog.error ? (
                <StatusMessage tone="error" title="The game log could not be loaded." action={{ label: "Try again", onClick: () => void gamelog.refetch() }}>
                  {gamelog.error instanceof ApiError ? gamelog.error.message : "The API did not answer."}
                </StatusMessage>
              ) : (
                <RecentGames
                  weeks={season === null && career.data ? [] : gamelog.data?.weeks}
                  position={position}
                  season={selected}
                  scored={ranked}
                  loading={gamelog.isLoading}
                  stale={gamelog.isPlaceholderData}
                />
              )}
              {gamelog.data && gamelog.data.weeks.length > 5 && (
                <button
                  type="button"
                  onClick={() => setTab("log")}
                  className="mt-3 w-full rounded-control py-2 text-[14px] text-energy-text transition-colors hover:bg-well"
                >
                  Every game in the log
                </button>
              )}
            </Panel>

            {selected && (
              <Panel title="Season line" meta={`${selected.season} regular season, box score`} className="lg:col-span-8">
                <SeasonLine position={position} season={selected} />
              </Panel>
            )}

            {seasons.length > 0 && (
              <Panel title="Seasons" meta="Points per game" className={selected ? "lg:col-span-4" : "lg:col-span-12"}>
                <CareerSnapshot
                  seasons={seasons}
                  position={position}
                  selected={season}
                  onSelect={(s) => write({ season: String(s) })}
                  scored={ranked}
                  stale={careerStale}
                />
              </Panel>
            )}
          </div>
        )}

        {shownTab === "log" && (
          <Panel
            title="Game log"
            meta={season !== null ? `${season}, every game, playoffs included` : undefined}
            bodyClassName="pb-0"
          >
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
        )}

        {shownTab === "scoring" && season !== null && (
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
        )}

        {shownTab === "career" && (
          <div className="grid gap-5 lg:grid-cols-12">
            <Panel title="By season" meta="Points per game, regular season" className="lg:col-span-4">
              {career.error ? (
                <CareerError error={career.error} onRetry={() => void career.refetch()} />
              ) : !career.data ? (
                <TableSkeleton rows={4} />
              ) : seasons.length === 0 ? (
                <p className="py-4 text-[15px] text-mute">No regular-season games on record.</p>
              ) : (
                <CareerSnapshot
                  seasons={seasons}
                  position={position}
                  selected={season}
                  onSelect={(s) => write({ season: String(s) })}
                  scored={ranked}
                  stale={careerStale}
                />
              )}
            </Panel>
            <Panel title="Career" meta="Regular season, newest first" className="lg:col-span-8" bodyClassName="pb-0">
              {career.error ? (
                <div className="px-5 pb-5"><CareerError error={career.error} onRetry={() => void career.refetch()} /></div>
              ) : !career.data ? (
                <div className="px-5 pb-5"><TableSkeleton rows={4} /></div>
              ) : seasons.length === 0 ? (
                <p className="px-5 pb-5 text-[15px] text-mute sm:px-6">No regular-season games on record.</p>
              ) : (
                <CareerTable
                  position={position}
                  scored={ranked}
                  seasons={seasons}
                  selected={season}
                  onSelect={(s) => write({ season: String(s) })}
                  stale={careerStale}
                />
              )}
            </Panel>
          </div>
        )}
      </TabPanel>
    </Frame>
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
