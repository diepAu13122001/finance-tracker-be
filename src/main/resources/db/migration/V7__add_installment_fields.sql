-- ============================================================
-- V7: Fix constraints để support Wallet (GoalType.NORMAL)
-- ============================================================

-- ── 1. Fix type check — thêm 'NORMAL' vào danh sách hợp lệ ──────────────────
-- PostgreSQL: cần drop + re-add vì không có ALTER CONSTRAINT
ALTER TABLE goals
DROP
CONSTRAINT IF EXISTS goals_type_check;

ALTER TABLE goals
    ADD CONSTRAINT goals_type_check
        CHECK (type IN ('SAVINGS', 'DEBT', 'INVESTMENT', 'NORMAL'));

-- ── 2. Fix target_amount check — NORMAL wallet không có target (= 0) ─────────
-- Goals: target_amount > 0 (bắt buộc có mục tiêu)
-- Wallets (NORMAL): target_amount = 0 (số dư ban đầu, không có target)
ALTER TABLE goals
DROP
CONSTRAINT IF EXISTS goals_target_amount_check;

ALTER TABLE goals
    ADD CONSTRAINT goals_target_amount_check
        CHECK (
            (type IN ('SAVINGS', 'DEBT', 'INVESTMENT') AND target_amount > 0)
                OR (type = 'NORMAL' AND target_amount >= 0)
            );

-- ── 3. Thêm wallet_id vào transactions (nullable) ─────────────────────────────
-- Transactions có thể link vào NORMAL wallet để tính balance
-- Thực ra dùng lại cột goal_id (wallet cũng là goal với type=NORMAL)
-- Không cần cột mới — goal_id đã đủ dùng

-- ── 4. Index mới cho wallet queries ──────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_goals_user_type
    ON goals (user_id, type);

-- ── 5. Note: Wallets = Goals với type='NORMAL'
--   - current_amount = SUM(INCOME) - SUM(EXPENSE) của transactions link vào wallet
--   - target_amount = 0 (không có mục tiêu — chỉ track số dư)
--   - status = ACTIVE (không auto-complete như goals)