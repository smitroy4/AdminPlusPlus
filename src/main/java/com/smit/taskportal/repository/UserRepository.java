package com.smit.taskportal.repository;

import com.smit.taskportal.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from User u where lower(u.username) = lower(:username) or lower(u.email) = lower(:email)")
    Optional<User> findByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

    List<User> findAllByOrderByUsernameAsc();

    List<User> findAllByIdIn(Collection<Long> ids);

    boolean existsByUsernameIgnoreCase(String username);
}
