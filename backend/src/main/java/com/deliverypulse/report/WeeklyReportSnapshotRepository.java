package com.deliverypulse.report;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReportSnapshotRepository extends JpaRepository<WeeklyReportSnapshot, UUID> {

    List<WeeklyReportSnapshot> findAllByOrderByGeneratedAtDesc();
}
