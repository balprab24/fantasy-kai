import type { ScoringProfile } from "./types";

/**
 * What a preset is called on screen.
 *
 * The database names the three reception rates "Standard", "Half PPR" and
 * "Full PPR". "Standard" stopped meaning anything once most leagues made PPR
 * their standard, so the product says what the setting *is*: 0, half or full
 * point per reception. Display only -- the API, the seed migration and every
 * test keep the stored names, and so does anything that keys on them.
 *
 * Keyed on `preset && name`: a user is free to call their own ruleset
 * "Standard", and that one is theirs to name.
 */
const PRESET_LABELS: Record<string, string> = {
  Standard: "0 PPR",
  "Full PPR": "PPR",
};

export function profileLabel(profile: Pick<ScoringProfile, "name" | "preset">): string {
  return (profile.preset && PRESET_LABELS[profile.name]) || profile.name;
}

/**
 * What a preset pays for the one thing the presets disagree on: a catch
 * (V3__seed_scoring_presets.sql; `tests/lib.test.ts` parses the migration and
 * holds these words to its rates). Shown under each option of the scoring
 * switch, so the choice explains itself. A member's own ruleset says only
 * that it is theirs -- its rules are not readable back from the API, and a
 * caption guessed from its name would be a claim the product cannot back.
 */
const PRESET_CAPTIONS: Record<string, string> = {
  Standard: "0 per catch",
  "Half PPR": "½ per catch",
  "Full PPR": "1 per catch",
  "TE Premium": "TE 1.5 per catch",
};

export function profileCaption(profile: Pick<ScoringProfile, "name" | "preset">): string {
  return (profile.preset && PRESET_CAPTIONS[profile.name]) || "Your ruleset";
}
