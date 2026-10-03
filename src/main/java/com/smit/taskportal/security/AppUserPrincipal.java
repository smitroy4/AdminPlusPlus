package com.smit.taskportal.security;

import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Authenticated principal. Kept as a record so it is trivially immutable and
 * cheap to store in the HTTP session. {@code clientId} is only populated for
 * {@link Role#CLIENT} accounts and scopes every task read to that customer.
 */
public record AppUserPrincipal(Long id,
                               String username,
                               String password,
                               String email,
                               Role role,
                               Long clientId) implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPassword(),
                user.getEmail(), user.getRole(),
                user.getClient() == null ? null : user.getClient().getId());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Records expose {@code username()}; {@code UserDetails} wants a getter. */
    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public boolean isManagerOrAbove() {
        return role != null && role.isManagerOrAbove();
    }

    public boolean isClient() {
        return role != null && role.isClient();
    }

    public boolean isCoordinatorOrAbove() {
        return role != null && role.isCoordinatorOrAbove();
    }

    public boolean isAssociate() {
        return role != null && role == com.smit.taskportal.domain.Role.ASSOCIATE;
    }

    public boolean isAdmin() {
        return role != null && role == com.smit.taskportal.domain.Role.ADMIN;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
