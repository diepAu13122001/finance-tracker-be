-- ============================================================
-- V15: Track khi nào budget bắt đầu áp dụng + ngày bắt đầu tháng mới của user
-- ============================================================

-- 1. categories.budget_started_at
--    NULL = category chưa từng có budget
--    Date = ngày đầu kỳ đầu tiên áp dụng budget hiện tại
--    Dùng để check tháng trước CÓ budget hay không → rollover có hợp lệ không
ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS budget_started_at DATE;

-- Backfill: category đang có budget hiện tại → coi như budget áp dụng từ đầu tháng này
UPDATE categories
SET budget_started_at = DATE_TRUNC('month', CURRENT_DATE)::DATE
WHERE monthly_budget IS NOT NULL
  AND budget_started_at IS NULL;

-- 2. users.month_start_day
--    Ngày bắt đầu chu kỳ tháng — vd: lương trả ngày 5 thì chọn 5
--    Hạn từ 1-28 để tránh tháng không có ngày 29/30/31
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS month_start_day INT NOT NULL DEFAULT 1;

ALTER TABLE users
DROP
CONSTRAINT IF EXISTS users_month_start_day_check;
ALTER TABLE users
    ADD CONSTRAINT users_month_start_day_check
        CHECK (month_start_day BETWEEN 1 AND 28);