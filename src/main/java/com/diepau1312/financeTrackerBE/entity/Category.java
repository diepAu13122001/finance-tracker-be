package com.diepau1312.financeTrackerBE.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

  @Column(nullable = false, length = 10)
  @Enumerated(EnumType.STRING)
  private TransactionType type;

  // ── THÊM MỚI: self-reference đến category cha ──────────────────────────
  // LAZY để tránh load cha mỗi khi load con
  // Nullable: category root (cấp 1) không có parent
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_category_id")
  private Category parent;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  // Helper: kiểm tra có phải root không (không có parent)
  public boolean isRoot() {
    return parent == null;
  }
}