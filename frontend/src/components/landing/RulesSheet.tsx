import { LIMITS } from "@/lib/ruleset";

/**
 * What a ruleset is, as the Scoring page builds one: every rate a league sets,
 * grouped the way a league's settings page groups them. The values are a real
 * ruleset -- Half PPR with 6-point passing touchdowns, the rules most of the
 * hero's mixer turns on -- and the two lines at the foot say what else the
 * builder takes, within the limits the server enforces (`LIMITS`). A sheet to
 * read, not a form: nothing on it is pressable.
 */
const GROUPS: { heading: string; rows: [string, string][] }[] = [
  {
    heading: "Passing",
    rows: [
      ["Yards", "1 per 25"],
      ["Touchdown", "6"],
      ["Interception", "−2"],
    ],
  },
  {
    heading: "Rushing",
    rows: [
      ["Yards", "1 per 10"],
      ["Touchdown", "6"],
    ],
  },
  {
    heading: "Receiving",
    rows: [
      ["Catch", "½"],
      ["Yards", "1 per 10"],
      ["Touchdown", "6"],
    ],
  },
  {
    heading: "Other",
    rows: [
      ["Fumble lost", "−2"],
      ["2-pt conversion", "2"],
    ],
  },
];

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
