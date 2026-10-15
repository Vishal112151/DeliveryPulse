package com.deliverypulse.risk;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskRepository extends JpaRepository<Risk, UUID> {

	List<Risk> findAllByProjectId(UUID projectId);
}