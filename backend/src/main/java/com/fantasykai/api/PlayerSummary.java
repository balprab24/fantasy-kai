package com.fantasykai.api;

/** A player as {@code /players} lists them. Keyed on id -- names are not unique. */
public record PlayerSummary(long id, String name, String position, String team, String status) {}
