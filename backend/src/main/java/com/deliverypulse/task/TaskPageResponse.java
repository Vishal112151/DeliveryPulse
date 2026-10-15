package com.deliverypulse.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TaskPageResponse(
        List<TaskResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        LocalDate analysisDate,
        Instant analysisTimestamp,
        TaskSortField sortBy,
        TaskSortDirection sortDirection) {

    public TaskPageResponse {
        content = List.copyOf(content);
    }
}