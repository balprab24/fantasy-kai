package com.fantasykai.api;

import com.fantasykai.auth.EmailAlreadyRegisteredException;
import com.fantasykai.auth.InvalidTokenException;
import com.fantasykai.query.DuplicateProfileNameException;
import com.fantasykai.query.InvalidQueryParameterException;
import com.fantasykai.scoring.InvalidRulesetException;
import com.fantasykai.scoring.NoSuchProfileException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns the failures this API can produce into RFC 7807
 * {@code application/problem+json}, per §7.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring's own MVC failures
 * -- a missing required parameter, a non-numeric {@code page}, a {@code size}
 * past its bound -- come back in the same shape rather than as an HTML error
 * page. Without this the project had no advice at all, and an unknown
 * {@code ?profileId=} surfaced as a 500.
 *
 * <p>The ordering of the two ruleset handlers matters: {@link NoSuchProfileException}
 * is a subclass of {@link InvalidRulesetException}, and Spring picks the most
 * specific handler, so "no such profile" is a 404 while "these rules are not
 * valid" stays a 422.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(PlayerNotFoundException.class)
    ProblemDetail playerNotFound(PlayerNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Player not found", e.getMessage());
    }

    @ExceptionHandler(NoSuchProfileException.class)
    ProblemDetail profileNotFound(NoSuchProfileException e) {
        return problem(HttpStatus.NOT_FOUND, "Scoring profile not found", e.getMessage());
    }

    /** The rules exist but do not validate -- the request was understood and refused. */
    @ExceptionHandler(InvalidRulesetException.class)
    ProblemDetail invalidRuleset(InvalidRulesetException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid ruleset", e.getMessage());
    }

    /**
     * Bad credentials, and every flavour of unusable token, as one 401.
     *
     * <p>Reached only from a controller -- a token rejected inside the filter
     * chain never gets here, which is why {@code ProblemAuthenticationEntryPoint}
     * exists and writes the identical shape.
     */
    @ExceptionHandler(InvalidTokenException.class)
    ProblemDetail invalidToken(InvalidTokenException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", e.getMessage());
    }

    /**
     * 409 rather than 422: the body is well-formed and the rules are fine, the
     * conflict is with a row that already exists.
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail emailTaken(EmailAlreadyRegisteredException e) {
        return problem(HttpStatus.CONFLICT, "Email already registered", e.getMessage());
    }

    @ExceptionHandler(DuplicateProfileNameException.class)
    ProblemDetail duplicateProfileName(DuplicateProfileNameException e) {
        return problem(HttpStatus.CONFLICT, "Duplicate profile name", e.getMessage());
    }

    /** A sort, scope or position that is not on the whitelist. §8. */
    @ExceptionHandler(InvalidQueryParameterException.class)
    ProblemDetail invalidParameter(InvalidQueryParameterException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid query parameter", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
