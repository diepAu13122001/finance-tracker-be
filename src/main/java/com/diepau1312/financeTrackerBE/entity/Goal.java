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

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private GoalSubtype subtype;  // 👈 THÊM: chỉ dùng cho DEBT

  @Column(name = "target_amount", nullable = false)
  @Builder.Default
  private Long targetAmount = 0L;

  @Column(name = "current_amount", nullable = false)
  @Builder.Default
  private Long currentAmount = 0L;

  // ── DEBT CREDIT_CARD fields ──────────────────────────────────────────────
  @Column(name = "credit_limit")
  private Long creditLimit;    // hạn mức thẻ

  @Column(name = "billing_date")
  private Integer billingDate; // ngày đáo hạn hàng tháng (1-28)

  @Column(name = "interest_rate", precision = 5, scale = 2)
  private BigDecimal interestRate; // lãi suất %/tháng

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

  // ── Enums ────────────────────────────────────────────────────────────────
  public enum GoalType {
    SAVINGS, DEBT, INVESTMENT, NORMAL  // 👈 THÊM NORMAL
  }

  public enum GoalSubtype {
    CREDIT_CARD, INSTALLMENT
  }

  public enum GoalStatus {
    ACTIVE, COMPLETED, CANCELLED
  }
}