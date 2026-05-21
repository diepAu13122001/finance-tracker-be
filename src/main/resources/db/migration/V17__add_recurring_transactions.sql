-- Giao dịch định kỳ: lưu template, tự cập nhật next_execution_date khi execute
CREATE TABLE recurring_transactions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                VARCHAR(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount              BIGINT      NOT NULL CHECK (amount > 0),
    note                VARCHAR(500),
    category_id         UUID        REFERENCES categories(id) ON DELETE SET NULL,
    wallet_id           UUID        REFERENCES wallets(id)    ON DELETE SET NULL,

    -- Tần suất lặp: DAILY | WEEKLY | MONTHLY | YEARLY
    frequency           VARCHAR(10) NOT NULL CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY')),

    -- Hỗ trợ schedule linh hoạt
    day_of_month        INTEGER     CHECK (day_of_month BETWEEN 1 AND 31), -- dùng cho MONTHLY/YEARLY
    day_of_week         INTEGER     CHECK (day_of_week  BETWEEN 1 AND 7),  -- dùng cho WEEKLY (1=T2, 7=CN)

    next_execution_date DATE        NOT NULL,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_user   ON recurring_transactions(user_id);
CREATE INDEX idx_recurring_active ON recurring_transactions(next_execution_date) WHERE is_active = TRUE;
