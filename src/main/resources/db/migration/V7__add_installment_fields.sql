-- V7: Thêm fields cho DEBT INSTALLMENT
ALTER TABLE goals
    ADD COLUMN IF NOT EXISTS number_of_periods  INT,           -- tổng số kỳ
    ADD COLUMN IF NOT EXISTS monthly_payment     BIGINT,        -- tiền góp mỗi kỳ (gồm cả lãi)
    ADD COLUMN IF NOT EXISTS initial_amount      BIGINT;        -- số tiền mượn ban đầu

-- Bỏ interest_rate khỏi form (giữ column để sau này tính lãi credit card)
-- Không drop column vì data cũ có thể đã có giá trị