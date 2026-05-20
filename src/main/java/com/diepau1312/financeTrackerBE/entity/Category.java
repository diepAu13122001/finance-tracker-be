package com.diepau1312.financeTrackerBE.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, length = 20)
  @Builder.Default
  private String icon = "tag";

  @Column(nullable = false, length = 7)
  @Builder.Default
  private String color = "#82b01e";

  @Column(name = "monthly_budget")
  private Long monthlyBudget;  // NULL = không có budget

  // ── THÊM MỚI: Ngày đầu của KỲ đầu tiên áp dụng budget hiện tại ──
  // NULL  → category chưa từng có budget
  // Date  → kỳ trước đó (< ngày này) KHÔNG được tính rollover
  @Column(name = "budget_started_at")
  private LocalDate budgetStartedAt;

  @Column(nullable = false, length = 10)
  @Enumerated(EnumType.STRING)
  private TransactionType type;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_category_id")
  private Category parent;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  public boolean isRoot() {
    return parent == null;
  }
}