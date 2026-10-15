package com.deliverypulse.task;

public enum TaskSortField {
    TASK_ID("taskKey"),
    TASK_NAME("taskName"),
    STATUS("status"),
    PRIORITY("priority"),
    ASSIGNEE("assignee"),
    DUE_DATE("dueDate");

    private final String property;

    TaskSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}