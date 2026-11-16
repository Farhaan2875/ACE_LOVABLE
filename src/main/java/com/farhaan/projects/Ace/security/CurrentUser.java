package com.farhaan.projects.Ace.security;

import com.farhaan.projects.Ace.error.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Gives controllers the id of the logged-in user (replaces the old hard-coded "Long userId = 1L"). */
public final class CurrentUser {
    private CurrentUser() {}

    public static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new UnauthorizedException("Please log in");
    }
}
