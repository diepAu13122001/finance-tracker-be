-- ============================================================
-- V17: Household Tracker (Premium feature)
-- 3 bảng: household_items, item_reviews, notifications
-- ============================================================
CREATE TABLE
    IF NOT EXISTS household_items (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
        user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        name VARCHAR(200) NOT NULL,
        brand VARCHAR(100),
        -- ENUM category lưu dạng string → dễ thêm mới sau này
        category VARCHAR(20) NOT NULL DEFAULT 'OTHER' CHECK (
            category IN (
                'SKINCARE',
                'HOUSECARE',
                'FOOD',
                'CLOTHES',
                'OTHER'
            )
        ),
        ai_category VARCHAR(50), -- gợi ý của AI (có thể khác category user chọn)
        price BIGINT,
        purchase_date DATE,
        expiry_date DATE,
        quantity NUMERIC(10, 2),
        unit VARCHAR(20), -- 'ml', 'g', 'gói', 'cái'
        status VARCHAR(20) NOT NULL DEFAULT 'IN_USE' CHECK (status IN ('IN_USE', 'FINISHED', 'NEED_RESTOCK')),
        notify_before_days INT NOT NULL DEFAULT 7,
        notes TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMP NOT NULL DEFAULT NOW ()
    );

CREATE TABLE
    IF NOT EXISTS item_reviews (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
        item_id UUID NOT NULL REFERENCES household_items (id) ON DELETE CASCADE,
        user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
        review_text TEXT,
        would_buy_again BOOLEAN,
        reviewed_at TIMESTAMP NOT NULL DEFAULT NOW ()
    );

-- notifications dùng chung cho: ITEM_EXPIRING, ITEM_RESTOCK, SUBSCRIPTION_EXPIRING
CREATE TABLE
    IF NOT EXISTS notifications (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
        user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        type VARCHAR(50) NOT NULL,
        title VARCHAR(200),
        message TEXT,
        related_id UUID, -- item_id hoặc subscription_id
        is_read BOOLEAN NOT NULL DEFAULT FALSE,
        created_at TIMESTAMP NOT NULL DEFAULT NOW ()
    );

-- Indexes
CREATE INDEX IF NOT EXISTS idx_household_user_status ON household_items (user_id, status);

CREATE INDEX IF NOT EXISTS idx_household_expiry ON household_items (user_id, expiry_date)
WHERE
    expiry_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications (user_id, is_read, created_at DESC);

-- Trigger auto-update updated_at
DROP TRIGGER IF EXISTS trigger_household_updated_at ON household_items;

CREATE TRIGGER trigger_household_updated_at BEFORE
UPDATE ON household_items FOR EACH ROW EXECUTE FUNCTION update_updated_at ();