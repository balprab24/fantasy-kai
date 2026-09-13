"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useProfiles } from "@/lib/queries";
import { RulesetBuilder } from "@/components/RulesetBuilder";
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
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
      <h1 className="font-display text-2xl font-bold tracking-tight">Scoring rulesets</h1>
      <p className="mt-2 max-w-[66ch] text-[15px] leading-relaxed text-mute">
        A ruleset is the ruler. Rankings are recomputed against whichever one you pick, so a league
        with unusual settings gets numbers that are right for that league rather than approximately
        right for a generic one.
      </p>

      <section className="mt-10">
        <h2 className="font-display text-lg font-bold tracking-tight">Presets</h2>
        <ul className="mt-3 divide-y divide-line border-y border-line">
          {presets.map((profile) => (
            <li key={profile.id} className="flex items-center justify-between py-3">
              <span className="font-medium">{profile.name}</span>
              <Link
                href="/rankings"
                className="text-sm text-mute underline decoration-line-strong underline-offset-4 hover:text-ink"
              >
                Use it
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="mt-12">
        <h2 className="font-display text-lg font-bold tracking-tight">Yours</h2>

        {status === "signed-out" ? (
          <p className="mt-3 text-sm leading-relaxed text-mute">
            <Link
              href="/login"
              className="text-ink underline decoration-line-strong underline-offset-2"
            >
              Sign in
            </Link>{" "}
            to save a ruleset of your own. Presets and every ranking above stay public either way.
          </p>
        ) : mine.length === 0 && !building ? (
          <p className="mt-3 text-sm leading-relaxed text-mute">
            Nothing saved yet. Build one and it becomes selectable everywhere a ruleset is.
          </p>
        ) : (
          <ul className="mt-3 divide-y divide-line border-y border-line">
            {mine.map((profile) => (
              <li key={profile.id} className="flex items-center justify-between py-3">
                <span className="font-medium">{profile.name}</span>
                <button
                  type="button"
                  onClick={() => remove.mutate(profile.id)}
                  className="text-sm text-mute underline decoration-line-strong underline-offset-4 hover:text-ink"
                >
                  Delete
                </button>
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
            <button
              type="button"
              onClick={() => setBuilding(true)}
              className="mt-5 rounded bg-field px-4 py-2 font-medium text-white"
            >
              Build a ruleset
            </button>
          ))}
      </section>
    </div>
  );
}
