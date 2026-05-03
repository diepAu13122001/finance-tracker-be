-- Chạy với superuser trước
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ft_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO ft_user;

-- Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- BẢNG 1: users
CREATE TABLE IF NOT EXISTS users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) UNIQUE NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    first_name       VARCHAR(100),
    last_name        VARCHAR(100),
    default_currency VARCHAR(3)  DEFAULT 'VND',
    language         VARCHAR(10) DEFAULT 'vi',
    created_at       TIMESTAMP   DEFAULT NOW(),
    updated_at       TIMESTAMP   DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- BẢNG 2: subscription_plans
CREATE TABLE IF NOT EXISTS subscription_plans (
    id            VARCHAR(20) PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    price_vnd     BIGINT       NOT NULL DEFAULT 0,
    billing_cycle VARCHAR(20),
    features      JSONB
);

INSERT INTO subscription_plans (id, name, price_vnd, billing_cycle, features) VALUES
    ('FREE',    'Miễn phí', 0,      NULL,     '{"maxTransactions": 50, "maxAiMessages": 0}'),
    ('PLUS',    'Plus',     0,      'YEARLY', '{"maxTransactions": -1, "maxAiMessages": 20}'),
    ('PREMIUM', 'Premium',  499000, 'YEARLY', '{"maxTransactions": -1, "maxAiMessages": -1, "household": true}')
ON CONFLICT (id) DO NOTHING;

-- BẢNG 3: user_subscriptions
CREATE TABLE IF NOT EXISTS user_subscriptions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id     VARCHAR(20) NOT NULL REFERENCES subscription_plans(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMP,
    payment_ref VARCHAR(255),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user
    ON user_subscriptions(user_id);

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_expires
    ON user_subscriptions(expires_at)
    WHERE expires_at IS NOT NULL;

-- BẢNG 4: payment_history
CREATE TABLE IF NOT EXISTS payment_history (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id               VARCHAR(20) NOT NULL REFERENCES subscription_plans(id),
    amount_vnd            BIGINT      NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payos_order_id        VARCHAR(255),
    payos_payment_link_id VARCHAR(255),
    created_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    paid_at               TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_history_user ON payment_history(user_id);

-- BẢNG 5: transactions
CREATE TABLE IF NOT EXISTS transactions (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type             VARCHAR(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount           BIGINT      NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)  NOT NULL DEFAULT 'VND',
    note             TEXT,
    transaction_date DATE        NOT NULL DEFAULT CURRENT_DATE,
    source           VARCHAR(20) NOT NULL DEFAULT 'manual',
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_user_date
    ON transactions(user_id, transaction_date DESC);

-- TRIGGER
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_users_updated_at ON users;
CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trigger_transactions_updated_at ON transactions;
CREATE TRIGGER trigger_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();