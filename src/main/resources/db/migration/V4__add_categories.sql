-- ============================================================
-- V4: Categories System (Plus feature)
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- BẢNG: categories
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS categories
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    icon       VARCHAR(20) NOT NULL DEFAULT 'tag',
    color      VARCHAR(7)  NOT NULL DEFAULT '#82b01e',
    type       VARCHAR(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    -- Mỗi user có thể có nhiều category cùng tên cho 2 type khác nhau
    -- Ví dụ: "Đầu tư" có thể là INCOME (lãi) hoặc EXPENSE (mua cổ phiếu)
    UNIQUE (user_id, name, type)
);

-- Index cho query phổ biến: lấy categories của user, lọc theo type
CREATE INDEX IF NOT EXISTS idx_categories_user_type
    ON categories (user_id, type);

-- ────────────────────────────────────────────────────────────
-- BẢNG: transactions — thêm cột category_id
-- ────────────────────────────────────────────────────────────
-- ADD COLUMN IF NOT EXISTS không có sẵn → dùng DO block để check trước
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.columns
                       WHERE table_name = 'transactions'
                         AND column_name = 'category_id') THEN
            ALTER TABLE transactions
                ADD COLUMN category_id UUID
                    REFERENCES categories (id) ON DELETE SET NULL;
        END IF;
    END
$$;

-- Partial index: chỉ index những row có category
CREATE INDEX IF NOT EXISTS idx_transactions_category
    ON transactions (category_id) WHERE category_id IS NOT NULL;
