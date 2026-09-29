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
