package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.User;

/** Compact user projection embedded in task / message payloads. */
public record UserSummaryDto(Long id, String username, String email, Role role) {

    public static UserSummaryDto from(User user) {
        return user == null ? null
                : new UserSummaryDto(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
