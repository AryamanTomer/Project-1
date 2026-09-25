-- You don't have to run this by hand. The app creates these tables on startup.
-- Handy if you want to set up the DB yourself though.

-- One row per account. CHECK keeps the balance from going negative.
CREATE TABLE IF NOT EXISTS accounts (
    account_id VARCHAR(16) PRIMARY KEY,
    pin_hash VARCHAR(60) NOT NULL,
    balance NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- History. related_account_id is only filled in for transfers.
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(16) NOT NULL REFERENCES accounts(account_id),
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    related_account_id VARCHAR(16),
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- history is "give me the latest for this account"
CREATE INDEX IF NOT EXISTS idx_transactions_account_created
    ON transactions (account_id, created_at DESC);
