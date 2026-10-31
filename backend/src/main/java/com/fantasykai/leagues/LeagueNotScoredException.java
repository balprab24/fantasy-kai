package com.fantasykai.leagues;

/**
 * The league has no ruleset -- its scoring did not validate, or the member deleted the profile
 * its import made. A 409: nothing is projected under a guessed ruleset.
 */
public class LeagueNotScoredException extends RuntimeException {

    public LeagueNotScoredException(String message) {
        super(message);
    }
}
