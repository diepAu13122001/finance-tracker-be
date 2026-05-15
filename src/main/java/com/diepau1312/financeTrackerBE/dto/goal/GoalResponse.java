package com.diepau1312.financeTrackerBE.dto.goal;

import com.diepau1312.financeTrackerBE.entity.Goal;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalStatus;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalSubtype;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalType;
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
  private GoalSubtype subtype;       // null nếu không phải DEBT
  private Long targetAmount;
  private Long currentAmount;
  private LocalDate deadline;
  private GoalStatus status;
  private LocalDateTime createdAt;

  // ── DEBT / CREDIT_CARD fields ─────────────────────────────────────────────
  private Long creditLimit;
  private Integer billingDate;
  private BigDecimal interestRate;

  // ── INSTALLMENT fields ────────────────────────────────────────────────────
  private Integer numberOfPeriods;
  private Long monthlyPayment;
  private Long initialAmount;

  // ── Computed fields ───────────────────────────────────────────────────────

  private Double progressPercent;    // 0–100, 0 nếu NORMAL wallet (không có target)
  private Long remainingAmount;      // max(target - current, 0)
  private boolean overLimit;         // DEBT: current > target → đang vượt mức

  public static GoalResponse from(Goal g) {
    long current = g.getCurrentAmount();
    long target = g.getTargetAmount();

    // NORMAL wallet không có target → progress luôn = 0
    double pct = (target > 0)
        ? Math.min((current * 100.0) / target, 100.0)
        : 0.0;

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
        // DEBT fields
        .creditLimit(g.getCreditLimit())
        .billingDate(g.getBillingDate())
        .interestRate(g.getInterestRate())
        // INSTALLMENT fields
        .numberOfPeriods(g.getNumberOfPeriods())
        .monthlyPayment(g.getMonthlyPayment())
        .initialAmount(g.getInitialAmount())
        // Computed
        .progressPercent(Math.round(pct * 10.0) / 10.0)
        .remainingAmount(Math.max(target - current, 0L))
        .overLimit(g.getType() == GoalType.DEBT && current > target)
        .build();
  }
}