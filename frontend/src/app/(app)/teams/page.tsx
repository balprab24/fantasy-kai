"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "@/lib/api";
import { syncedAgo } from "@/lib/leagues";
import { currentSeason, seasons } from "@/lib/season";
import type { Discovery, League, LeagueSummary } from "@/lib/types";
import { SeasonSelect } from "@/components/ui/SeasonSelect";
import { Skeleton } from "@/components/ui/StatusMessage";
import { BUTTON_PRIMARY, BUTTON_SECONDARY, LINK_QUIET, PANEL } from "@/components/ui/buttons";

/** The API's own pattern (`LeagueDtos.USERNAME`), so a bad name is caught before a request. */
const USERNAME = /^[A-Za-z0-9_]{1,40}$/;

/**
 * My Teams: the member's imported leagues, and connecting a Sleeper account
 * to bring one in. Discovery stores nothing; importing a league is a separate,
 * deliberate press per league.
 */
export default function TeamsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const leagues = useQuery({
    queryKey: ["leagues"],
    queryFn: () => api<LeagueSummary[]>("/api/v1/leagues"),
  });

  const [username, setUsername] = useState("");
  const [season, setSeason] = useState(currentSeason());
  const [formError, setFormError] = useState<string | null>(null);

  const discover = useMutation({
    mutationFn: (input: { username: string; season: number }) =>
      api<Discovery>("/api/v1/leagues/sleeper/discover", { method: "POST", body: input }),
    onMutate: () => setFormError(null),
  });

  const importLeague = useMutation({
    mutationFn: (input: { username: string; externalLeagueId: string; season: number }) =>
      api<League>("/api/v1/leagues", { method: "POST", body: { platform: "sleeper", ...input } }),
    onSuccess: async (league) => {
      queryClient.setQueryData(["league", league.id], league);
      await queryClient.invalidateQueries({ queryKey: ["leagues"] });
      await queryClient.invalidateQueries({ queryKey: ["profiles"] });
      router.push(`/teams/${league.id}`);
    },
  });

  function find(event: React.FormEvent) {
    event.preventDefault();
    const name = username.trim();
    if (!USERNAME.test(name)) {
      setFormError("A Sleeper username is letters, numbers and underscores, up to 40 of them.");
      return;
    }
    discover.mutate({ username: name, season });
  }

  const found = discover.data;
  const searched = discover.variables;

  return (
    <div className="mx-auto max-w-[1440px] px-4 pt-8 pb-10 sm:px-6 lg:px-8">
      <h1 className="type-display text-[clamp(2.75rem,6vw,3.75rem)] text-ink">My Teams</h1>
      <p className="mt-3 max-w-[60ch] text-[16px] leading-relaxed text-mute">
        Bring in a league and Fantasy Kai reads it the way the league does: its scoring becomes a ruleset you
        can rank with, and its lineup slots, rosters and schedule come with it. Sleeper for now; ESPN and Yahoo
        are next.
      </p>

      <div className="mt-8 grid items-start gap-5 lg:grid-cols-2">
        <section aria-labelledby="leagues-title" className={PANEL}>
          <h2 id="leagues-title" className="type-heading px-5 pt-5 pb-4 sm:px-6">Your leagues</h2>
          {leagues.isPending ? (
            <div className="space-y-3 border-t border-line px-5 py-5 sm:px-6">
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-5 w-1/2" />
            </div>
          ) : leagues.isError ? (
            <p role="alert" className="border-t border-line px-5 py-5 text-[15px] text-danger sm:px-6">
              Your leagues could not be loaded.{" "}
              <button type="button" onClick={() => leagues.refetch()} className={LINK_QUIET}>
                Try again
              </button>
            </p>
          ) : leagues.data.length === 0 ? (
            <p className="border-t border-line px-5 py-5 text-[15px] leading-relaxed text-mute sm:px-6">
              No leagues yet. Find yours by your Sleeper username, then import the ones you want.
            </p>
          ) : (
            <ul className="border-t border-line">
              {leagues.data.map((league) => (
                <li key={league.id} className="border-b border-line last:border-b-0">
                  <Link
                    href={`/teams/${league.id}`}
                    className="flex min-h-16 items-center justify-between gap-6 px-5 py-3 transition-colors hover:bg-well sm:px-6"
                  >
                    <span className="min-w-0">
                      <span className="block truncate font-semibold text-ink">{league.name}</span>
                      <span className="block text-[13px] text-mute">
                        {league.season} · {league.numTeams} teams
                        {league.myTeamName ? ` · ${league.myTeamName}` : ""}
                      </span>
                    </span>
                    <span className="shrink-0 text-[13px] text-faint">Synced {syncedAgo(league.syncedAt)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-labelledby="connect-title" className={PANEL}>
          <h2 id="connect-title" className="type-heading px-5 pt-5 pb-1 sm:px-6">Connect Sleeper</h2>
          <p className="max-w-[56ch] px-5 pb-4 text-[15px] leading-relaxed text-mute sm:px-6">
            Your username is all it takes. Sleeper&rsquo;s league data is public, so there is no password to give
            and nothing secret is kept.
          </p>
          <form onSubmit={find} className="flex flex-wrap items-end gap-3 border-t border-line px-5 py-5 sm:px-6">
            <label className="flex min-w-48 flex-1 flex-col gap-1.5">
              <span className="type-label text-mute">Sleeper username</span>
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="off"
                autoCapitalize="none"
                spellCheck={false}
                maxLength={40}
                aria-invalid={formError !== null}
                aria-describedby={formError ? "connect-error" : undefined}
                className="h-10 rounded-control bg-well px-3.5 text-[15px] text-ink shadow-[inset_0_0_0_1px_var(--color-line-strong)] placeholder:text-faint hover:shadow-[inset_0_0_0_1px_var(--color-mute)]"
              />
            </label>
            <SeasonSelect value={season} options={seasons()} onChange={setSeason} variant="field" />
            <button type="submit" disabled={discover.isPending} className={BUTTON_PRIMARY}>
              {discover.isPending ? "Finding…" : "Find leagues"}
            </button>
          </form>

          {(formError || discover.isError) && (
            <p id="connect-error" role="alert" className="px-5 pb-5 text-[15px] text-danger sm:px-6">
              {formError ?? errorText(discover.error)}
            </p>
          )}

          {found && searched && (
            <div className="border-t border-line">
              <p className="px-5 pt-4 pb-3 text-[15px] text-mute sm:px-6">
                {found.leagues.length === 0
                  ? `${found.displayName} has no ${searched.season} NFL leagues on Sleeper.`
                  : `${found.displayName}'s ${searched.season} leagues on Sleeper.`}
              </p>
              <ul>
                {found.leagues.map((league) => {
                  const importing =
                    importLeague.isPending && importLeague.variables?.externalLeagueId === league.externalLeagueId;
                  return (
                    <li
                      key={league.externalLeagueId}
                      className="flex min-h-14 items-center justify-between gap-6 border-t border-line px-5 py-2 sm:px-6"
                    >
                      <span className="min-w-0">
                        <span className="block truncate font-semibold text-ink">{league.name}</span>
                        <span className="block text-[13px] text-mute">{league.numTeams} teams</span>
                      </span>
                      {league.importedLeagueId ? (
                        <Link href={`/teams/${league.importedLeagueId}`} className={`shrink-0 text-sm ${LINK_QUIET}`}>
                          Open
                        </Link>
                      ) : (
                        <button
                          type="button"
                          disabled={importLeague.isPending}
                          onClick={() =>
                            importLeague.mutate({
                              username: searched.username,
                              externalLeagueId: league.externalLeagueId,
                              season: searched.season,
                            })
                          }
                          className={`shrink-0 ${BUTTON_SECONDARY}`}
                        >
                          {importing ? "Importing…" : "Import"}
                        </button>
                      )}
                    </li>
                  );
                })}
              </ul>
              {importLeague.isError && (
                <p role="alert" className="border-t border-line px-5 py-4 text-[15px] text-danger sm:px-6">
                  {errorText(importLeague.error)}
                </p>
              )}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

/** The API's own words when it sent some; it writes `detail` for a person. */
function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : "Sleeper could not be reached. Try again in a moment.";
}
