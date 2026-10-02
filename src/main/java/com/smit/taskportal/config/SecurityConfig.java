package com.smit.taskportal.config;

import com.smit.taskportal.repository.UserRepository;
import com.smit.taskportal.security.AppUserPrincipal;
import com.smit.taskportal.security.RestAwareAccessDeniedHandler;
import com.smit.taskportal.security.RestAwareAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * Form-login (browser) + session based security for the whole portal.
 *
 * <ul>
 *   <li>BCrypt password encoding</li>
 *   <li>CSRF protection <b>enabled</b> for every mutating request, with the
 *       token delivered through a JS-readable {@code XSRF-TOKEN} cookie</li>
 *   <li>{@code /api/**} requires authentication and answers with JSON 401/403
 *       instead of an HTML redirect</li>
 *   <li>{@code /api/manager/**} requires MANAGER or ADMIN, {@code /api/admin/**}
 *       requires ADMIN</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Header the SPA sets to distinguish AJAX calls from plain form posts. */
    public static final String AJAX_HEADER = "X-Requested-With";

    /** Custom request header the browser must echo the CSRF token back in. */
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    /** Form parameter fallback, used by plain HTML forms if any are ever added. */
    public static final String CSRF_PARAMETER = "_csrf";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final String[] PUBLIC_RESOURCES = {
            "/", "/index.html", "/login.html",
            "/css/**", "/js/**", "/images/**", "/favicon.ico",
            "/error", "/actuator/health", "/api/csrf"
    };

    // ------------------------------------------------------------------ beans

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Deliberately not exposed as a {@code UserDetailsService} bean: Spring Security
     * then autowires it into {@link #authenticationProvider}, and declaring both
     * makes the container complain that the {@code UserDetailsService} will be
     * ignored for username/password login.
     */
    @Bean
    public AuthenticationProvider authenticationProvider(UserRepository userRepository,
                                                         PasswordEncoder passwordEncoder) {
        UserDetailsService userDetailsService = username -> userRepository.findByUsername(username)
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for username: " + username));

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Never leak "user exists but wrong password" through the login error.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    // ----------------------------------------------------------- filter chain

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationProvider authenticationProvider) throws Exception {

        RestAwareAuthenticationEntryPoint entryPoint = new RestAwareAuthenticationEntryPoint();
        RestAwareAccessDeniedHandler accessDeniedHandler = new RestAwareAccessDeniedHandler(entryPoint);

        http
                .authenticationProvider(authenticationProvider)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // Runs ahead of CsrfFilter and rejects unsafe requests that never
                // echo the token back in a header / form field.
                .addFilterBefore(new CsrfTokenDeliveryFilter(accessDeniedHandler), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_RESOURCES).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/manager/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(authenticationSuccessHandler())
                        .failureHandler(authenticationFailureHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("TPJSESSIONID", "XSRF-TOKEN")
                        .permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .frameOptions(Customizer.withDefaults())
                        .contentTypeOptions(Customizer.withDefaults())
                        .cacheControl(Customizer.withDefaults()));

        // Runs straight after CsrfFilter so the JS-readable XSRF-TOKEN cookie is
        // always written, including on the very first (unauthenticated) request.
        http.addFilterAfter(new CsrfCookieEagerLoadFilter(), CsrfFilter.class);

        return http.build();
    }

    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setHeaderName(CSRF_HEADER);
        repository.setParameterName(CSRF_PARAMETER);
        return repository;
    }

    // ---------------------------------------------------------------- handlers

    /**
     * Progressive enhancement: a real form post is redirected to the target
     * page, an XHR receives a small JSON body so the SPA can show inline errors.
     */
    private AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            if (isAjax(request)) {
                writeJson(response, HttpStatus.OK, "{\"success\":true,\"message\":\"OK\",\"data\":null}");
            } else {
                response.sendRedirect(request.getContextPath() + defaultTarget(request));
            }
        };
    }

    private AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            if (isAjax(request)) {
                writeJson(response, HttpStatus.UNAUTHORIZED,
                        "{\"success\":false,\"message\":\"Invalid username or password\",\"data\":null}");
            } else {
                response.sendRedirect(request.getContextPath() + "/login.html?error");
            }
        };
    }

    /** Only same-origin, non-protocol-relative paths are honoured. */
    private static String defaultTarget(HttpServletRequest request) {
        String next = request.getParameter("next");
        if (next != null && next.startsWith("/") && !next.startsWith("//") && !next.contains(":")) {
            return next;
        }
        return "/dashboard.html";
    }

    private static boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equalsIgnoreCase(request.getHeader(AJAX_HEADER));
    }

    private static void writeJson(HttpServletResponse response, HttpStatus status, String body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    /**
     * Rejects unsafe requests that do not echo the CSRF token back through the
     * {@code X-XSRF-TOKEN} header or a {@code _csrf} form field.
     *
     * <p>This closes the well-known hole of a bare cookie based repository: the
     * browser attaches {@code XSRF-TOKEN} to every same-site <em>and</em>
     * cross-site request, so on its own the cookie proves nothing. Requiring the
     * value to arrive in a header the SPA sets through {@code fetch} (or in a
     * form field) restores the double-submit guarantee, because a third-party
     * page can neither read the cookie nor add a custom header without a CORS
     * pre-flight it will not be granted.
     */
    static final class CsrfTokenDeliveryFilter extends OncePerRequestFilter {

        private final RestAwareAccessDeniedHandler accessDeniedHandler;

        private CsrfTokenDeliveryFilter(RestAwareAccessDeniedHandler accessDeniedHandler) {
            this.accessDeniedHandler = accessDeniedHandler;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return SAFE_METHODS.contains(request.getMethod());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (!isDelivered(request)) {
                accessDeniedHandler.handle(request, response, new AccessDeniedException(
                        "CSRF token must be sent in the " + CSRF_HEADER + " header or the "
                                + CSRF_PARAMETER + " parameter"));
                return;
            }
            filterChain.doFilter(request, response);
        }

        private static boolean isDelivered(HttpServletRequest request) {
            return isPresent(request.getHeader(CSRF_HEADER))
                    || isPresent(request.getParameter(CSRF_PARAMETER));
        }

        private static boolean isPresent(String value) {
            return value != null && !value.isBlank();
        }
    }

    /**
     * Spring Security resolves the CSRF token lazily; this filter touches it on
     * every request so the {@code XSRF-TOKEN} cookie is always present. Written
     * defensively so it works whether the request attribute holds a
     * {@link CsrfToken} or a {@link java.util.function.Supplier} of one.
     */
    static final class CsrfCookieEagerLoadFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            resolve(request.getAttribute(CsrfToken.class.getName())).ifPresent(CsrfToken::getToken);
            filterChain.doFilter(request, response);
        }

        private static Optional<CsrfToken> resolve(Object attribute) {
            if (attribute instanceof CsrfToken token) {
                return Optional.of(token);
            }
            if (attribute instanceof java.util.function.Supplier<?> supplier
                    && supplier.get() instanceof CsrfToken token) {
                return Optional.of(token);
            }
            return Optional.empty();
        }
    }
}
