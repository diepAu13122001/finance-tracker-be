package com.diepau1312.financeTrackerBE.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TransactionType type;

  @Column(nullable = false)
  private Long amount;

  @Column(nullable = false, length = 3)
  @Builder.Default
  private String currency = "VND";

  private String note;

  @Column(name = "transaction_date", nullable = false)
  private LocalDate transactionDate;

  @Column(nullable = false, length = 20)
  @Builder.Default
  private String source = "manual";
  // Giá trị đặc biệt cho transfer:
  // 'transfer_out' = tiền rời khỏi ví nguồn
  // 'transfer_in'  = tiền đến ví đích

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private Category category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wallet_id")
  private Wallet wallet;

  // ── Transfer pair fields ───────────────────────────────────────────────────

  /** UUID dùng chung giữa 2 transaction của 1 lần transfer */
  @Column(name = "transfer_pair_id")
  private UUID transferPairId;

  /** ID của ví đối diện trong transfer (target nếu đây là transfer_out, source nếu là transfer_in) */
  @Column(name = "linked_wallet_id")
  private UUID linkedWalletId;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public enum TransactionType {
    INCOME, EXPENSE, TRANSFER
  }
}