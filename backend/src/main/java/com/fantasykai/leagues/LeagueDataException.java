package com.fantasykai.leagues;

/** A platform answered, but with something this code cannot read. Shown as a 502. */
public class LeagueDataException extends RuntimeException {

    public LeagueDataException(String message) {
        super(message);
    }

    public LeagueDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
