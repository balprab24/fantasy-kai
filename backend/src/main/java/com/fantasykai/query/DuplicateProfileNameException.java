package com.fantasykai.query;

/** A user already has a scoring profile by that name. V5's partial unique index. */
public class DuplicateProfileNameException extends RuntimeException {

    public DuplicateProfileNameException(String name) {
        super("you already have a scoring profile named \"" + name + "\"");
    }
}
