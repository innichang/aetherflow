ALTER TABLE workflow_runs ADD COLUMN trigger_key VARCHAR(255);
CREATE UNIQUE INDEX idx_workflow_runs_trigger_key ON workflow_runs (trigger_key) WHERE trigger_key IS NOT NULL;
