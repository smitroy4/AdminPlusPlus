package com.smit.taskportal.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Returns a JSON {@code 401} for API calls and a redirect to the login page for
 * browser navigations, so the SPA never has to parse an HTML error page.
 */
public class RestAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String API_PREFIX = "/api/";

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        if (isApiRequest(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(Json.error("Authentication required"));
            return;
        }

        String target = request.getRequestURI();
        response.sendRedirect(request.getContextPath() + "/login.html?next="
                + java.net.URLEncoder.encode(target, StandardCharsets.UTF_8));
    }

    static boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = (context != null && !context.isEmpty() && uri.startsWith(context))
                ? uri.substring(context.length())
                : uri;
        return path.startsWith(API_PREFIX);
    }
}
