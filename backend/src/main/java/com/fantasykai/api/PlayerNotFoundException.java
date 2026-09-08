package com.fantasykai.api;

/** No player with that id. Mapped to 404 by {@link ApiExceptionHandler}. */
public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(long id) {
        super("no player with id " + id);
    }
}
