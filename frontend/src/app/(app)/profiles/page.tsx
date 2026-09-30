"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { profileLabel } from "@/lib/profiles";
import { useProfiles } from "@/lib/queries";
import { RulesetBuilder } from "@/components/RulesetBuilder";
import { BUTTON_PRIMARY, LINK_QUIET } from "@/components/ui/buttons";
import type { RulesetDoc } from "@/lib/ruleset";
import type { ScoringProfile } from "@/lib/types";

export default function ProfilesPage() {
  const { status } = useAuth();
  const profiles = useProfiles();
  const queryClient = useQueryClient();
  const [building, setBuilding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = useMutation({
    // `rules` goes over as a JSON *string*, not a nested object: the column is
    // jsonb and the server hands the raw text to RulesetValidator, which is
    // what lets it reject an unknown key by name instead of silently dropping
    // whatever Jackson could not bind. Sending an object here is a 400.
    mutationFn: (input: { name: string; rules: RulesetDoc }) =>
      api<ScoringProfile>("/api/v1/scoring-profiles", {
        method: "POST",
        body: { name: input.name, rules: JSON.stringify(input.rules) },
      }),
    onSuccess: async () => {
      setBuilding(false);
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ["profiles"] });
    },
    onError: (e) =>
      setError(e instanceof ApiError ? e.message : "Could not save. Check the API is running."),
  });

  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`/api/v1/scoring-profiles/${id}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["profiles"] }),
  });

  const presets = profiles.data?.filter((p) => p.preset) ?? [];
  const mine = profiles.data?.filter((p) => !p.preset) ?? [];

  return (
    <div className="mx-auto max-w-3xl px-4 pt-7 pb-10 sm:px-6">
      <h1 className="type-title">Scoring rulesets</h1>
      <p className="mt-3 max-w-[52ch] text-[15px] leading-relaxed text-mute">
        A ruleset is the ruler. Rankings are recomputed against whichever one you pick, so a league
        with unusual settings gets numbers that are right for that league rather than approximately
        right for a generic one.
      </p>

      <section className="mt-10">
        <h2 className="type-heading">Presets</h2>
        <ul className="mt-4 divide-y divide-line border-y border-line">
          {presets.map((profile) => (
            <li key={profile.id} className="flex h-12 items-center justify-between">
              <span className="font-semibold">{profileLabel(profile)}</span>
              <Link
                href={`/rankings?profileId=${profile.id}`}
                aria-label={`Rank with ${profileLabel(profile)}`}
                className={`text-sm ${LINK_QUIET}`}
              >
                Rank with it
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="mt-12">
        <h2 className="type-heading">Yours</h2>

        {/* No signed-out branch: this page sits behind the account gate
            (app/(app)/layout.tsx), so it only ever renders for a member. */}
        {mine.length === 0 && !building ? (
          <p className="mt-4 text-[15px] leading-relaxed text-mute">
            Nothing saved yet. Build one and it becomes selectable everywhere a ruleset is.
          </p>
        ) : (
          <ul className="mt-4 divide-y divide-line border-y border-line">
            {mine.map((profile) => (
              <li key={profile.id} className="flex h-12 items-center justify-between gap-6">
                <span className="min-w-0 truncate font-semibold">{profile.name}</span>
                <span className="flex shrink-0 items-center gap-8">
                  <Link
                    href={`/rankings?profileId=${profile.id}`}
                    aria-label={`Rank with ${profile.name}`}
                    className={`text-sm ${LINK_QUIET}`}
                  >
                    Rank with it
                  </Link>
                  {/* Destructive, so set apart and quiet: grey until pointed
                      at or focused, and only then the danger colour. */}
                  <button
                    type="button"
                    onClick={() => remove.mutate(profile.id)}
                    aria-label={`Delete ${profile.name}`}
                    className="text-sm text-mute transition-colors hover:text-danger focus-visible:text-danger"
                  >
                    Delete
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}

        {status === "signed-in" &&
          (building ? (
            <RulesetBuilder
              saving={create.isPending}
              serverError={error}
              onCancel={() => {
                setBuilding(false);
                setError(null);
              }}
              onSave={(name, rules) => create.mutate({ name, rules })}
            />
          ) : (
            <button type="button" onClick={() => setBuilding(true)} className={`mt-6 ${BUTTON_PRIMARY}`}>
              Build a ruleset
            </button>
          ))}
      </section>
    </div>
  );
}
