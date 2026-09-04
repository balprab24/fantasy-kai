package com.fantasykai.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Invariants the schema cannot enforce on its own, re-checked after every ingest.
 *
 * <p>player_game_stats.season/week are denormalized copies of games.season/week
 * (handoff doc section 9). A composite foreign key would enforce that, but it
 * costs a unique index on games(id, season, week) that the section 9 baseline is
 * deliberately doing without. Ingestion is the only writer, so the invariant is
 * asserted here instead -- cheap, and it catches the bug the constraint would.
 */
@Component
public class IntegrityChecks {

    public static final String SEASON_WEEK_DRIFT = """
            SELECT count(*) FROM player_game_stats s
            JOIN games g ON g.id = s.game_id
            WHERE s.season <> g.season OR s.week <> g.week
            """;

    private static final Logger log = LoggerFactory.getLogger(IntegrityChecks.class);

    private final JdbcTemplate jdbc;

    public IntegrityChecks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return how many stat rows disagree with the game they point at; expected zero */
    public long seasonWeekDrift() {
        Long drifted = jdbc.queryForObject(SEASON_WEEK_DRIFT, Long.class);
        return drifted == null ? 0 : drifted;
    }

    /** Logs loudly rather than throwing: the data is already written, and a stale row beats no service. */
    public void verifyAfterIngest() {
        long drifted = seasonWeekDrift();
        if (drifted > 0) {
            log.error("integrity: {} stat rows disagree with their game on season/week", drifted);
        } else {
            log.info("integrity: season/week agree with the joined game on every stat row");
        }
    }
}
