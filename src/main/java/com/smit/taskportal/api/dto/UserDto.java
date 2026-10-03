package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.User;

import java.time.Instant;

/**
 * Full user representation — note the absence of any password material.
 *
 * <p>{@code clientId} is populated only for {@link Role#CLIENT} accounts. It lets
 * the browser build the "My Company" link without a second round trip, while
 * staying a plain foreign key: the API still decides what that customer may see.
 */
public record UserDto(Long id,
                      String username,
                      String email,
                      Role role,
                      Long clientId,
                      Instant createdAt,
                      Instant updatedAt) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.getClient() == null ? null : user.getClient().getId(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}