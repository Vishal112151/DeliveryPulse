package com.deliverypulse.dependency;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DependencyRepository extends JpaRepository<Dependency, UUID> {

	List<Dependency> findAllByDependentTaskProjectId(UUID projectId);
}