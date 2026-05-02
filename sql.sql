-- ============================================================
-- V1: Schema khởi tạo — bao gồm subscription từ đầu
-- ============================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
-- pgcrypto cung cấp gen_random_uuid() để tạo UUID tự động

-- ────────────────────────────────────────────────────────────
-- BẢNG 1: users
-- ────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    default_currency VARCHAR(3)  DEFAULT 'VND',
    language      VARCHAR(10)  DEFAULT 'vi',
    created_at    TIMESTAMP    DEFAULT NOW(),
    updated_at    TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ────────────────────────────────────────────────────────────
-- BẢNG 2: subscription_plans (seed data — không phải user tạo)
-- ────────────────────────────────────────────────────────────
CREATE TABLE subscription_plans (
    id            VARCHAR(20) PRIMARY KEY,  -- 'FREE', 'PLUS', 'PREMIUM'
    name          VARCHAR(100) NOT NULL,
    price_vnd     BIGINT       NOT NULL DEFAULT 0,
    billing_cycle VARCHAR(20),              -- 'YEARLY', NULL cho free
    features      JSONB                     -- lưu feature flags dạng JSON
);

-- Seed dữ liệu mặc định
INSERT INTO subscription_plans (id, name, price_vnd, billing_cycle, features) VALUES
    ('FREE',
     'Miễn phí',
     0,
     NULL,
     '{"maxTransactions": 50, "maxAiMessages": 0}'
    ),
    ('PLUS',
     'Plus',
     0,
     'YEARLY',
     '{"maxTransactions": -1, "maxAiMessages": 20}'
    ),
    ('PREMIUM',
     'Premium',
     499000,
     'YEARLY',
     '{"maxTransactions": -1, "maxAiMessages": -1, "household": true}'
    );

-- ────────────────────────────────────────────────────────────
-- BẢNG 3: user_subscriptions
-- ────────────────────────────────────────────────────────────
CREATE TABLE user_subscriptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id     VARCHAR(20) NOT NULL REFERENCES subscription_plans(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, CANCELLED
    started_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMP,           -- NULL = không bao giờ hết hạn (free)
    payment_ref VARCHAR(255),        -- mã tham chiếu PayOS
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_subscriptions_user ON user_subscriptions(user_id);
CREATE INDEX idx_user_subscriptions_expires ON user_subscriptions(expires_at)
    WHERE expires_at IS NOT NULL;  -- partial index — chỉ index hàng có expires_at

-- ────────────────────────────────────────────────────────────
-- BẢNG 4: payment_history
-- ────────────────────────────────────────────────────────────
CREATE TABLE payment_history (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id               VARCHAR(20) NOT NULL REFERENCES subscription_plans(id),
    amount_vnd            BIGINT      NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED
    payos_order_id        VARCHAR(255),
    payos_payment_link_id VARCHAR(255),
    created_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    paid_at               TIMESTAMP
);

CREATE INDEX idx_payment_history_user ON payment_history(user_id);

-- ────────────────────────────────────────────────────────────
-- BẢNG 5: transactions
-- ────────────────────────────────────────────────────────────
CREATE TABLE transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type             VARCHAR(10)    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount           BIGINT         NOT NULL CHECK (amount > 0),
    -- Dùng BIGINT thay DECIMAL cho VND vì VND không có số thập phân
    currency         VARCHAR(3)     NOT NULL DEFAULT 'VND',
    note             TEXT,
    transaction_date DATE           NOT NULL DEFAULT CURRENT_DATE,
    source           VARCHAR(20)    NOT NULL DEFAULT 'manual', -- manual, voice, receipt
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_user_date
    ON transactions(user_id, transaction_date DESC);
-- Index kết hợp: tối ưu cho query "lấy giao dịch của user X, sắp xếp mới nhất"

-- ────────────────────────────────────────────────────────────
-- TRIGGER: tự cập nhật updated_at
-- ────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trigger_transactions_updated_at
    BEFORE UPDATE ON transactions
FOR EACH ROW EXECUTE FUNCTION update_updated_at();


-- ────────────────────────────────────────────────────────────
-- KIEM TRA DB TABLE
-- ────────────────────────────────────────────────────────────
SELECT * FROM subscription_plans;
SELECT * FROM users;
SELECT * FROM user_subscriptions;
SELECT * FROM transactions;
