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

  // Credit card
  private Long creditLimit;
  private Integer billingDate;
  private BigDecimal interestRate;

  // Installment
  private Integer numberOfPeriods; // tổng số kỳ
  private Long monthlyPayment; // tiền mỗi kỳ
  private Long initialAmount; // số vay ban đầu
  private Integer currentPeriod; // số kỳ đã trả (computed)
  private Integer remainingPeriods; // số kỳ còn lại (computed)

  // Computed chung
  private Double progressPercent;
  private Long remainingAmount;
  private boolean overLimit;
  private Long balance;

  public static GoalResponse from(Goal g) {
    long current = g.getCurrentAmount();
    long target = g.getTargetAmount();

    double pct;
    long remaining;
    boolean over;
    long balance;
    Integer currentPeriod = null;
    Integer remainingPeriods = null;

    switch (g.getType()) {
      case NORMAL -> {
        pct = 0;
        remaining = current;
        over = current < 0;
        balance = current;
      }
      case DEBT -> {
        if (g.getSubtype() == GoalSubtype.INSTALLMENT
            && g.getNumberOfPeriods() != null
            && g.getMonthlyPayment() != null
            && g.getMonthlyPayment() > 0) {
          // INSTALLMENT: progress theo số kỳ đã trả
          int paidPeriods = (int) (current / g.getMonthlyPayment());
          currentPeriod = Math.min(paidPeriods, g.getNumberOfPeriods());
          remainingPeriods = Math.max(g.getNumberOfPeriods() - currentPeriod, 0);
          pct = g.getNumberOfPeriods() > 0
              ? Math.min((currentPeriod * 100.0) / g.getNumberOfPeriods(), 100.0)
              : 0;
          remaining = (long) remainingPeriods * g.getMonthlyPayment();
          over = currentPeriod > g.getNumberOfPeriods();
          balance = remaining; // còn phải trả
        } else {
          // CREDIT_CARD
          long limit = g.getCreditLimit() != null ? g.getCreditLimit() : target;
          pct = limit > 0 ? Math.min((current * 100.0) / limit, 100.0) : 0;
          remaining = Math.max(limit - current, 0L);
          over = current > limit;
          balance = limit - current;
        }
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
        .numberOfPeriods(g.getNumberOfPeriods())
        .monthlyPayment(g.getMonthlyPayment())
        .initialAmount(g.getInitialAmount())
        .currentPeriod(currentPeriod)
        .remainingPeriods(remainingPeriods)
        .progressPercent(Math.round(pct * 10.0) / 10.0)
        .remainingAmount(remaining)
        .overLimit(over)
        .balance(balance)
        .build();
  }
}