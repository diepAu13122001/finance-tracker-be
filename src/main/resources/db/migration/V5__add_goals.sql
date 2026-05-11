-- ============================================================
-- V5: Goals System (Plus feature)
-- ============================================================

CREATE TABLE IF NOT EXISTS goals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    icon            VARCHAR(20)  NOT NULL DEFAULT 'target',
    color           VARCHAR(7)   NOT NULL DEFAULT '#82b01e',
    type            VARCHAR(20)  NOT NULL CHECK (type IN ('SAVINGS', 'DEBT', 'INVESTMENT')),
    target_amount   BIGINT       NOT NULL CHECK (target_amount > 0),
    current_amount  BIGINT       NOT NULL DEFAULT 0,
    deadline        DATE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_goals_user_status ON goals(user_id, status);

-- Thêm goal_id vào transactions (nullable — transaction cũ không có goal)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'transactions' AND column_name = 'goal_id'
    ) THEN
        ALTER TABLE transactions
        ADD COLUMN goal_id UUID REFERENCES goals(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_transactions_goal
    ON transactions(goal_id) WHERE goal_id IS NOT NULL;

-- Trigger tự cập nhật updated_at cho goals
DROP TRIGGER IF EXISTS trigger_goals_updated_at ON goals;
CREATE TRIGGER trigger_goals_updated_at
    BEFORE UPDATE ON goals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();