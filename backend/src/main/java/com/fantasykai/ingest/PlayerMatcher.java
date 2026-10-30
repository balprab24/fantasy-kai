package com.fantasykai.ingest;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.HashMap;
import java.util.ArrayList;

/** Resolves provider identities without ever accepting an ambiguous name match. */
public final class PlayerMatcher {
    public record Player(long id, String gsisId, String name, String position,
                         LocalDate birthDate, Map<String, String> externalIds) {}
    public record Identity(String platform, String externalId, String gsisId, String espnId,
                           String name, String position, LocalDate birthDate) {}

    private final List<Player> players;
    private final Map<String, Long> overrides;
    private final Map<String, List<Player>> ids = new HashMap<>();
    private final Map<String, List<Player>> names = new HashMap<>();

    /** Override keys are platform + ':' + external id. */
    public PlayerMatcher(List<Player> players, Map<String, Long> overrides) {
        this.players = List.copyOf(players);
        this.overrides = Map.copyOf(overrides);
        for (Player player : players) {
            index(ids, "gsis", player.gsisId(), player);
            player.externalIds().forEach((platform, id) -> index(ids, platform, id, player));
            names.computeIfAbsent(normalize(player.name()), ignored -> new ArrayList<>()).add(player);
        }
    }

    public OptionalLong match(Identity identity) {
        // An override is a reviewed human decision, so it outranks every heuristic below --
        // checked last, it could fill a gap but never correct a wrong name match.
        Long override = overrides.get(identity.platform() + ":" + identity.externalId());
        if (override != null && players.stream().anyMatch(p -> p.id() == override)) {
            return OptionalLong.of(override);
        }
        List<Player> hits = lookup(identity.platform(), identity.externalId());
        if (!hits.isEmpty()) return unique(hits);
        hits = lookup("gsis", identity.gsisId());
        if (!hits.isEmpty()) return unique(hits);
        hits = lookup("espn", identity.espnId());
        if (!hits.isEmpty()) return unique(hits);
        String name = normalize(identity.name());
        if (!name.isEmpty()) {
            hits = names.getOrDefault(name, List.of()).stream().filter(p -> equalId(p.position(), identity.position())).toList();
            if (hits.size() > 1 && identity.birthDate() != null) {
                hits = hits.stream().filter(p -> identity.birthDate().equals(p.birthDate())).toList();
            }
            if (hits.size() == 1) return unique(hits);
            // A supplied date permits a cross-position match (e.g. FB listed as RB).
            if (identity.birthDate() != null) {
                hits = names.getOrDefault(name, List.of()).stream()
                        .filter(p -> identity.birthDate().equals(p.birthDate())).toList();
                if (hits.size() == 1) return unique(hits);
            }
        }
        return OptionalLong.empty();
    }

    private static void index(Map<String, List<Player>> index, String platform, String id, Player player) {
        if (id != null && !id.isBlank()) {
            index.computeIfAbsent(platform + ":" + id.trim(), ignored -> new ArrayList<>()).add(player);
        }
    }

    private List<Player> lookup(String platform, String id) {
        return id == null || id.isBlank() ? List.of() : ids.getOrDefault(platform + ":" + id.trim(), List.of());
    }

    private static OptionalLong unique(List<Player> hits) {
        return hits.size() == 1 ? OptionalLong.of(hits.getFirst().id()) : OptionalLong.empty();
    }

    private static boolean equalId(String left, String right) {
        return left != null && right != null && !left.isBlank() && left.trim().equals(right.trim());
    }

    static String normalize(String name) {
        if (name == null) return "";
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "").trim()
                .replaceAll("\\s+", " ")
                .replaceAll("(?: (?:jr|sr|ii|iii|iv|v))+$", "");
        // A narrow, tested alias; do not apply broad nickname guessing.
        return normalized.replaceFirst("^joshua ", "josh ");
    }
}
