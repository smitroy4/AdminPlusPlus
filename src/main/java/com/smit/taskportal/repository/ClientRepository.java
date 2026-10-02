package com.smit.taskportal.repository;

import com.smit.taskportal.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Client> findAllByOrderByNameAsc();
}
