package com.fantasykai.leagues;

/**
 * The platform could not be reached, timed out, or our shared budget for it is
 * spent this minute. Shown as a 503: try again shortly, nothing is wrong with
 * the member's league.
 */
public class LeagueUnavailableException extends RuntimeException {

    public LeagueUnavailableException(String message) {
        super(message);
    }

    public LeagueUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
