-- ============================================================
-- V10: Dọn sạch constraints tàn dư từ goals table
-- ============================================================

-- Xóa check constraint cũ từ goals (target_amount)
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS goals_target_amount_check;
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS wallets_target_amount_check;

-- Bỏ NOT NULL và set default 0 cho target_amount
-- (cột này không dùng nữa nhưng vẫn giữ để tránh lỗi legacy data)
ALTER TABLE wallets
    ALTER COLUMN target_amount SET DEFAULT 0,
ALTER COLUMN target_amount DROP NOT NULL;