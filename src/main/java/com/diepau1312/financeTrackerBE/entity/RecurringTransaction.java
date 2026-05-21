package com.diepau1312.financeTrackerBE.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** Loại giao dịch — chỉ INCOME hoặc EXPENSE (không hỗ trợ TRANSFER cho recurring) */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Transaction.TransactionType type;

  @Column(nullable = false)
  private Long amount;

  private String note;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private Category category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wallet_id")
  private Wallet wallet;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Frequency frequency;

  /** Ngày trong tháng (1–31) — dùng cho MONTHLY và YEARLY */
  @Column(name = "day_of_month")
  private Integer dayOfMonth;

  /** Thứ trong tuần (1=T2, 7=CN) — dùng cho WEEKLY */
  @Column(name = "day_of_week")
  private Integer dayOfWeek;

  @Column(name = "next_execution_date", nullable = false)
  private LocalDate nextExecutionDate;

  @Builder.Default
  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public enum Frequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
  }
}
