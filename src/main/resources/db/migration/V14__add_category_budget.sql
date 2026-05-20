-- ============================================================
-- V14: Ngân sách dự kiến per category (theo tháng) + rollover
-- NULL = không set budget
-- ============================================================
ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS monthly_budget BIGINT;

CREATE INDEX IF NOT EXISTS idx_categories_with_budget
    ON categories (user_id)
    WHERE monthly_budget IS NOT NULL;