ALTER TABLE projects ADD COLUMN normalized_name VARCHAR(160);
UPDATE projects SET normalized_name = LOWER(name);
ALTER TABLE projects ALTER COLUMN normalized_name SET NOT NULL;
CREATE UNIQUE INDEX uk_projects_normalized_name ON projects (normalized_name);