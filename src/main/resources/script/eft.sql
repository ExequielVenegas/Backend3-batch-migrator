CREATE TABLE IF NOT EXISTS eft_records (
    kind VARCHAR(20) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    source_line INT NOT NULL,
    period VARCHAR(7) NOT NULL,
    rule_version VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(120) NOT NULL,
    reference_id INT,
    operation_date DATE,
    operation_type VARCHAR(30),
    amount DECIMAL(19,2),
    interest DECIMAL(19,2),
    final_balance DECIMAL(19,2),
    raw_line TEXT NOT NULL,
    worker VARCHAR(100) NOT NULL,
    PRIMARY KEY (kind, source_hash, source_line, period, rule_version)
);
