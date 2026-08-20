-- V4__create_accounts_portfolios_ledger.sql
-- Phase 5: Account, Portfolio Positions, Double-Entry Ledger, and Trade Settlement Tables

CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id),
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    available_balance NUMERIC(19,4) NOT NULL CONSTRAINT chk_accounts_avail_bal_non_negative CHECK (available_balance >= 0),
    locked_balance NUMERIC(19,4) NOT NULL CONSTRAINT chk_accounts_locked_bal_non_negative CHECK (locked_balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE portfolio_positions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    stock_id UUID NOT NULL REFERENCES stocks(id),
    symbol VARCHAR(20) NOT NULL,
    quantity BIGINT NOT NULL CONSTRAINT chk_portfolio_qty_non_negative CHECK (quantity >= 0),
    locked_quantity BIGINT NOT NULL DEFAULT 0 CONSTRAINT chk_portfolio_locked_qty_non_negative CHECK (locked_quantity >= 0),
    average_buy_price NUMERIC(19,4) NOT NULL CONSTRAINT chk_portfolio_avg_price_non_negative CHECK (average_buy_price >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_stock UNIQUE (user_id, stock_id)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    balance_before NUMERIC(19,4) NOT NULL,
    balance_after NUMERIC(19,4) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE trade_settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id VARCHAR(36) UNIQUE NOT NULL,
    buyer_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    stock_id UUID NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    price NUMERIC(19,4) NOT NULL,
    quantity BIGINT NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_portfolio_user_id ON portfolio_positions(user_id);
CREATE INDEX idx_portfolio_user_symbol ON portfolio_positions(user_id, symbol);
CREATE INDEX idx_ledger_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX idx_settlements_execution_id ON trade_settlements(execution_id);
