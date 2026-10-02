package com.smit.taskportal.service;

import com.smit.taskportal.api.dto.ChangePasswordRequest;
import com.smit.taskportal.api.dto.CreateUserRequest;
import com.smit.taskportal.api.dto.UserDto;
import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.User;
import com.smit.taskportal.exception.BadRequestException;
import com.smit.taskportal.exception.DuplicateResourceException;
import com.smit.taskportal.exception.ResourceNotFoundException;
import com.smit.taskportal.exception.UnauthorizedException;
import com.smit.taskportal.repository.UserRepository;
import com.smit.taskportal.security.AppUserPrincipal;
import com.smit.taskportal.security.CurrentUserHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserHolder currentUser;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       CurrentUserHolder currentUser) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    // -------------------------------------------------------------- retrieval

    /** @return the currently authenticated user, refreshed from the database. */
    public User getCurrentUser() {
        return userRepository.findById(currentUser.id())
                .orElseThrow(() -> new UnauthorizedException("Your account no longer exists"));
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    public List<User> findAll() {
        return userRepository.findAllByOrderByUsernameAsc();
    }

    public List<UserDto> findAllDtos() {
        return findAll().stream().map(UserDto::from).toList();
    }

    // ----------------------------------------------------------------- writes

    /** Creates a new account. Authorising the requested role is the caller's job. */
    @Transactional
    public User register(CreateUserRequest request) {
        String username = request.username().strip();
        String email = request.email().strip().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("Username '%s' is already taken".formatted(username));
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email '%s' is already registered".formatted(email));
        }

        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build());
    }

    /** Convenience overload used by the bootstrap seeder. */
    public User register(String username, String rawPassword, String email, Role role) {
        return register(new CreateUserRequest(username, rawPassword, email, role));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getCurrentUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from the current one");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = getUserById(userId);
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("New password must be different from the current one");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /** The caller updates their own contact details. */
    @Transactional
    public User updateOwnProfile(String email) {
        User user = getCurrentUser();
        String newEmail = email.strip().toLowerCase();
        if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new DuplicateResourceException("Email '%s' is already registered".formatted(newEmail));
        }
        user.setEmail(newEmail);
        return userRepository.save(user);
    }

    /** Admin: rewrites any combination of username and email on any account. */
    @Transactional
    public User updateUserInfo(Long userId, String username, String email) {
        User user = getUserById(userId);

        String newUsername = username != null ? username.strip() : user.getUsername();
        String newEmail = email != null ? email.strip().toLowerCase() : user.getEmail();

        if (!newUsername.equalsIgnoreCase(user.getUsername())
                && userRepository.existsByUsernameIgnoreCase(newUsername)) {
            throw new DuplicateResourceException("Username '%s' is already taken".formatted(newUsername));
        }
        if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new DuplicateResourceException("Email '%s' is already registered".formatted(newEmail));
        }

        user.setUsername(newUsername);
        user.setEmail(newEmail);
        return userRepository.save(user);
    }

    @Transactional
    public User updateRole(Long userId, Role role) {
        User user = getUserById(userId);

        if (user.getRole() == role) {
            throw new BadRequestException("User already has role %s".formatted(role));
        }

        AppUserPrincipal actor = currentUser.require();
        if (actor.id().equals(userId) && role != Role.ADMIN) {
            throw new BadRequestException("You cannot remove your own ADMIN role");
        }
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN && countAdmins() <= 1) {
            throw new BadRequestException("At least one ADMIN account must remain");
        }

        user.setRole(role);
        return userRepository.save(user);
    }

    public long countAdmins() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .count();
    }
}
