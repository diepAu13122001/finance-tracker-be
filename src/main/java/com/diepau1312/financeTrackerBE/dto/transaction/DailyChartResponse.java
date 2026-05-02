package com.diepau1312.financeTrackerBE.dto.transaction;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DailyChartResponse {
  private String date;        // "2026-01-14"
  private Long   income;
  private Long   expense;
}