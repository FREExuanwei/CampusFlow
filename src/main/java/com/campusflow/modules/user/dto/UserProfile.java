package com.campusflow.modules.user.dto;

import com.campusflow.modules.user.entity.Role;
import com.campusflow.modules.user.entity.UserAccount;
import com.campusflow.modules.user.enums.RoleCode;
import com.campusflow.modules.user.enums.UserStatus;
import java.time.Instant;
import java.util.List;

public record UserProfile(long id, String username, String displayName, List<RoleCode> roles,
                          UserStatus status, Instant createdAt) {

    public static UserProfile from(UserAccount user) {
        var roles = user.getRoles().stream().map(Role::getCode).sorted().toList();
        return new UserProfile(user.getId(), user.getUsername(), user.getDisplayName(),
                roles, user.getStatus(), user.getCreatedAt());
    }
}
