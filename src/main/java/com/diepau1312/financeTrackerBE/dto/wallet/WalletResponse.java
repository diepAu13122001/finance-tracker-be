package com.diepau1312.financeTrackerBE.dto.wallet;

import com.diepau1312.financeTrackerBE.entity.Wallet;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletStatus;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletSubtype;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class WalletResponse {

    private UUID id;
    private String name;
    private String icon;
    private String color;
    private WalletType type;
    private WalletSubtype subtype;

    // Số dư hiện tại
    // NORMAL: income - expense (có thể âm)
    // DEBT: expense - income (số nợ đang có)
    private Long currentAmount;

    // ── CREDIT_CARD fields ────────────────────────────────────────────────────
    private Long creditLimit;
    private Integer billingDate;

    // ── INSTALLMENT fields ────────────────────────────────────────────────────
    private Integer numberOfPeriods;
    private Long monthlyPayment;
    private Long initialAmount;

    private WalletStatus status;
    private LocalDateTime createdAt;

    // ── Computed fields ───────────────────────────────────────────────────────
    private Long balance; // alias của currentAmount, dùng cho NORMAL
    private Double progressPercent; // DEBT với creditLimit: (currentAmount/creditLimit)*100
    private Long remainingAmount; // DEBT: max(creditLimit - currentAmount, 0)
    private boolean overLimit; // DEBT: currentAmount > creditLimit

    public static WalletResponse from(Wallet w) {
        long current = w.getCurrentAmount();
        Long limit = w.getCreditLimit();

        double pct = 0.0;
        long remaining = 0L;
        boolean over = false;

        if (w.getType() == WalletType.DEBT && limit != null && limit > 0) {
            pct = Math.min((current * 100.0) / limit, 100.0);
            remaining = Math.max(limit - current, 0L);
            over = current > limit;
        }

        return WalletResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .icon(w.getIcon())
                .color(w.getColor())
                .type(w.getType())
                .subtype(w.getSubtype())
                .currentAmount(current)
                .creditLimit(limit)
                .billingDate(w.getBillingDate())
                .numberOfPeriods(w.getNumberOfPeriods())
                .monthlyPayment(w.getMonthlyPayment())
                .initialAmount(w.getInitialAmount())
                .status(w.getStatus())
                .createdAt(w.getCreatedAt())
                .balance(current)
                .progressPercent(Math.round(pct * 10.0) / 10.0)
                .remainingAmount(remaining)
                .overLimit(over)
                .build();
    }
}