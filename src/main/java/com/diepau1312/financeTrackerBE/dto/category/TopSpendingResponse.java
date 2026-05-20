package com.diepau1312.financeTrackerBE.dto.category;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TopSpendingResponse {
  private UUID categoryId;
  private String name;
  private String icon;
  private String color;

  private Long monthlyBudget;       // budget gốc user đặt
  private Long effectiveBudget;     // = monthlyBudget + rolloverAmount (sau rollover)
  private Long rolloverAmount;      // dư/lố từ kỳ trước (có thể âm)

  private Long totalSpent;
  private Long transactionCount;

  // % tính theo effectiveBudget — ĐỒNG NHẤT với CategoryCard
  private Double budgetProgressPercent;
  private boolean overBudget;
}