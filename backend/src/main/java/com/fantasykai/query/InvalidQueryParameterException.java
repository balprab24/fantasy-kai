package com.fantasykai.query;

/**
 * A request named something the query layer does not permit -- a sort column, a
 * scope or a position that is not on the whitelist.
 *
 * <p>Thrown by the whitelist resolvers rather than returned as a fallback,
 * because §8's rule is that an unrecognised name is rejected, never quietly
 * defaulted and never passed through to SQL.
 */
public class InvalidQueryParameterException extends RuntimeException {

    public InvalidQueryParameterException(String message) {
        super(message);
    }
}
