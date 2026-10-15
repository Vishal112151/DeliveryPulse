package com.deliverypulse.upload;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRunRepository extends JpaRepository<ImportRun, UUID> {
}