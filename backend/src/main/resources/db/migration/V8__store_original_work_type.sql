-- The source's own name for the item type (Jira issue type such as Story, Task or Bug, or the upload's Type value).
-- issue_type stays the TASK/DEFECT classification the risk rules use. Filled by the next import or sync.
ALTER TABLE tasks ADD COLUMN work_type VARCHAR(40);
