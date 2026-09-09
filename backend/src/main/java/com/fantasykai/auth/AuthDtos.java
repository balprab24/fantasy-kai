package com.fantasykai.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The request and response bodies for {@code /api/v1/auth}. Handoff §7.
 *
 * <p>These are the first request bodies in the codebase, which is why §8's
 * "Bean Validation on every DTO" row starts mattering here — the existing
 * {@code @Min}/{@code @Max} on query parameters covers none of it.
 */
final class AuthDtos {

    private AuthDtos() {}

    /**
     * @param password minimum 12, maximum 128. The floor is length rather than a
     *     composition rule because length is what actually resists guessing and
     *     composition rules mostly produce {@code Password1!}. The ceiling
     *     exists because Argon2's cost is paid on our CPU: without it, a 1 MB
     *     password is a free denial of service.
     */
    record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password) {}

    record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {}

    /**
     * The refresh token is deliberately absent: it goes back as an HttpOnly
     * cookie so script cannot read it, which is the entire reason for the
     * split. Only the short-lived access token crosses into JavaScript.
     *
     * @param expiresInSeconds so the client can refresh before a 401 rather than in response to one
     */
    record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {

        static TokenResponse bearer(String accessToken, long expiresInSeconds) {
            return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
        }
    }
}
