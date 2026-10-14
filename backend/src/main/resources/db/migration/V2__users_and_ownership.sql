CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (issuer, subject)
);
ALTER TABLE reconciliation_runs ADD COLUMN owner_id UUID REFERENCES app_users(id);
CREATE INDEX runs_owner_history ON reconciliation_runs(owner_id, created_at DESC, id DESC);
