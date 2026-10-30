package com.fantasykai.leagues;

/** The member already imported this league for this season, or synced it too recently. */
public class LeagueConflictException extends RuntimeException {

    public LeagueConflictException(String message) {
        super(message);
    }
}
