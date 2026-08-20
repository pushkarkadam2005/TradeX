CREATE TABLE transaction_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    transaction_type VARCHAR(30) NOT NULL,
    transaction_status VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID,
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_tx_records_user ON transaction_records(user_id);
CREATE INDEX idx_tx_records_created ON transaction_records(created_at DESC, id DESC);
CREATE INDEX idx_tx_records_ref ON transaction_records(reference_type, reference_id);
