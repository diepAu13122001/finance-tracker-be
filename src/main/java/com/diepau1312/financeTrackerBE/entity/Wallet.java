package com.diepau1312.financeTrackerBE.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String icon = "wallet";

    @Column(nullable = false, length = 7)
    @Builder.Default
    private String color = "#8b5cf6";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletType type;

    // Chỉ dùng cho DEBT: CREDIT_CARD hoặc INSTALLMENT
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WalletSubtype subtype;

    // NORMAL: balance = SUM(INCOME) - SUM(EXPENSE), có thể âm
    // DEBT: currentAmount = SUM(EXPENSE) - SUM(INCOME) = số nợ hiện tại
    @Column(name = "current_amount", nullable = false)
    @Builder.Default
    private Long currentAmount = 0L;

    // ── DEBT CREDIT_CARD ──────────────────────────────────────────────────────
    @Column(name = "credit_limit")
    private Long creditLimit;

    @Column(name = "billing_date")
    private Integer billingDate;

    // ── DEBT INSTALLMENT ──────────────────────────────────────────────────────
    @Column(name = "number_of_periods")
    private Integer numberOfPeriods;

    @Column(name = "monthly_payment")
    private Long monthlyPayment;

    @Column(name = "initial_amount")
    private Long initialAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum WalletType {
        NORMAL, // Tiền mặt, ngân hàng, ví điện tử
        DEBT // Thẻ tín dụng, vay trả góp
    }

    public enum WalletSubtype {
        CREDIT_CARD, // Thẻ tín dụng (creditLimit, billingDate)
        INSTALLMENT // Trả góp (numberOfPeriods, monthlyPayment, initialAmount)
    }

    public enum WalletStatus {
        ACTIVE,
        CANCELLED
    }
}