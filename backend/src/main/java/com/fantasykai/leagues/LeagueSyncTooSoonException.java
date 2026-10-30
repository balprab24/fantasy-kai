package com.fantasykai.leagues;

import java.time.Duration;

/** A sync inside the league's cooldown. A 429 carrying {@code Retry-After}. */
public class LeagueSyncTooSoonException extends RuntimeException {

    private final Duration cooldown;

    public LeagueSyncTooSoonException(Duration cooldown) {
        super("This league was synced in the last " + cooldown.toMinutes()
                + " minutes. Please try again later.");
        this.cooldown = cooldown;
    }

    public Duration cooldown() {
        return cooldown;
    }
}
