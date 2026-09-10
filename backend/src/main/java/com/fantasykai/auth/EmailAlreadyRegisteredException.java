package com.fantasykai.auth;

/** Registration against an email that already has an account. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
