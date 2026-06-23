package com.diepau1312.financeTrackerBE.dto.household;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HouseholdSpendingByMonth {
  private int month;           // 1-12
  private int year;            // 2026
  private BigDecimal totalSpent;  // Tổng tiền chi
  private long itemCount;      // Số item mua trong tháng
}