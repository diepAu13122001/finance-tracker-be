package com.diepau1312.financeTrackerBE.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "goals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Goal {

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
  private String color = "#82b01e";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GoalType type;

  // Chỉ dùng cho DEBT: CREDIT_CARD hoặc INSTALLMENT
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private GoalSubtype subtype;

  // Goals: target_amount > 0 (bắt buộc có mục tiêu)
  // Wallets (NORMAL): target_amount = 0 (không có mục tiêu, chỉ track số dư)
  @Column(name = "target_amount", nullable = false)
  @Builder.Default
  private Long targetAmount = 0L;

  // Goals: tổng tiền đã đóng góp (>= 0)
  // Wallets (NORMAL): số dư = SUM(INCOME) - SUM(EXPENSE), có thể âm
  @Column(name = "current_amount", nullable = false)
  @Builder.Default
  private Long currentAmount = 0L;

  // ── DEBT / CREDIT_CARD fields ─────────────────────────────────────────────

  @Column(name = "credit_limit")
  private Long creditLimit;          // hạn mức thẻ tín dụng

  @Column(name = "billing_date")
  private Integer billingDate;       // ngày đáo hạn hàng tháng (1-28)

  @Column(name = "interest_rate", precision = 5, scale = 2)
  private BigDecimal interestRate;   // lãi suất %/tháng

  // ── INSTALLMENT fields ────────────────────────────────────────────────────

  @Column(name = "number_of_periods")
  private Integer numberOfPeriods;   // tổng số kỳ trả góp

  @Column(name = "monthly_payment")
  private Long monthlyPayment;       // tiền góp mỗi kỳ (gốc + lãi)

  @Column(name = "initial_amount")
  private Long initialAmount;        // số tiền mượn ban đầu

  // ── Common ────────────────────────────────────────────────────────────────

  @Column(name = "deadline")
  private LocalDate deadline;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private GoalStatus status = GoalStatus.ACTIVE;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  // ── Enums ─────────────────────────────────────────────────────────────────

  public enum GoalType {
    SAVINGS,
    DEBT,
    INVESTMENT,
    NORMAL      // Wallet thường: tiền mặt, ngân hàng, ví điện tử
  }

  public enum GoalSubtype {
    CREDIT_CARD,    // Thẻ tín dụng (có creditLimit, billingDate, interestRate)
    INSTALLMENT     // Vay trả góp (có numberOfPeriods, monthlyPayment, initialAmount)
  }

  public enum GoalStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
  }
}