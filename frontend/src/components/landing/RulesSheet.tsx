import { presetRates } from "@/lib/receipt";
import { LIMITS } from "@/lib/ruleset";
import { sheetRows } from "@/lib/rulesSheet";

/**
 * What a ruleset is, as the Scoring page builds one: every rate a league sets,
 * grouped the way a league's settings page groups them. The values are a real
 * ruleset -- Half PPR with 6-point passing touchdowns, the rules most of the
 * hero's mixer turns on -- worked out from V3's rates (`lib/rulesSheet.ts`,
 * held to the migration by `tests/lib.test.ts`), not typed. The two lines at
 * the foot say what else the builder takes, within the limits the server
 * enforces (`LIMITS`). A sheet to read, not a form: nothing on it is pressable.
 */
const GROUPS = sheetRows({ ...presetRates(0.5), pass_td: 6 });

export function RulesSheet() {
  return (
    <figure className="rounded-plate border border-line bg-surface">
      <figcaption className="flex items-baseline justify-between gap-4 border-b border-line px-5 py-4 sm:px-6">
        <span className="type-heading text-ink">My league</span>
        <span className="text-[13px] text-mute">A saved ruleset</span>
      </figcaption>
      <div className="grid grid-cols-2 gap-x-6 gap-y-5 px-5 py-5 sm:gap-x-8 sm:px-6">
        {GROUPS.map((g) => (
          <dl key={g.heading} className="min-w-0">
            <dt className="text-[13px] font-semibold text-mute">{g.heading}</dt>
            {g.rows.map(([what, value]) => (
              <dd key={what} className="flex items-baseline justify-between gap-4 border-b border-line py-2 text-[15px] last:border-b-0">
                <span className="text-ink">{what}</span>
                <span className="tabular font-label font-bold text-ink">{value}</span>
              </dd>
            ))}
          </dl>
        ))}
      </div>
      <div className="space-y-2 border-t border-line px-5 py-4 text-[14px] text-mute sm:px-6">
        <p>
          <span className="font-semibold text-ink">Position overrides.</span> Pay one position differently: a
          tight-end premium is one line.
        </p>
        <p>
          <span className="font-semibold text-ink">Bonuses.</span> Points for reaching a mark in a game, up to{" "}
          {LIMITS.maxBonuses} of them.
        </p>
      </div>
    </figure>
  );
}
