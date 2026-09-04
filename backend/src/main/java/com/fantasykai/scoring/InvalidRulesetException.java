package com.fantasykai.scoring;

/** A ruleset that cannot be stored or evaluated. Carries a message meant for the caller. */
public class InvalidRulesetException extends RuntimeException {

    public InvalidRulesetException(String message) {
        super(message);
    }
}
