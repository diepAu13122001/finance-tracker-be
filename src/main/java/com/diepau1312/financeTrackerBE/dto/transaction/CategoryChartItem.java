package com.diepau1312.financeTrackerBE.dto.transaction;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CategoryChartItem {
  private UUID categoryId;     // null nếu transactions chưa phân loại
  private String categoryName;   // "Chưa phân loại" nếu null
  private String categoryColor;  // hex color
  private Long totalAmount;    // tổng amount của category này
  private Long transactionCount; // số giao dịch trong category
  private Double percentage;     // % so với tổng (0-100)
}