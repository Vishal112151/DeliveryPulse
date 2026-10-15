package com.deliverypulse.task;

import org.springframework.data.domain.Sort;

public enum TaskSortDirection {
    ASC,
    DESC;

    public Sort.Direction toSortDirection() {
        return this == DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}