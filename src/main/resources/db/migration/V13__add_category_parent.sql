-- ============================================================
-- V13: Thêm parent_category_id cho categories (self-reference)
-- ============================================================
-- ON DELETE SET NULL: khi xóa parent, children sẽ thành root categories
ALTER TABLE categories
ADD COLUMN IF NOT EXISTS parent_category_id UUID REFERENCES categories (id) ON DELETE SET NULL;

-- Index cho query "lấy tất cả con của parent X"
CREATE INDEX IF NOT EXISTS idx_categories_parent ON categories (parent_category_id)
WHERE
    parent_category_id IS NOT NULL;

-- Check constraint: parent và child phải cùng type (INCOME/EXPENSE)
-- Note: không enforce ở DB vì cần subquery — validate ở service layer