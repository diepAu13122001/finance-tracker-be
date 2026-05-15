-- ============================================================
-- V8: Add INSTALLMENT fields to goals table
-- V6 thêm credit_limit/billing_date/interest_rate (CREDIT_CARD)
-- V7 fix constraints
-- V8 thêm 3 fields còn thiếu cho INSTALLMENT mà entity đang dùng
-- ============================================================
ALTER TABLE goals
ADD COLUMN IF NOT EXISTS number_of_periods INT, -- tổng số kỳ trả góp
ADD COLUMN IF NOT EXISTS monthly_payment BIGINT, -- tiền trả mỗi kỳ (gốc + lãi)
ADD COLUMN IF NOT EXISTS initial_amount BIGINT; -- số tiền vay ban đầu