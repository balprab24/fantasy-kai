package com.fantasykai.projections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * A player's games before a target week, oldest first -- and the only way a provider sees one.
 *
 * <p>The cutoff is built in, not left to callers: {@link #before} keeps games strictly before the
 * target week and drops the target week itself, every later week and every later season, however
 * the collection it is handed was assembled. A provider cannot be given a history that knows the
 * future. This is the production twin of the research harness's {@code Timeline.asOf}, and
 * {@code PlayerHistoryTests} fails if the comparison ever admits the target week.
 */
public final class PlayerHistory {

    private final SeasonWeek target;
    private final List<HistoricalGame> games;

    private PlayerHistory(SeasonWeek target, List<HistoricalGame> games) {
        this.target = target;
        this.games = games;
    }

    /**
     * The games in {@code all} strictly before {@code target}, in the order they were played.
     *
     * @throws IllegalArgumentException if two games share a week: one player plays at most once
     *     a week, so a duplicate means the caller merged two sources wrongly
     */
    public static PlayerHistory before(SeasonWeek target, Collection<HistoricalGame> all) {
        List<HistoricalGame> kept = new ArrayList<>();
        for (HistoricalGame game : all) {
            if (game.when().isBefore(target)) {
                kept.add(game);
            }
        }
        kept.sort(Comparator.comparing(HistoricalGame::when));
        for (int i = 1; i < kept.size(); i++) {
            if (kept.get(i).when().equals(kept.get(i - 1).when())) {
                throw new IllegalArgumentException("two games in " + kept.get(i).when());
            }
        }
        return new PlayerHistory(target, List.copyOf(kept));
    }

    public SeasonWeek target() {
        return target;
    }

    /** Oldest first; nothing at or after {@link #target()}. */
    public List<HistoricalGame> games() {
        return games;
    }
}
