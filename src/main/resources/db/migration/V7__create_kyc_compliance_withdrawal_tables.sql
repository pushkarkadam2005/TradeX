CREATE TABLE kyc_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    level VARCHAR(30) NOT NULL DEFAULT 'BASIC',
    provider VARCHAR(50) NOT NULL DEFAULT 'MOCK_KYC',
    provider_reference VARCHAR(100),
    submitted_at TIMESTAMP WITH TIME ZONE,
    verified_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE compliance_audit_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    decision VARCHAR(30) NOT NULL,
    rule_code VARCHAR(50) NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE withdrawals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    idempotency_key VARCHAR(100) NOT NULL,
    destination_reference VARCHAR(255) NOT NULL,
    compliance_decision VARCHAR(30),
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_withdrawal_idemp UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_kyc_user ON kyc_verifications(user_id);
CREATE INDEX idx_kyc_status ON kyc_verifications(status);

CREATE INDEX idx_compliance_audit_user ON compliance_audit_records(user_id, created_at DESC);
CREATE INDEX idx_compliance_audit_ref ON compliance_audit_records(reference_type, reference_id);

CREATE INDEX idx_withdrawals_user ON withdrawals(user_id, created_at DESC, id DESC);
CREATE INDEX idx_withdrawals_status ON withdrawals(status);
CREATE INDEX idx_withdrawals_idemp ON withdrawals(user_id, idempotency_key);
