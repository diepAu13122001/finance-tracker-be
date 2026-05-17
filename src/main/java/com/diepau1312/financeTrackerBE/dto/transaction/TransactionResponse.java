package com.diepau1312.financeTrackerBE.dto.transaction;

import com.diepau1312.financeTrackerBE.dto.category.CategoryResponse;
import com.diepau1312.financeTrackerBE.dto.wallet.WalletResponse;
import com.diepau1312.financeTrackerBE.entity.Transaction;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {

  private UUID id;
  private TransactionType type;
  private Long amount;
  private String currency;
  private String note;
  private LocalDate transactionDate;
  private String source; // 'manual', 'transfer_out', 'transfer_in'
  private LocalDateTime createdAt;
  private CategoryResponse category;
  private LocalDateTime updatedAt;
  private WalletResponse wallet;

  // ── Transfer fields ──────────────────────────────────────────────────────
  /** UUID chung của cặp transfer (dùng để xóa cả 2) */
  private UUID transferPairId;

  /** Tên ví nguồn (source) trong transfer — luôn có nếu là TRANSFER */
  private String transferSourceWalletName;

  /** Tên ví đích (target) trong transfer — luôn có nếu là TRANSFER */
  private String transferTargetWalletName;

  public static TransactionResponse from(Transaction t) {
    return TransactionResponse.builder()
        .id(t.getId())
        .type(t.getType())
        .amount(t.getAmount())
        .currency(t.getCurrency())
        .note(t.getNote())
        .createdAt(t.getCreatedAt())
        .transactionDate(t.getTransactionDate())
        .source(t.getSource())
        .category(t.getCategory() != null ? CategoryResponse.from(t.getCategory()) : null)
        .updatedAt(t.getUpdatedAt())
        .wallet(t.getWallet() != null ? WalletResponse.from(t.getWallet()) : null)
        .transferPairId(t.getTransferPairId())
        // transferSource/Target phải được set thêm từ service vì cần lookup linked
        // wallet
        .build();
  }
}