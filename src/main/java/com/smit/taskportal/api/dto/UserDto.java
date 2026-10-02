package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.User;

import java.time.Instant;

/** Full user representation — note the absence of any password material. */
public record UserDto(Long id,
                      String username,
                      String email,
                      Role role,
                      Instant createdAt,
                      Instant updatedAt) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
