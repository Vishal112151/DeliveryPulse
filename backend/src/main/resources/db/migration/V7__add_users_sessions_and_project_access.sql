CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL,
    normalized_username VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_users_normalized_username UNIQUE (normalized_username),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'MANAGER', 'VIEWER'))
);

-- Which projects a Manager or Viewer may see. Admins see every project.
CREATE TABLE project_members (
    user_id UUID NOT NULL,
    project_id UUID NOT NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (user_id, project_id),
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id)
);
CREATE INDEX ix_project_members_project ON project_members (project_id);

-- Login sessions. Only a SHA-256 hash of each bearer token is stored.
CREATE TABLE user_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES app_users (id)
);
CREATE INDEX ix_user_sessions_user ON user_sessions (user_id);
