package com.diepau1312.financeTrackerBE.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @Column(length = 20)
    private String id;  // 'FREE', 'PLUS', 'PREMIUM'

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "price_vnd", nullable = false)
    private Long priceVnd = 0L;

    @Column(name = "billing_cycle", length = 20)
    private String billingCycle;  // 'YEARLY' hoặc null

    // JSONB lưu dưới dạng String — parse trong service khi cần
    @Column(columnDefinition = "jsonb")
    private String features;
}