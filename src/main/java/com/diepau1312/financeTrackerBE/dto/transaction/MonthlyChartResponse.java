package com.diepau1312.financeTrackerBE.dto.transaction;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonthlyChartResponse {
  private int month;    // 1-12
  private String label;    // "Th1", "Th2"...
  private Long income;
  private Long expense;
  private Long balance;  // income - expense
}