package com.diepau1312.financeTrackerBE.dto.transaction;

import com.diepau1312.financeTrackerBE.dto.category.CategoryResponse;
import com.diepau1312.financeTrackerBE.entity.Transaction;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import com.diepau1312.financeTrackerBE.dto.goal.GoalResponse;
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
  private String source;
  private LocalDateTime createdAt;
  private CategoryResponse category;
  private GoalResponse goal;
  private LocalDateTime updatedAt;

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
        .category(t.getCategory() != null
            ? CategoryResponse.from(t.getCategory())
            : null)
        .goal(t.getGoal() != null ? GoalResponse.from(t.getGoal()) : null)
        .updatedAt(t.getUpdatedAt())
        .build();
  }
}