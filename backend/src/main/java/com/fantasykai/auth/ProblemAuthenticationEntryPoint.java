package com.fantasykai.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 401 as {@code application/problem+json}.
 *
 * <p>Spring Security throws inside the filter chain, before {@code DispatcherServlet}
 * runs, so {@code ApiExceptionHandler} never sees an authentication failure --
 * without this the API answers every other error in RFC 7807 and answers 401
 * with an HTML error page. {@code ReadApiTests} asserts the content type on the
 * error path; auth holds the same bar.
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Request attribute carrying why the token was refused, when we know. */
    static final String REASON = "com.fantasykai.auth.reason";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException e) throws IOException {
        Object reason = request.getAttribute(REASON);
        Problems.write(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                reason instanceof String detail ? detail : "authentication is required");
    }
}
