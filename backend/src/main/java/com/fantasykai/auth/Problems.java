package com.fantasykai.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Writes an RFC 7807 body straight to the response.
 *
 * <p>Needed only on the filter-chain paths, which run outside MVC and so have
 * no message converters to hand. Everything reachable from a controller goes
 * through {@code ApiExceptionHandler} instead.
 */
final class Problems {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Problems() {}

    static void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        MAPPER.writeValue(response.getOutputStream(), problem);
    }
}
