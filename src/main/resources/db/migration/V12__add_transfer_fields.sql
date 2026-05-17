-- ============================================================
-- V12: Add transfer pair fields to transactions
-- Cho phép link 2 transaction của 1 lần transfer với nhau
-- ============================================================

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS transfer_pair_id UUID,
    ADD COLUMN IF NOT EXISTS linked_wallet_id UUID;

-- Index cho việc tìm paired transaction nhanh
CREATE INDEX IF NOT EXISTS idx_transactions_transfer_pair
    ON transactions (transfer_pair_id)
    WHERE transfer_pair_id IS NOT NULL;

-- NOTE: source field values for transfer:
-- 'transfer_out' = money leaving source wallet (EXPENSE direction)
-- 'transfer_in'  = money arriving at target wallet (INCOME direction)
-- source VARCHAR(20) đã đủ chứa 'transfer_out' (12 chars)
