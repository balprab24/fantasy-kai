"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "@/lib/api";
import {
  lineupSentence,
  perWeek,
  record,
  signedDiff,
  slotLabel,
  slotOrder,
  spotName,
  syncedAgo,
  unmodeledNotices,
  weakestSlot,
  weekSpan,
} from "@/lib/leagues";
import { useProfiles } from "@/lib/queries";
import type { League, LeagueTeam, PlayerOutlook, RosterSpot, TeamNeedsView } from "@/lib/types";
import { Icon } from "@/components/ui/Icon";
import { PositionChip } from "@/components/ui/PositionChip";
import { Skeleton, StatusMessage } from "@/components/ui/StatusMessage";
import { BUTTON_SECONDARY, LINK_QUIET, PANEL } from "@/components/ui/buttons";

const TYPE: Record<League["leagueType"], string> = {
  REDRAFT: "redraft",
  KEEPER: "keeper",
  DYNASTY: "dynasty",
  UNKNOWN: "league type unknown",
};

/**
 * One imported league: the member's roster by lineup slot, how the league
 * scores and what of that Fantasy Kai cannot model, the standings and the
 * schedule still to play. No points: the platform's are not stored, and ours
 * arrive with rest-of-season projections (plan S2).
 */
export default function LeaguePage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const profiles = useProfiles();
  const league = useQuery({
    queryKey: ["league", Number(id)],
    queryFn: () => api<League>(`/api/v1/leagues/${encodeURIComponent(id)}`),
  });
  const [confirming, setConfirming] = useState(false);
  // Computed on read from the league's own ruleset (plan S2); nothing to ask for without a team.
  const needs = useQuery({
    queryKey: ["league-needs", Number(id)],
    queryFn: () => api<TeamNeedsView>(`/api/v1/leagues/${encodeURIComponent(id)}/needs`),
    enabled: league.isSuccess && league.data.myTeamExternalId !== null,
    retry: false,
  });

  const store = (updated: League) => {
    queryClient.setQueryData(["league", updated.id], updated);
    void queryClient.invalidateQueries({ queryKey: ["leagues"] });
    void queryClient.invalidateQueries({ queryKey: ["league-needs", updated.id] });
  };

  const sync = useMutation({
    mutationFn: () => api<League>(`/api/v1/leagues/${encodeURIComponent(id)}/sync`, { method: "POST" }),
    onSuccess: (updated) => {
      store(updated);
      void queryClient.invalidateQueries({ queryKey: ["profiles"] });
    },
  });

  const chooseTeam = useMutation({
    mutationFn: (externalTeamId: string) =>
      api<League>(`/api/v1/leagues/${encodeURIComponent(id)}/my-team`, { method: "PUT", body: { externalTeamId } }),
    onSuccess: store,
  });

  const remove = useMutation({
    mutationFn: () => api<void>(`/api/v1/leagues/${encodeURIComponent(id)}`, { method: "DELETE" }),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ["league", Number(id)] });
      await queryClient.invalidateQueries({ queryKey: ["leagues"] });
      await queryClient.invalidateQueries({ queryKey: ["profiles"] });
      router.push("/teams");
    },
  });

  return (
    <div className="mx-auto max-w-[1440px] px-4 pt-6 pb-10 sm:px-6 lg:px-8">
      <Link href="/teams" className="inline-flex items-center gap-1.5 text-sm text-mute transition-colors hover:text-ink">
        <Icon name="arrowLeft" size={16} />
        My Teams
      </Link>

      {league.isPending ? (
        <div className="mt-4 space-y-4">
          <Skeleton className="h-14 w-2/3" />
          <Skeleton className="h-5 w-1/2" />
          <Skeleton className="mt-8 h-80 w-full" />
        </div>
      ) : league.isError ? (
        <div className="mt-6">
          {league.error instanceof ApiError && league.error.status === 404 ? (
            <StatusMessage tone="info" title="No such league">
              It may have been removed. <Link href="/teams" className={LINK_QUIET}>Back to your leagues</Link>.
            </StatusMessage>
          ) : (
            <StatusMessage tone="error" title="The league could not be loaded" action={{ label: "Try again", onClick: () => league.refetch() }}>
              {league.error instanceof ApiError ? league.error.message : "Check the API is running."}
            </StatusMessage>
          )}
        </div>
      ) : (
        <LeagueView
          league={league.data}
          profileName={profiles.data?.find((p) => p.id === league.data.scoringProfileId)?.name ?? null}
          needs={needs.data ?? null}
          needsState={needs.isPending ? "loading" : needs.isError ? "error" : "ready"}
          needsError={needs.isError ? errorText(needs.error) : null}
          onSync={() => sync.mutate()}
          syncing={sync.isPending}
          syncError={sync.isError ? errorText(sync.error) : null}
          onChooseTeam={(team) => chooseTeam.mutate(team)}
          choosing={chooseTeam.isPending}
          remove={
            <div className="mt-8 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
              {confirming ? (
                <>
                  <span className="text-mute">Remove this league and the ruleset its import made?</span>
                  <button
                    type="button"
                    onClick={() => remove.mutate()}
                    disabled={remove.isPending}
                    className="font-semibold text-danger"
                  >
                    {remove.isPending ? "Removing…" : "Remove it"}
                  </button>
                  <button type="button" onClick={() => setConfirming(false)} className={LINK_QUIET}>
                    Keep it
                  </button>
                </>
              ) : (
                // Destructive, so quiet until pointed at, as on the Scoring page.
                <button
                  type="button"
                  onClick={() => setConfirming(true)}
                  className="text-mute transition-colors hover:text-danger focus-visible:text-danger"
                >
                  Remove this league
                </button>
              )}
              {remove.isError && (
                <span role="alert" className="text-danger">
                  {errorText(remove.error)}
                </span>
              )}
            </div>
          }
        />
      )}
    </div>
  );
}

