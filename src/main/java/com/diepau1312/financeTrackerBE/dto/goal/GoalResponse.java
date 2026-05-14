package com.diepau1312.financeTrackerBE.dto.goal;

import com.diepau1312.financeTrackerBE.entity.Goal;
import com.diepau1312.financeTrackerBE.entity.Goal.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GoalResponse {

  private UUID id;
  private String name;
  private String icon;
  private String color;
  private GoalType type;
  private GoalSubtype subtype;
  private Long targetAmount;
  private Long currentAmount;
  private LocalDate deadline;
  private GoalStatus status;
  private LocalDateTime createdAt;

  // Credit card fields
  private Long creditLimit;
  private Integer billingDate;
  private BigDecimal interestRate;

  // Computed
  private Double progressPercent;
  private Long remainingAmount;
  private boolean overLimit;
  private Long balance;

  public static GoalResponse from(Goal g) {
    long current = g.getCurrentAmount();
    long target = g.getTargetAmount();

    // ── Tính toán theo type ───────────────────────────────────────────────
    double pct;
    long remaining;
    boolean over;
    long balance;

    switch (g.getType()) {
      case NORMAL -> {
        // Balance = current (đã được recalculate từ INCOME - EXPENSE)
        pct = 0;
        remaining = current; // remaining = số dư hiện tại
        over = current < 0; // âm = bội chi
        balance = current;
      }
      case DEBT -> {
        long limit = g.getCreditLimit() != null ? g.getCreditLimit()
            : target; // fallback về target nếu không có creditLimit
        pct = limit > 0 ? Math.min((current * 100.0) / limit, 100.0) : 0;
        remaining = Math.max(limit - current, 0L); // số tiền còn có thể nợ
        over = current > limit;               // vượt hạn mức
        balance = limit - current;               // hạn mức còn lại
      }
      default -> { // SAVINGS, INVESTMENT
        pct = target > 0 ? Math.min((current * 100.0) / target, 100.0) : 0;
        remaining = Math.max(target - current, 0L);
        over = false;
        balance = current;
      }
    }

    return GoalResponse.builder()
        .id(g.getId())
        .name(g.getName())
        .icon(g.getIcon())
        .color(g.getColor())
        .type(g.getType())
        .subtype(g.getSubtype())
        .targetAmount(target)
        .currentAmount(current)
        .deadline(g.getDeadline())
        .status(g.getStatus())
        .createdAt(g.getCreatedAt())
        .creditLimit(g.getCreditLimit())
        .billingDate(g.getBillingDate())
        .interestRate(g.getInterestRate())
        .progressPercent(Math.round(pct * 10.0) / 10.0)
        .remainingAmount(remaining)
        .overLimit(over)
        .balance(balance)
        .build();
  }
}