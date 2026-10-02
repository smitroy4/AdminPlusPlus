package com.smit.taskportal.controller;

import com.smit.taskportal.api.dto.AdminResetPasswordRequest;
import com.smit.taskportal.api.dto.AdminUpdateUserRequest;
import com.smit.taskportal.api.dto.ApiResponse;
import com.smit.taskportal.api.dto.CreateUserRequest;
import com.smit.taskportal.api.dto.UpdateRoleRequest;
import com.smit.taskportal.api.dto.UserDto;
import com.smit.taskportal.exception.BadRequestException;
import com.smit.taskportal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-only account management. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserDto>> listUsers() {
        return ApiResponse.ok(userService.findAllDtos());
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = UserDto.from(userService.register(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("User created", created));
    }

    @PatchMapping("/users/{id}/role")
    public ApiResponse<UserDto> updateRole(@PathVariable Long id,
                                           @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok("Role updated", UserDto.from(userService.updateRole(id, request.role())));
    }

    /** Admin rewrites a user's username and/or email. */
    @PatchMapping("/users/{id}")
    public ApiResponse<UserDto> updateUser(@PathVariable Long id,
                                           @Valid @RequestBody AdminUpdateUserRequest request) {
        if (!request.hasChanges()) {
            throw new BadRequestException("Nothing to update");
        }
        return ApiResponse.ok("User updated",
                UserDto.from(userService.updateUserInfo(id, request.username(), request.email())));
    }

    /** Admin sets a new password for any account without knowing the old one. */
    @PatchMapping("/users/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody AdminResetPasswordRequest request) {
        userService.resetPassword(id, request.password());
        return ApiResponse.ok("Password reset", null);
    }
}
