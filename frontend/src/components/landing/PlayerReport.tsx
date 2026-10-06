import { PositionBadge, positionHue, positionName } from "@/components/rankings/PositionBadge";
import { WeeklyChart } from "@/components/player/WeeklyChart";
import { formatPoints, STARTERS, starterWeeks } from "@/lib/board";
import { columnsFor, formatCount, GROUP_LABELS } from "@/lib/playerStats";
import { CareerLine } from "./CareerLine";
import { NACUA_2025_PPR } from "./previewData";

/**
 * The player the product band's board follows, opened: the player page's own
 * parts (`player/PlayerIdentity.tsx`, `WeeklyChart`, the career table) cut to
 * one season, in the product's skin. The receiver the rule change moved from
 * 20th to 2nd, so the page's story keeps one cast.
 *
 * No cut-out: the landing shows no player's likeness (owner decision
 * 2026-09-28, kept since), so the plate is type -- the name, the season line
 * and the box score -- which is what the photo stands beside in the app.
 * Every figure is the career capture's (`previewData.ts`); the stat line's
 * columns are chosen by `columnsFor`, the app's own rule.
 */
export function PlayerReport() {
  const season = NACUA_2025_PPR;
  const position = "WR";
  const groups = columnsFor(position, [season]);

  return (
    <article aria-labelledby="report-name" className="grid gap-x-10 gap-y-10 md:grid-cols-2 xl:grid-cols-1">
      <div className="rounded-plate bg-surface px-5 pt-6 pb-6 sm:px-7">
        <h3 id="report-name" className="type-display text-[clamp(2.5rem,4.4vw,3.5rem)] text-ink">
          Puka Nacua
        </h3>
        <p className="mt-2.5 flex flex-wrap gap-x-4 gap-y-1 text-[15px] text-mute">
          <span className={`font-semibold capitalize ${positionHue(position).text}`}>{positionName(position)}</span>
          <span>{season.teams.join(", ")}</span>
          <span className="tabular">Age {season.age} in {season.season}</span>
        </p>

        <dl className="mt-5 flex flex-wrap items-baseline gap-x-6 gap-y-2">
          <Figure label="Rank">
            <PositionBadge
              position={position}
              rank={season.posRank}
              basis={`by ${season.season} season points`}
              className="font-label text-[2rem] leading-none font-bold italic"
            />
          </Figure>
          <Figure label="Points" unit="pts">
            {formatPoints(season.points)}
          </Figure>
          <Figure label="Per game" unit="a game">
            {formatPoints(season.pointsPerGame)}
          </Figure>
        </dl>
        <p className="mt-2 text-[13px] text-mute">
          {season.season} regular season, {season.gamesPlayed} games, scored under PPR
        </p>

        {/* A phone keeps the stage to about one screen, so the weekly chart and the
            seasons stay in the app; the plate says what they would show -- the
            weeks in one line, as the hero's lower-third does -- and where they are. */}
        <div className="mt-4 border-t border-line pt-4 text-[14px] leading-snug sm:hidden">
          <p className="text-ink">
            A top-{STARTERS.WR} receiver in{" "}
            <span className="font-semibold">{starterWeeks(season.weeks, position)}</span> of {season.gamesPlayed}{" "}
            weeks.
          </p>
          <p className="mt-1 text-mute">Inside: every week charted, the full game log, every season since 2020.</p>
        </div>

        {/* The box score steps away on a phone: the figures above and the line below carry the season. */}
        <dl className="mt-4 hidden space-y-1 border-t border-line pt-4 text-[14px] sm:block">
          {groups.map((g) => (
            <div key={g.group} className="flex flex-wrap items-baseline gap-x-4 gap-y-0.5">
              <dt className="w-20 shrink-0 text-[13px] text-mute">{GROUP_LABELS[g.group]}</dt>
              <dd className="flex flex-wrap gap-x-4 gap-y-0.5">
                {g.columns.map((c) => (
                  <span key={c.key} title={c.title} className="whitespace-nowrap text-mute">
                    <span className="font-semibold text-ink">{formatCount(c.value(season))}</span>{" "}
                    {c.label.toLowerCase() === "yd" ? "yds" : c.label}
                  </span>
                ))}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      {/* From sm up: his weeks and his finished seasons, as the player page sets them. */}
      <div className="min-w-0 space-y-10 max-sm:hidden">
        <WeeklyChart season={season} position={position} scoringLabel="PPR" />
        <CareerLine />
      </div>
    </article>
  );
}

/** A figure of the season line, as the player page sets it: the number big, its unit small after it. */
function Figure({ label, unit, children }: { label: string; unit?: string; children: React.ReactNode }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <dt className="sr-only">{label}</dt>
      <dd className="flex items-baseline gap-1.5">
        <span className="type-stat text-[2rem] text-ink">{children}</span>
        {unit && <span className="text-[13px] text-mute">{unit}</span>}
      </dd>
    </div>
  );
}
