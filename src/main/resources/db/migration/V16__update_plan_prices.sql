-- V16: Đổi sang billing hàng tháng, cập nhật giá
UPDATE subscription_plans
SET price_vnd     = 69000,
    billing_cycle = 'MONTHLY'
WHERE id = 'PLUS';

UPDATE subscription_plans
SET price_vnd     = 129000,
    billing_cycle = 'MONTHLY'
WHERE id = 'PREMIUM';