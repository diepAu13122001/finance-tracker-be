package com.diepau1312.financeTrackerBE.dto.chart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryChartResponse {

    private UUID categoryId; // null nếu giao dịch chưa phân loại
    private String categoryName; // "Ăn uống", "Uncategorized"...
    private String categoryColor; // hex "#FF5733"
    private Long totalAmount;
    private Long transactionCount;
    private Double percentage; // 0.0 – 100.0
}