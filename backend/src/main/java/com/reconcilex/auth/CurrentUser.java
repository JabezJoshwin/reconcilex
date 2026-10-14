package com.reconcilex.auth;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
public final class CurrentUser {
    private CurrentUser() {}
    public static UUID id() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() instanceof String && auth.getName().equals("anonymousUser"))
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Sign in required");
        return UUID.fromString(auth.getName());
    }
}
