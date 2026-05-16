-- ============================================================
-- V9: Rename goals → wallets, chỉ giữ NORMAL và DEBT
-- ============================================================
-- 1. Rename table
ALTER TABLE goals
RENAME TO wallets;

-- 2. Chuyển SAVINGS/INVESTMENT → NORMAL (giữ data cũ)
UPDATE wallets
SET
    type = 'NORMAL'
WHERE
    type IN ('SAVINGS', 'INVESTMENT');

-- 3. Chuyển COMPLETED → ACTIVE (wallet không có trạng thái "hoàn thành")
UPDATE wallets
SET
    status = 'ACTIVE'
WHERE
    status = 'COMPLETED';

-- 4. Cập nhật constraint type
ALTER TABLE wallets
DROP CONSTRAINT IF EXISTS goals_type_check;

ALTER TABLE wallets ADD CONSTRAINT wallets_type_check CHECK (type IN ('NORMAL', 'DEBT'));

-- 5. Cập nhật constraint status
ALTER TABLE wallets
DROP CONSTRAINT IF EXISTS goals_status_check;

ALTER TABLE wallets ADD CONSTRAINT wallets_status_check CHECK (status IN ('ACTIVE', 'CANCELLED'));

-- 6. Xóa constraint target_amount (wallets không có target)
ALTER TABLE wallets
DROP CONSTRAINT IF EXISTS goals_target_amount_check;

UPDATE wallets
SET
    target_amount = 0;

-- 7. Đổi tên cột goal_id → wallet_id trong transactions
ALTER TABLE transactions
RENAME COLUMN goal_id TO wallet_id;

-- 8. Cập nhật FK constraint
ALTER TABLE transactions
DROP CONSTRAINT IF EXISTS transactions_goal_id_fkey;

ALTER TABLE transactions ADD CONSTRAINT transactions_wallet_id_fkey FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE SET NULL;

-- 9. Cập nhật indexes
DROP INDEX IF EXISTS idx_goals_user_status;

CREATE INDEX IF NOT EXISTS idx_wallets_user_status ON wallets (user_id, status);

DROP INDEX IF EXISTS idx_goals_user_type;

CREATE INDEX IF NOT EXISTS idx_wallets_user_type ON wallets (user_id, type);

DROP INDEX IF EXISTS idx_transactions_goal;

CREATE INDEX IF NOT EXISTS idx_transactions_wallet ON transactions (wallet_id)
WHERE
    wallet_id IS NOT NULL;

-- 10. Trigger
DROP TRIGGER IF EXISTS trigger_goals_updated_at ON wallets;

CREATE TRIGGER trigger_wallets_updated_at BEFORE
UPDATE ON wallets FOR EACH ROW EXECUTE FUNCTION update_updated_at ();