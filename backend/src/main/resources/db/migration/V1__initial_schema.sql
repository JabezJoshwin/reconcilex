CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status = 'COMPLETED'),
    internal_rows INTEGER NOT NULL CHECK (internal_rows BETWEEN 0 AND 20000),
    processor_rows INTEGER NOT NULL CHECK (processor_rows BETWEEN 0 AND 20000),
    summary_json TEXT NOT NULL
);
CREATE TABLE reconciliation_results (
    run_id UUID NOT NULL REFERENCES reconciliation_runs(id) ON DELETE CASCADE,
    transaction_id VARCHAR(128) NOT NULL,
    classification VARCHAR(40) NOT NULL CHECK (classification IN ('MATCHED','AMOUNT_MISMATCH','CURRENCY_MISMATCH','AMOUNT_AND_CURRENCY_MISMATCH','MISSING_IN_PROCESSOR','UNEXPECTED_IN_PROCESSOR','DUPLICATE_INTERNAL','DUPLICATE_PROCESSOR','DUPLICATE_BOTH')),
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    internal_amount_minor BIGINT CHECK (internal_amount_minor > 0),
    internal_currency VARCHAR(3),
    processor_amount_minor BIGINT CHECK (processor_amount_minor > 0),
    processor_currency VARCHAR(3),
    internal_occurrences INTEGER NOT NULL CHECK (internal_occurrences >= 0),
    processor_occurrences INTEGER NOT NULL CHECK (processor_occurrences >= 0),
    PRIMARY KEY (run_id, transaction_id),
    UNIQUE (run_id, ordinal)
);
CREATE INDEX results_filter ON reconciliation_results(run_id, classification, ordinal);
CREATE TABLE reconciliation_source_rows (
    run_id UUID NOT NULL REFERENCES reconciliation_runs(id) ON DELETE CASCADE,
    source VARCHAR(9) NOT NULL CHECK (source IN ('INTERNAL','PROCESSOR')),
    row_number INTEGER NOT NULL CHECK (row_number BETWEEN 1 AND 20000),
    transaction_id VARCHAR(128) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    PRIMARY KEY (run_id, source, row_number)
);
