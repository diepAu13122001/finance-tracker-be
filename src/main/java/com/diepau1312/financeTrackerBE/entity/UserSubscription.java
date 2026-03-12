package com.diepau1312.financeTrackerBE.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Quan hệ 1-1 với User
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    // Quan hệ nhiều-1 với SubscriptionPlan
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;  // null = không hết hạn

    @Column(name = "payment_ref")
    private String paymentRef;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Helper method — kiểm tra subscription còn hiệu lực không
    public boolean isActive() {
        if (!"ACTIVE".equals(status)) return false;
        if (expiresAt == null) return true;  // free plan
        return LocalDateTime.now().isBefore(expiresAt);
    }

    // Lấy planId dạng String để so sánh
    public String getPlanId() {
        return plan != null ? plan.getId() : "FREE";
    }
}