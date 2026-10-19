-- A saved copy of the weekly report, written by the weekly job (or a manual run), so past weeks can be reopened.
CREATE TABLE weekly_report_snapshots (
    id UUID PRIMARY KEY,
    week_start_date DATE NOT NULL,
    week_end_date DATE NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    payload TEXT NOT NULL,
    CONSTRAINT ck_weekly_report_snapshots_trigger CHECK (trigger_type IN ('SCHEDULED', 'MANUAL'))
);
CREATE INDEX ix_weekly_report_snapshots_generated_at ON weekly_report_snapshots (generated_at);
