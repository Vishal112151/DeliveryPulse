ALTER TABLE tasks ADD COLUMN issue_type VARCHAR(20) DEFAULT 'TASK' NOT NULL;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_issue_type CHECK (issue_type IN ('TASK', 'DEFECT'));
ALTER TABLE tasks ADD COLUMN milestone VARCHAR(160);
ALTER TABLE tasks ADD COLUMN milestone_date DATE;

ALTER TABLE risks DROP CONSTRAINT ck_risks_category;
ALTER TABLE risks ADD CONSTRAINT ck_risks_category CHECK (category IN ('OVERDUE_TASK', 'BLOCKED_TASK',
    'CRITICAL_TASK_AT_RISK', 'DEPENDENCY_RISK', 'DELIVERY_RISK', 'DATA_QUALITY_WARNING',
    'DEFECT_RISK', 'RECURRING_BLOCKER'));

-- One row per status a task enters. from_status is null when the task first appears in an import.
CREATE TABLE task_status_history (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_task_status_history_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT ck_task_status_history_from CHECK (from_status IN ('NOT_STARTED', 'IN_PROGRESS', 'BLOCKED', 'DONE')),
    CONSTRAINT ck_task_status_history_to CHECK (to_status IN ('NOT_STARTED', 'IN_PROGRESS', 'BLOCKED', 'DONE'))
);
CREATE INDEX ix_task_status_history_task ON task_status_history (task_id);
CREATE INDEX ix_task_status_history_changed_at ON task_status_history (changed_at);

-- Existing tasks start their history with the status they were imported with.
-- Reusing the task id as the row id keeps this portable (one row per task, so it stays unique).
INSERT INTO task_status_history (id, task_id, from_status, to_status, changed_at)
    SELECT id, id, NULL, status, created_at FROM tasks;
