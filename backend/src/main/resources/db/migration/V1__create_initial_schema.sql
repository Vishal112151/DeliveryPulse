CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL UNIQUE,
    source_sheet_name VARCHAR(160),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    task_key VARCHAR(100) NOT NULL,
    task_name VARCHAR(300) NOT NULL,
    team_or_module VARCHAR(160),
    assignee VARCHAR(160),
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_date DATE,
    due_date DATE,
    blocker_text TEXT,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uk_tasks_project_task_key UNIQUE (project_id, task_key),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_tasks_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'BLOCKED', 'DONE'))
);
CREATE INDEX ix_tasks_project_due_date ON tasks (project_id, due_date);

CREATE TABLE dependencies (
    id UUID PRIMARY KEY,
    dependent_task_id UUID NOT NULL,
    prerequisite_task_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_text VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_dependencies_dependent_task FOREIGN KEY (dependent_task_id) REFERENCES tasks (id),
    CONSTRAINT fk_dependencies_prerequisite_task FOREIGN KEY (prerequisite_task_id) REFERENCES tasks (id),
    CONSTRAINT uk_dependencies_task_pair UNIQUE (dependent_task_id, prerequisite_task_id),
    CONSTRAINT ck_dependencies_not_self CHECK (dependent_task_id <> prerequisite_task_id),
    CONSTRAINT ck_dependencies_status CHECK (status IN ('OPEN', 'RESOLVED'))
);
CREATE INDEX ix_dependencies_prerequisite_task ON dependencies (prerequisite_task_id);

CREATE TABLE risks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    task_id UUID,
    category VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(240) NOT NULL,
    explanation TEXT NOT NULL,
    evidence TEXT,
    recommended_action TEXT,
    status VARCHAR(20) NOT NULL,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_risks_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_risks_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT ck_risks_category CHECK (category IN ('OVERDUE_TASK', 'BLOCKED_TASK', 'CRITICAL_TASK_AT_RISK', 'DEPENDENCY_RISK', 'DELIVERY_RISK', 'DATA_QUALITY_WARNING')),
    CONSTRAINT ck_risks_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_risks_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'))
);
CREATE INDEX ix_risks_project_status ON risks (project_id, status);
CREATE INDEX ix_risks_detected_at ON risks (detected_at);

CREATE TABLE import_runs (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    project_count INTEGER NOT NULL DEFAULT 0,
    task_count INTEGER NOT NULL DEFAULT 0,
    warning_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_import_runs_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'))
);