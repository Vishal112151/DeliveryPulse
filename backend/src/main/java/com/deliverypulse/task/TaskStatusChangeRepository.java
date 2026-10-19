package com.deliverypulse.task;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskStatusChangeRepository extends JpaRepository<TaskStatusChange, UUID> {

    @Query("select c from TaskStatusChange c where c.task.project.id = :projectId order by c.changedAt")
    List<TaskStatusChange> findAllByProjectId(@Param("projectId") UUID projectId);

    @Query("select c from TaskStatusChange c join fetch c.task t join fetch t.project "
            + "where c.changedAt >= :from and c.changedAt < :to")
    List<TaskStatusChange> findAllChangedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
