ALTER TABLE risks ADD COLUMN certainty VARCHAR(20);
UPDATE risks SET certainty = 'CONFIRMED' WHERE certainty IS NULL;
ALTER TABLE risks ALTER COLUMN certainty SET NOT NULL;
ALTER TABLE risks ADD CONSTRAINT ck_risks_certainty
    CHECK (certainty IN ('CONFIRMED', 'POTENTIAL', 'DATA_QUALITY'));

ALTER TABLE risks ADD COLUMN fingerprint VARCHAR(500);
UPDATE risks SET fingerprint = 'legacy-' || CAST(id AS VARCHAR) WHERE fingerprint IS NULL;
ALTER TABLE risks ALTER COLUMN fingerprint SET NOT NULL;
CREATE UNIQUE INDEX uk_risks_fingerprint ON risks (fingerprint);

CREATE TABLE risk_supporting_tasks (
    risk_id UUID NOT NULL,
    task_id UUID NOT NULL,
    CONSTRAINT pk_risk_supporting_tasks PRIMARY KEY (risk_id, task_id),
    CONSTRAINT fk_risk_supporting_tasks_risk FOREIGN KEY (risk_id) REFERENCES risks (id),
    CONSTRAINT fk_risk_supporting_tasks_task FOREIGN KEY (task_id) REFERENCES tasks (id)
);
INSERT INTO risk_supporting_tasks (risk_id, task_id)
    SELECT id, task_id FROM risks WHERE task_id IS NOT NULL;