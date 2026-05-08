package com.diepau1312.financeTrackerBE.entity;

import jakarta.persistence.*;
import lombok.*;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import java.time.LocalDateTime;
import java.util.UUID;

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

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}