package com.smit.taskportal.controller;

import com.smit.taskportal.api.dto.ApiResponse;
import com.smit.taskportal.api.dto.ChangePasswordRequest;
import com.smit.taskportal.api.dto.CsrfDto;
import com.smit.taskportal.api.dto.UpdateProfileRequest;
import com.smit.taskportal.api.dto.UserDto;
import com.smit.taskportal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session / identity endpoints. Login and logout themselves are handled by
 * Spring Security (see {@code SecurityConfig}); this controller exposes the
 * information the SPA needs to render a logged-in shell.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** Current user, or 401 when the session is missing/expired. */
    @GetMapping("/me")
    public ApiResponse<UserDto> me() {
        return ApiResponse.ok(UserDto.from(userService.getCurrentUser()));
    }

    /**
     * Public so the login page can obtain a CSRF token before authenticating.
     * The filter chain already writes the {@code XSRF-TOKEN} cookie; this
     * endpoint simply makes sure it exists and reports the names the client
     * needs.
     */
    @GetMapping("/csrf")
    public ApiResponse<CsrfDto> csrf(
            @CookieValue(name = CSRF_COOKIE, required = false) String cookieToken) {
        return ApiResponse.ok(new CsrfDto(cookieToken, "X-XSRF-TOKEN", "_csrf"));
    }

    @PostMapping("/auth/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", null));
    }

    /** The caller edits their own contact details (username/role stay admin-only). */
    @PutMapping("/me/profile")
    public ApiResponse<UserDto> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok("Profile updated", UserDto.from(userService.updateOwnProfile(request.email())));
    }
}
