-- ============================================================
-- V6: Wallet system — extend goals table
-- ============================================================

-- 1. Thêm type NORMAL vào CHECK constraint
ALTER TABLE goals DROP CONSTRAINT IF EXISTS goals_type_check;
ALTER TABLE goals
    ADD CONSTRAINT goals_type_check
        CHECK (type IN ('SAVINGS', 'DEBT', 'INVESTMENT', 'NORMAL'));

-- 2. Thêm subtype cho DEBT (CREDIT_CARD / INSTALLMENT)
ALTER TABLE goals
    ADD COLUMN IF NOT EXISTS subtype VARCHAR (20)
    CHECK (subtype IN ('CREDIT_CARD', 'INSTALLMENT') OR subtype IS NULL);

-- 3. Fields cho DEBT CREDIT_CARD
ALTER TABLE goals
    ADD COLUMN IF NOT EXISTS credit_limit BIGINT, -- hạn mức thẻ tín dụng
    ADD COLUMN IF NOT EXISTS billing_date INT, -- ngày đáo hạn (1-28)
    ADD COLUMN IF NOT EXISTS interest_rate NUMERIC (5,2);
-- lãi suất %/tháng

-- 4. NORMAL wallet: balance = tính ngược từ transactions
-- không cần thêm column, dùng current_amount để cache

-- 5. Free user limit: đổi check thành trigger (logic ở service)
-- goal limit per user: FREE = 5, PLUS = unlimited

-- 6. Seed 1 ví "Tiền mặt" mặc định cho user mới
-- (sẽ tạo programmatic trong AuthService khi register)

COMMENT
ON COLUMN goals.target_amount IS
    'SAVINGS/INVESTMENT: mục tiêu tích lũy | DEBT: hạn mức nợ | NORMAL: không dùng (set = 0)';
COMMENT
ON COLUMN goals.current_amount IS
    'SAVINGS/INVESTMENT: đã tích lũy | DEBT: số nợ hiện tại | NORMAL: số dư hiện tại';

-- DEBT CREDIT_CARD dùng credit_limit thay vì target_amount
-- NORMAL wallet không cần target_amount
ALTER TABLE goals DROP CONSTRAINT IF EXISTS goals_target_amount_check;
ALTER TABLE goals
    ADD CONSTRAINT goals_target_amount_check
        CHECK (target_amount >= 0); -- 👈 ĐỔI > 0 thành >= 0