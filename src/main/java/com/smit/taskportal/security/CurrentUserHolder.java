package com.smit.taskportal.security;

import com.smit.taskportal.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Convenience access to the {@link AppUserPrincipal} of the current request. */
@Component
public class CurrentUserHolder {

    /** @return the principal, or empty when the caller is anonymous. */
    public Optional<AppUserPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    /** @return the principal, or throws {@link UnauthorizedException}. */
    public AppUserPrincipal require() {
        return find().orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    public Long id() {
        return require().id();
    }

    public boolean isManagerOrAbove() {
        return find().map(AppUserPrincipal::isManagerOrAbove).orElse(false);
    }
}
