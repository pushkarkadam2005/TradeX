CREATE TABLE stocks (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol          VARCHAR(10)    NOT NULL UNIQUE,
    company_name    VARCHAR(255)   NOT NULL,
    current_price   NUMERIC(19,4)  NOT NULL CHECK (current_price > 0),
    previous_close  NUMERIC(19,4)  NOT NULL CHECK (previous_close > 0),
    sector          VARCHAR(100),
    market_status   VARCHAR(20)    NOT NULL DEFAULT 'OPEN'
                                   CHECK (market_status IN ('OPEN', 'CLOSED', 'HALTED')),
    tradable        BOOLEAN        NOT NULL DEFAULT TRUE,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stocks_symbol   ON stocks(symbol);
CREATE INDEX idx_stocks_tradable ON stocks(tradable) WHERE tradable = TRUE;

-- Seed Data (Deterministic initial prices)
INSERT INTO stocks (symbol, company_name, current_price, previous_close, sector, market_status, tradable) VALUES
('AAPL',  'Apple Inc.',                  185.5000, 184.2500, 'Technology',        'OPEN', TRUE),
('MSFT',  'Microsoft Corporation',      420.7500, 418.9000, 'Technology',        'OPEN', TRUE),
('GOOGL', 'Alphabet Inc.',               175.2500, 174.8000, 'Communication',     'OPEN', TRUE),
('AMZN',  'Amazon.com Inc.',            182.0000, 181.1000, 'Consumer Cyclical', 'OPEN', TRUE),
('TSLA',  'Tesla, Inc.',                178.3000, 176.5000, 'Consumer Cyclical', 'OPEN', TRUE),
('NVDA',  'NVIDIA Corporation',         125.4000, 122.8000, 'Technology',        'OPEN', TRUE);
