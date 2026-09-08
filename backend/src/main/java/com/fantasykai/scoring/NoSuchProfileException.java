package com.fantasykai.scoring;

/**
 * No scoring profile exists with the requested id or preset name.
 *
 * <p>Deliberately a subclass of {@link InvalidRulesetException} rather than a
 * sibling. "This profile does not exist" and "these rules are not valid" are
 * different answers -- 404 and 422 -- but every caller written before this type
 * existed catches the supertype, so widening the hierarchy costs nothing and
 * narrowing it would have broken them.
 */
public class NoSuchProfileException extends InvalidRulesetException {

    public NoSuchProfileException(String message) {
        super(message);
    }
}
