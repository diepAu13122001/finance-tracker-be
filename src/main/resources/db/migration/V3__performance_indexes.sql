-- ============================================================
-- V2: Performance indexes
-- ============================================================

-- Index cho filter theo type (INCOME/EXPENSE)
-- Query: WHERE user_id = ? AND type = ?
CREATE INDEX IF NOT EXISTS idx_transactions_user_type
    ON transactions(user_id, type);

-- Index cho chart queries theo năm/tháng
-- Query: WHERE user_id = ? AND EXTRACT(YEAR FROM transaction_date) = ?
CREATE INDEX IF NOT EXISTS idx_transactions_user_date_trunc
    ON transactions(user_id, date_trunc('month', transaction_date));

-- Index cho payment_history lookup
CREATE INDEX IF NOT EXISTS idx_payment_history_status
    ON payment_history(user_id, status);

-- Index cho subscription expiry check (scheduler hàng ngày)
CREATE INDEX IF NOT EXISTS idx_subscriptions_expires_status
    ON user_subscriptions(expires_at, status)
    WHERE expires_at IS NOT NULL AND status = 'ACTIVE';