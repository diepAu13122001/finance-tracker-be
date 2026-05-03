-- ============================================================
-- V3: Performance indexes
-- ============================================================

-- Index cho filter theo type (INCOME/EXPENSE)
-- Query: WHERE user_id = ? AND type = ?
CREATE INDEX IF NOT EXISTS idx_transactions_user_type
    ON transactions(user_id, type);

-- Index cho chart queries theo năm/tháng
-- Dùng range query thay vì date_trunc() vì date_trunc không phải IMMUTABLE
-- Query: WHERE user_id = ? AND transaction_date >= ? AND transaction_date < ?
CREATE INDEX IF NOT EXISTS idx_transactions_user_date_asc
    ON transactions(user_id, transaction_date);

-- Index cho payment_history lookup
-- Query: WHERE user_id = ? AND status = ?
CREATE INDEX IF NOT EXISTS idx_payment_history_status
    ON payment_history(user_id, status);

-- Index cho subscription expiry check (scheduler hàng ngày)
CREATE INDEX IF NOT EXISTS idx_subscriptions_expires_status
    ON user_subscriptions(expires_at, status)
    WHERE expires_at IS NOT NULL AND status = 'ACTIVE';