package com.fantasykai.leagues;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a league scores that Fantasy Kai does not, grouped and named so the
 * member reads "kicking isn't modelled yet", not forty raw keys.
 *
 * <p>Sleeper's names, measured on a captured league's 81 settings (2026-10-28).
 * An unknown key is still reported -- under its own name -- never dropped.
 */
public final class Unmodeled {

    public enum Group { OFFENSE, KICKING, TEAM_DEFENSE, DEFENSIVE_PLAYERS, ROSTER }

    /** @param label plain words when the key is known; the platform's key otherwise */
    public record Item(String key, String label, Double value, Group group) {}

    private static final Set<String> TEAM_DEFENSE = Set.of(
            "sack", "sack_yd", "int", "int_ret_yd", "fum_rec", "fum_ret_yd", "ff", "safe",
            "blk_kick", "blk_kick_ret_yd", "fg_ret_yd", "qb_hit", "tkl", "tkl_ast", "tkl_loss",
            "tkl_solo", "st_ff", "st_fum_rec", "st_tkl_solo", "st_td");

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("pass_att", "Pass attempts"), Map.entry("pass_cmp", "Completions"),
            Map.entry("pass_inc", "Incompletions"), Map.entry("pass_sack", "Sacks taken"),
            Map.entry("rush_att", "Rushing attempts"), Map.entry("pass_fd", "Passing first downs"),
            Map.entry("rush_fd", "Rushing first downs"), Map.entry("rec_fd", "Receiving first downs"),
            Map.entry("fum", "Fumbles, lost or not"), Map.entry("kr_yd", "Kick return yards"),
            Map.entry("pr_yd", "Punt return yards"), Map.entry("kr_td", "Kick return touchdowns"),
            Map.entry("pr_td", "Punt return touchdowns"),
            Map.entry("pass_cmp_40p", "Completions of 40+ yards"),
            Map.entry("pass_td_40p", "Passing touchdowns of 40+ yards"),
            Map.entry("rush_40p", "Runs of 40+ yards"), Map.entry("rush_td_40p", "Rushing touchdowns of 40+ yards"),
            Map.entry("rec_40p", "Catches of 40+ yards"), Map.entry("rec_td_40p", "Receiving touchdowns of 40+ yards"),
            Map.entry("bonus_rush_rec_yd_100", "Bonus for 100 rushing + receiving yards"),
            Map.entry("bonus_rush_rec_yd_200", "Bonus for 200 rushing + receiving yards"));

    private Unmodeled() {}

    static List<Item> describe(Map<String, Double> scoring, Map<String, Integer> slots) {
        List<Item> items = new ArrayList<>();
        scoring.forEach((key, value) -> items.add(
                new Item(key, LABELS.getOrDefault(key, key), value, group(key))));
        slots.forEach((name, count) -> items.add(new Item(name,
                count + " × " + name + " lineup slot", (double) count, Group.ROSTER)));
        return items;
    }

    static Group group(String key) {
        if (key.startsWith("idp_")) {
            return Group.DEFENSIVE_PLAYERS;
        }
        if (key.startsWith("fg") && !key.equals("fg_ret_yd") || key.startsWith("xp")) {
            return Group.KICKING;
        }
        if (key.startsWith("def_") || key.startsWith("pts_allow") || key.startsWith("yds_allow")
                || TEAM_DEFENSE.contains(key)) {
            return Group.TEAM_DEFENSE;
        }
        return Group.OFFENSE;
    }
}