function LeagueView({
  league,
  profileName,
  needs,
  needsState,
  needsError,
  onSync,
  syncing,
  syncError,
  onChooseTeam,
  choosing,
  remove,
}: {
  league: League;
  profileName: string | null;
  needs: TeamNeedsView | null;
  needsState: "loading" | "error" | "ready";
  needsError: string | null;
  onSync: () => void;
  syncing: boolean;
  syncError: string | null;
  onChooseTeam: (externalTeamId: string) => void;
  choosing: boolean;
  remove: React.ReactNode;
}) {
  const mine = league.teams.find((team) => team.mine) ?? null;
  const notices = unmodeledNotices(league.unmodeled);

  return (
    <>
      <h1 className="type-display mt-3 text-[clamp(2.75rem,6vw,3.75rem)] break-words text-ink">{league.name}</h1>
      <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-3">
        <p className="text-[16px] leading-relaxed text-mute">
          Sleeper · {league.season} · {league.numTeams} teams · {TYPE[league.leagueType]}. Synced{" "}
          {syncedAgo(league.syncedAt)}.
        </p>
        <button type="button" onClick={onSync} disabled={syncing} className={BUTTON_SECONDARY}>
          {syncing ? "Syncing…" : "Sync now"}
        </button>
      </div>
      {syncError && (
        <p role="alert" className="mt-3 text-[15px] text-danger">
          {syncError}
        </p>
      )}

      <div className="mt-8 grid items-start gap-5 lg:grid-cols-12">
        <section aria-labelledby="team-title" className={`${PANEL} lg:col-span-7`}>
          <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 px-5 pt-5 pb-4 sm:px-6">
            <h2 id="team-title" className="type-heading min-w-0 break-words">
              {mine ? mine.name : "Your team"}
            </h2>
            <label className="flex items-center gap-2 text-sm text-mute">
              {mine ? "Not yours?" : "Which team is yours?"}
              <select
                value={mine?.externalTeamId ?? ""}
                disabled={choosing}
                onChange={(e) => e.target.value && onChooseTeam(e.target.value)}
                className="h-9 max-w-52 cursor-pointer rounded-control bg-well px-2.5 text-sm font-semibold text-ink shadow-[inset_0_0_0_1px_var(--color-line-strong)]"
              >
                {!mine && <option value="">Pick a team</option>}
                {league.teams.map((team) => (
                  <option key={team.externalTeamId} value={team.externalTeamId}>
                    {team.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {mine ? (
            <Roster team={mine} season={league.season} profileId={league.scoringProfileId} needs={needs} />
          ) : (
            <p className="border-t border-line px-5 py-5 text-[15px] text-mute sm:px-6">
              Your Sleeper account does not manage a team here. Pick the one that is yours to see its roster.
            </p>
          )}
          {league.unmatched > 0 && (
            <p className="border-t border-line px-5 py-4 text-[13px] leading-relaxed text-mute sm:px-6">
              {league.unmatched === 1 ? "One rostered player in this league is" : `${league.unmatched} rostered players in this league are`}{" "}
              not in Fantasy Kai&rsquo;s player list yet, so {league.unmatched === 1 ? "it shows" : "they show"} as not
              matched rather than as someone else.
            </p>
          )}
        </section>

        <div className="grid gap-5 lg:col-span-5">
          {mine && <NeedsPanel needs={needs} state={needsState} error={needsError} />}

          <section aria-labelledby="scoring-title" className={PANEL}>
            <h2 id="scoring-title" className="type-heading px-5 pt-5 pb-4 sm:px-6">Scoring</h2>
            <div className="space-y-3 border-t border-line px-5 py-5 text-[15px] leading-relaxed sm:px-6">
              {league.scoringProfileId !== null ? (
                <p className="text-mute">
                  Scored as the league scores it, saved as{" "}
                  <span className="font-semibold text-ink">{profileName ?? "this league's ruleset"}</span>.{" "}
                  <Link href={`/rankings?profileId=${league.scoringProfileId}`} className={LINK_QUIET}>
                    Rank with it
                  </Link>
                </p>
              ) : (
                <p className="text-danger">{league.scoringNote ?? "This league's scoring has no ruleset yet."}</p>
              )}
              <p className="text-mute">Starts {lineupSentence(league.rosterSlots)}.</p>
              {league.approximations.map((note) => (
                <p key={note} className="text-mute">{note}</p>
              ))}
            </div>
            {notices.length > 0 && (
              <ul className="border-t border-line">
                {notices.map((notice) => (
                  <li key={notice} className="flex gap-3 border-b border-line px-5 py-3 text-[14px] leading-relaxed text-mute last:border-b-0 sm:px-6">
                    <Icon name="info" size={16} className="mt-0.5 shrink-0 text-faint" />
                    {notice}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section aria-labelledby="standings-title" className={PANEL}>
            <h2 id="standings-title" className="type-heading px-5 pt-5 pb-4 sm:px-6">Standings</h2>
            <ol className="border-t border-line">
              {league.teams.map((team, i) => (
                <li
                  key={team.externalTeamId}
                  aria-current={team.mine ? "true" : undefined}
                  className={`flex min-h-12 items-center gap-4 border-b border-line px-5 py-2 last:border-b-0 sm:px-6 ${
                    team.mine ? "shadow-[inset_3px_0_0_var(--color-energy)]" : ""
                  }`}
                >
                  <span className="type-figure w-5 shrink-0 text-right text-mute tabular">{i + 1}</span>
                  <span className="min-w-0 flex-1">
                    <span className={`block truncate ${team.mine ? "font-semibold text-ink" : "text-ink"}`}>{team.name}</span>
                    {team.managerName && team.managerName !== team.name && (
                      <span className="block truncate text-[13px] text-faint">{team.managerName}</span>
                    )}
                  </span>
                  <span className="shrink-0 font-label font-semibold text-ink tabular">
                    {record(team.wins, team.losses, team.ties)}
                  </span>
                </li>
              ))}
            </ol>
          </section>

          <section aria-labelledby="schedule-title" className={PANEL}>
            <h2 id="schedule-title" className="type-heading px-5 pt-5 pb-4 sm:px-6">Still to play</h2>
            {!mine ? (
              <p className="border-t border-line px-5 py-5 text-[15px] text-mute sm:px-6">
                Pick your team to see who it plays.
              </p>
            ) : league.schedule.length === 0 ? (
              <p className="border-t border-line px-5 py-5 text-[15px] leading-relaxed text-mute sm:px-6">
                Sleeper has not paired the rest of the regular season yet. It fills in on a sync once it has.
              </p>
            ) : (
              <ul className="border-t border-line">
                {league.schedule.map((week) => (
                  <li key={week.week} className="flex min-h-11 items-center gap-4 border-b border-line px-5 py-2 last:border-b-0 sm:px-6">
                    <span className="w-16 shrink-0 font-label text-[13px] font-semibold text-mute tabular">Week {week.week}</span>
                    <span className="min-w-0 truncate text-ink">vs {week.opponentName ?? `team ${week.opponentExternalTeamId}`}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>

      {remove}
    </>
  );
}

/**
 * Each starting slot against the league median over the weeks left, from every team's best
 * lineup each week. One quality hue: a surplus is Starter Green, a deficit stays ink and carries
 * its minus sign -- never red, which is RB's (DESIGN.md, One Quality Hue Rule).
 */
function NeedsPanel({
  needs,
  state,
  error,
}: {
  needs: TeamNeedsView | null;
  state: "loading" | "error" | "ready";
  error: string | null;
}) {
  const weakest = needs ? weakestSlot(needs.slots) : null;
  return (
    <section aria-labelledby="needs-title" className={PANEL}>
      <h2 id="needs-title" className="type-heading px-5 pt-5 pb-1 sm:px-6">Team needs</h2>
      {state === "loading" ? (
        <div className="space-y-3 px-5 pt-3 pb-5 sm:px-6">
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-28 w-full" />
        </div>
      ) : state === "error" || !needs ? (
        <p role="alert" className="px-5 pt-2 pb-5 text-[15px] leading-relaxed text-danger sm:px-6">
          {error ?? "Team needs could not be worked out."}
        </p>
      ) : needs.weeks.length === 0 ? (
        <p className="px-5 pt-2 pb-5 text-[15px] leading-relaxed text-mute sm:px-6">
          No regular-season weeks are left before this league&rsquo;s playoffs, so there is nothing to project.
        </p>
      ) : (
        <>
          <p className="max-w-[56ch] px-5 pb-4 text-[15px] leading-relaxed text-mute sm:px-6">
            Your best lineup each week against the league&rsquo;s median team, in points a week over{" "}
            {weekSpan(needs.weeks)}.
            {weakest && (weakest.diff ?? 0) < 0 && (
              <>
                {" "}
                Furthest behind: <span className="font-semibold text-ink">{weakest.label}</span>,{" "}
                {signedDiff(weakest.diff ?? 0)} a week.
              </>
            )}
          </p>
          {/* Fixed layout: the name takes what the figures leave, instead of being squeezed to "Dr…". */}
          <table className="w-full table-fixed border-t border-line text-[14px]">
            <colgroup>
              <col className="w-[4.5rem] sm:w-20" />
              <col />
              <col className="w-14" />
              <col className="w-14" />
              <col className="w-[4.5rem] sm:w-20" />
            </colgroup>
            <thead className="sr-only">
              <tr>
                <th scope="col">Slot</th>
                <th scope="col">Your starter</th>
                <th scope="col">You, a week</th>
                <th scope="col">League median</th>
                <th scope="col">Difference</th>
              </tr>
            </thead>
            <tbody>
              {needs.slots.map((slot) => (
                <tr key={`${slot.slot}-${slot.label}`} className="border-b border-line last:border-b-0">
                  <th scope="row" className="py-2.5 pl-5 text-left font-label text-[13px] font-semibold text-mute sm:pl-6">
                    {slot.label}
                  </th>
                  {slot.modeled ? (
                    <>
                      <td className="truncate py-2.5 pr-3 text-ink">{slot.starterName ?? <span className="text-faint">Empty</span>}</td>
                      <td className="py-2.5 pr-3 text-right font-label font-semibold text-ink tabular">{perWeek(slot.mine ?? 0)}</td>
                      <td className="py-2.5 pr-3 text-right font-label text-faint tabular" title="League median">
                        {perWeek(slot.median ?? 0)}
                      </td>
                      <td
                        className={`py-2.5 pr-5 text-right font-label font-semibold tabular sm:pr-6 ${
                          (slot.diff ?? 0) > 0.05 ? "text-q-good" : "text-ink"
                        }`}
                      >
                        {signedDiff(slot.diff ?? 0)}
                      </td>
                    </>
                  ) : (
                    <td colSpan={4} className="py-2.5 pr-5 text-faint sm:pr-6">
                      Not modelled
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          {needs.noProjection > 0 && (
            <p className="border-t border-line px-5 py-3 text-[13px] leading-relaxed text-mute sm:px-6">
              {needs.noProjection === 1 ? "One rostered player has" : `${needs.noProjection} rostered players have`} no
              game to project from yet, so {needs.noProjection === 1 ? "counts" : "count"} as 0.
            </p>
          )}
        </>
      )}
    </section>
  );
}

function Roster({
  team,
  season,
  profileId,
  needs,
}: {
  team: LeagueTeam;
  season: number;
  profileId: number | null;
  needs: TeamNeedsView | null;
}) {
  const outlooks = new Map<string, PlayerOutlook>((needs?.players ?? []).map((p) => [p.externalPlayerId, p]));
  const spots = [...team.roster].sort((a, b) => slotOrder(a.slot) - slotOrder(b.slot));
  if (spots.length === 0) {
    return <p className="border-t border-line px-5 py-5 text-[15px] text-mute sm:px-6">This roster is empty.</p>;
  }
  return (
    <ul className="border-t border-line">
      {spots.map((spot) => (
        <li key={spot.externalPlayerId} className="flex min-h-12 items-center gap-4 border-b border-line px-5 py-2 last:border-b-0 sm:px-6">
          <span className="w-20 shrink-0 font-label text-[13px] font-semibold text-mute">{slotLabel(spot.slot)}</span>
          <span className="min-w-0 flex-1 truncate">
            <SpotName spot={spot} season={season} profileId={profileId} />
          </span>
          {spot.position && ["QB", "RB", "WR", "TE"].includes(spot.position) ? (
            <PositionChip position={spot.position} />
          ) : spot.position ? (
            <span className="font-label text-[12px] font-bold text-mute">{spot.position === "DST" ? "D/ST" : spot.position}</span>
          ) : null}
          <span className="w-10 shrink-0 text-right font-label text-[13px] text-faint">{spot.team ?? ""}</span>
          {needs && (
            <span
              className="w-14 shrink-0 text-right font-label text-[13px] text-mute tabular"
              title={outlooks.get(spot.externalPlayerId)?.perWeek != null ? "Expected points a week, rest of season" : undefined}
            >
              {outlooks.get(spot.externalPlayerId)?.perWeek != null
                ? perWeek(outlooks.get(spot.externalPlayerId)!.perWeek!)
                : "–"}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}

function SpotName({ spot, season, profileId }: { spot: RosterSpot; season: number; profileId: number | null }) {
  if (spot.kind === "PLAYER" && spot.playerId !== null) {
    return (
      <Link
        href={`/players/${spot.playerId}?season=${season}&profileId=${profileId ?? ""}`}
        className="font-semibold text-ink transition-colors hover:text-energy-text"
      >
        {spotName(spot)}
      </Link>
    );
  }
  return <span className={spot.kind === "UNMATCHED" ? "text-faint" : "font-semibold text-ink"}>{spotName(spot)}</span>;
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : "Sleeper could not be reached. Try again in a moment.";
}
