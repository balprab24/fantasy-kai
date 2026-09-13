"use client";

import Link from "next/link";
import { RulesetSwitch } from "@/components/RulesetSwitch";
import { RankingsBoard } from "@/components/RankingsBoard";
import { useRankings, useSelectedProfile } from "@/lib/queries";

const SEASON = 2025;

export default function Home() {
  const { profiles, profileId, setProfileId } = useSelectedProfile();

  const rankings = useRankings({
    profileId,
    season: SEASON,
    position: null,
    scope: "season",
    page: 0,
    size: 100,
  });

  return (
    <div className="mx-auto max-w-5xl px-4 sm:px-6">
      <section className="border-b border-line py-10 sm:py-12">
        <h1 className="font-display max-w-[18ch] text-[2rem] leading-[1.05] font-bold tracking-tight sm:text-[2.75rem]">
          One season. Four different answers.
        </h1>
        <p className="mt-4 max-w-[62ch] text-[15px] leading-relaxed text-mute">
          Nothing here stores a point total. Every number is computed when you ask for it, against
          the rules you picked — so changing the ruleset does not filter this board, it rewrites it.
          Switch between them and watch who moves.
        </p>

        <div className="mt-6">
          {profiles.data ? (
            <RulesetSwitch
              profiles={profiles.data}
              selected={profileId}
              onSelect={setProfileId}
            />
          ) : (
            <div className="h-[38px] w-72 rounded-md border border-line bg-raised" />
          )}
        </div>
      </section>

      <section className="py-7">
        <div className="mb-4 flex items-baseline justify-between gap-4">
          <h2 className="font-display text-lg font-bold tracking-tight">
            Top 100, {SEASON}
          </h2>
          <Link
            href="/rankings"
            className="text-sm text-mute underline decoration-line-strong underline-offset-4 hover:text-ink"
          >
            Filter and page through all of them
          </Link>
        </div>

        <RankingsBoard
          rows={rankings.data?.content}
          comparisonKey={String(profileId)}
          season={SEASON}
          profileId={profileId}
          loading={rankings.isLoading}
        />
      </section>
    </div>
  );
}
