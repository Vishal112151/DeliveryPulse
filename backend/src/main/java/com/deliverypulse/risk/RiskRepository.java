package com.deliverypulse.risk;

import java.util.Collection;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.RiskStatus;

public interface RiskRepository extends JpaRepository<Risk, UUID> {

	List<Risk> findAllByProjectId(UUID projectId);

	List<Risk> findAllByStatusNotAndSeverityIn(RiskStatus status, Collection<RiskSeverity> severities);
}