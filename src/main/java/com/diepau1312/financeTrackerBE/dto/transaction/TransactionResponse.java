package com.diepau1312.financeTrackerBE.dto.transaction;

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
  private String source;
  private LocalDateTime createdAt;
}