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
  private Long monthlyBudget;     // null nếu không set budget
  private Long totalSpent;
  private Long transactionCount;
  private Double budgetProgressPercent; // null nếu không có budget
  private boolean overBudget;
}