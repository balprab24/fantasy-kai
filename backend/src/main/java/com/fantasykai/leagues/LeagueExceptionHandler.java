package com.fantasykai.leagues;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * League failures as RFC 7807, in {@code ApiExceptionHandler}'s shape. Scoped
 * to {@link LeagueController} so its {@code IllegalArgumentException} rule --
 * a malformed platform id is the caller's mistake, a 400 -- cannot reach
 * another controller, where that exception would mean a bug.
 */
@RestControllerAdvice(assignableTypes = LeagueController.class)
class LeagueExceptionHandler {

    @ExceptionHandler(LeagueNotFoundException.class)
    ProblemDetail notFound(LeagueNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "League not found", e.getMessage());
    }

    @ExceptionHandler(LeagueConflictException.class)
    ProblemDetail conflict(LeagueConflictException e) {
        return problem(HttpStatus.CONFLICT, "League already imported", e.getMessage());
    }

    @ExceptionHandler(LeagueNotScoredException.class)
    ProblemDetail notScored(LeagueNotScoredException e) {
        return problem(HttpStatus.CONFLICT, "League has no ruleset", e.getMessage());
    }

    @ExceptionHandler(LeagueSyncTooSoonException.class)
    ResponseEntity<ProblemDetail> tooSoon(LeagueSyncTooSoonException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.cooldown().toSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Synced recently", e.getMessage()));
    }

    @ExceptionHandler(LeagueUnavailableException.class)
    ProblemDetail unavailable(LeagueUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "League platform unavailable", e.getMessage());
    }

    @ExceptionHandler(LeagueDataException.class)
    ProblemDetail badData(LeagueDataException e) {
        return problem(HttpStatus.BAD_GATEWAY, "Unreadable league data", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid league request", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
