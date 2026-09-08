package com.fantasykai.api;

/** {@code /players/{id}}. Adds the nflverse canonical id to the summary fields. */
public record PlayerDetail(long id, String gsisId, String name, String position,
        String team, String status) {}
