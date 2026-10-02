package com.smit.taskportal.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Answers {@code 403} for authenticated-but-unauthorised API calls. */
public class RestAwareAccessDeniedHandler implements AccessDeniedHandler {

    private final AuthenticationEntryPoint authenticationEntryPoint;

    public RestAwareAccessDeniedHandler(AuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Anonymous users are really "unauthenticated" -> 401 + login redirect.
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            authenticationEntryPoint.commence(request, response, new org.springframework.security.authentication.InsufficientAuthenticationException(
                    "Authentication required", accessDeniedException));
            return;
        }

        if (RestAwareAuthenticationEntryPoint.isApiRequest(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(Json.error("You do not have permission to perform this action"));
            return;
        }

        response.sendError(HttpStatus.FORBIDDEN.value());
    }
}
