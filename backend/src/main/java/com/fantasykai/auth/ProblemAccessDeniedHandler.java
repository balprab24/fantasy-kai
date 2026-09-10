package com.fantasykai.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 403 as {@code application/problem+json}. See {@link ProblemAuthenticationEntryPoint}. */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException e) throws IOException {
        Problems.write(response, HttpStatus.FORBIDDEN, "Forbidden",
                "you do not have access to that");
    }
}
