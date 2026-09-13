"use client";

import { useState } from "react";
import {
  ALL_STATS,
  LIMITS,
  OVERRIDE_POSITIONS,
  STAT_GROUPS,
  localProblems,
  startingRuleset,
  type OverridePosition,
  type RulesetDoc,
} from "@/lib/ruleset";
import type { StatKey } from "@/lib/types";
import { RateInput } from "./RateInput";

export function RulesetBuilder({
  onSave,
  onCancel,
  saving,
  serverError,
}: {
  onSave: (name: string, rules: RulesetDoc) => void;
  onCancel: () => void;
  saving: boolean;
  serverError: string | null;
}) {
  const [name, setName] = useState("");
  const [doc, setDoc] = useState<RulesetDoc>(startingRuleset);

  const problems = localProblems(doc);
  const overrides = doc.position_overrides ?? {};
  const bonuses = doc.bonuses ?? [];

  function setRate(stat: StatKey, value: string) {
    setDoc((d) => ({ ...d, base: { ...d.base, [stat]: value === "" ? 0 : Number(value) } }));
  }

  function setOverride(position: OverridePosition, stat: StatKey, value: string) {
    setDoc((d) => ({
      ...d,
      position_overrides: {
        ...d.position_overrides,
        [position]: { ...(d.position_overrides?.[position] ?? {}), [stat]: Number(value || 0) },
      },
    }));
  }

  function dropOverride(position: OverridePosition) {
    setDoc((d) => {
      const next = { ...(d.position_overrides ?? {}) };
      delete next[position];
      return { ...d, position_overrides: Object.keys(next).length ? next : undefined };
    });
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSave(name, doc);
      }}
      className="mt-6 border-t border-line pt-6"
    >
      <label className="block max-w-sm">
        <span className="text-sm text-mute">Ruleset name</span>
        <input
          required
          maxLength={64}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Bob's dynasty league"
          className="mt-1 w-full rounded border border-line-strong bg-raised px-3 py-2"
        />
      </label>

      <div className="mt-8 grid gap-x-10 gap-y-7 sm:grid-cols-2">
        {STAT_GROUPS.map((group) => (
          <fieldset key={group.heading}>
            <legend className="font-display text-[15px] font-bold tracking-tight">
              {group.heading}
            </legend>
            <div className="mt-2 space-y-2">
              {group.stats.map(([stat, label]) => (
                <label key={stat} className="flex items-center justify-between gap-4">
                  <span className="text-sm text-mute">{label}</span>
                  <RateInput
                    step="0.01"
                    min={-LIMITS.rate}
                    max={LIMITS.rate}
                    value={doc.base[stat]}
                    onChange={(v) => setRate(stat, v)}
                    className="w-24"
                  />
                </label>
              ))}
            </div>
          </fieldset>
        ))}
      </div>

      <section className="mt-10">
        <h3 className="font-display text-[15px] font-bold tracking-tight">By position</h3>
        <p className="mt-1 max-w-[62ch] text-sm leading-relaxed text-mute">
          Override a rate for one position only. A TE-premium league is this and nothing else: tight
          ends get a higher rate per reception than everyone else.
        </p>

        <div className="mt-3 space-y-2">
          {(Object.keys(overrides) as OverridePosition[]).map((position) => (
            <div key={position} className="flex flex-wrap items-center gap-2 text-sm">
              <span className="w-10 font-medium">{position}</span>
              {Object.entries(overrides[position] ?? {}).map(([stat, rate]) => (
                <span key={stat} className="flex items-center gap-2">
                  <span className="text-mute">{stat}</span>
                  <RateInput
                    step="0.01"
                    value={rate}
                    onChange={(v) => setOverride(position, stat as StatKey, v)}
                    className="w-20"
                  />
                </span>
              ))}
              <button
                type="button"
                onClick={() => dropOverride(position)}
                className="text-mute underline decoration-line-strong underline-offset-2 hover:text-ink"
              >
                Remove
              </button>
            </div>
          ))}
        </div>

        <AddOverride
          taken={Object.keys(overrides) as OverridePosition[]}
          onAdd={(position, stat) => setOverride(position, stat, String(doc.base[stat]))}
        />
      </section>

      <section className="mt-10">
        <h3 className="font-display text-[15px] font-bold tracking-tight">Bonuses</h3>
        <p className="mt-1 max-w-[62ch] text-sm leading-relaxed text-mute">
          Extra points once a stat reaches a threshold in a single game. These are what make scoring
          non-linear, which is why a season total is the sum of scored games and never one score
          over summed stats.
        </p>

        <div className="mt-3 space-y-2">
          {bonuses.map((bonus, i) => (
            <div key={i} className="flex flex-wrap items-center gap-2 text-sm">
              <select
                value={bonus.stat}
                onChange={(e) =>
                  setDoc((d) => ({
                    ...d,
                    bonuses: (d.bonuses ?? []).map((b, j) =>
                      j === i ? { ...b, stat: e.target.value as StatKey } : b,
                    ),
                  }))
                }
                className="rounded border border-line-strong bg-raised px-2 py-1"
              >
                {ALL_STATS.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
              <span className="text-mute">at or above</span>
              <RateInput
                value={bonus.gte}
                min={0}
                max={LIMITS.bonusThreshold}
                onChange={(v) =>
                  setDoc((d) => ({
                    ...d,
                    bonuses: (d.bonuses ?? []).map((b, j) =>
                      j === i ? { ...b, gte: Number(v || 0) } : b,
                    ),
                  }))
                }
                className="w-24"
              />
              <span className="text-mute">is worth</span>
              <RateInput
                step="0.5"
                value={bonus.points}
                onChange={(v) =>
                  setDoc((d) => ({
                    ...d,
                    bonuses: (d.bonuses ?? []).map((b, j) =>
                      j === i ? { ...b, points: Number(v || 0) } : b,
                    ),
                  }))
                }
                className="w-20"
              />
              <button
                type="button"
                onClick={() =>
                  setDoc((d) => ({ ...d, bonuses: (d.bonuses ?? []).filter((_, j) => j !== i) }))
                }
                className="text-mute underline decoration-line-strong underline-offset-2 hover:text-ink"
              >
                Remove
              </button>
            </div>
          ))}
        </div>

        {bonuses.length < LIMITS.maxBonuses && (
          <button
            type="button"
            onClick={() =>
              setDoc((d) => ({
                ...d,
                bonuses: [...(d.bonuses ?? []), { stat: "rush_yd", gte: 100, points: 3 }],
              }))
            }
            className="mt-3 rounded border border-line-strong px-3 py-1.5 text-sm hover:bg-field-soft"
          >
            Add a bonus
          </button>
        )}
      </section>

      {(problems.length > 0 || serverError) && (
        <ul
          role="alert"
          className="mt-8 space-y-1 rounded border border-stat-loss/30 bg-stat-loss/5 px-3 py-2 text-sm text-stat-loss"
        >
          {serverError && <li>{serverError}</li>}
          {problems.map((p) => (
            <li key={p}>{p}</li>
          ))}
        </ul>
      )}

      <div className="mt-8 flex gap-3">
        <button
          type="submit"
          disabled={saving || problems.length > 0}
          className="rounded bg-field px-4 py-2 font-medium text-white disabled:opacity-60"
        >
          {saving ? "Saving…" : "Save ruleset"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="rounded border border-line-strong px-4 py-2 hover:bg-field-soft"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}

function AddOverride({
  taken,
  onAdd,
}: {
  taken: OverridePosition[];
  onAdd: (position: OverridePosition, stat: StatKey) => void;
}) {
  const available = OVERRIDE_POSITIONS.filter((p) => !taken.includes(p));
  const [position, setPosition] = useState<OverridePosition>(available[0] ?? "TE");
  const [stat, setStat] = useState<StatKey>("rec");

  if (available.length === 0) return null;

  return (
    <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
      <select
        value={position}
        onChange={(e) => setPosition(e.target.value as OverridePosition)}
        className="rounded border border-line-strong bg-raised px-2 py-1"
      >
        {available.map((p) => (
          <option key={p} value={p}>
            {p}
          </option>
        ))}
      </select>
      <select
        value={stat}
        onChange={(e) => setStat(e.target.value as StatKey)}
        className="rounded border border-line-strong bg-raised px-2 py-1"
      >
        {ALL_STATS.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </select>
      <button
        type="button"
        onClick={() => onAdd(position, stat)}
        className="rounded border border-line-strong px-3 py-1.5 hover:bg-field-soft"
      >
        Add override
      </button>
    </div>
  );
}
