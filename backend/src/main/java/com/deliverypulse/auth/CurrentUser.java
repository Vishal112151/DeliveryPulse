package com.deliverypulse.auth;

import java.util.UUID;

/** The signed-in user, stored as the Spring Security principal for one request. */
public record CurrentUser(UUID id, String username, String displayName, Role role) {

    static CurrentUser of(AppUser user) {
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
