package com.deliverypulse.project;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	boolean existsByNameIgnoreCase(String name);

    Optional<Project> findByNameIgnoreCase(String name);
}