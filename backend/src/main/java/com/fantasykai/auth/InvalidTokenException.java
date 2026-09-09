package com.fantasykai.auth;

/** A token that is absent, expired, forged, replayed or otherwise not usable. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
