package com.fantasykai.leagues;

/**
 * No such league, account or team -- on the platform, or among the caller's
 * own imports. Always a 404: for an import, a 403 would confirm that another
 * member's league id exists.
 */
public class LeagueNotFoundException extends RuntimeException {

    public LeagueNotFoundException(String message) {
        super(message);
    }
}
