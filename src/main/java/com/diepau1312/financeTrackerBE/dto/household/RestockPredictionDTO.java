package com.diepau1312.financeTrackerBE.dto.household;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestockPredictionDTO {
  private UUID itemId;
  private String itemName;
  private LocalDate predictedRunOutDate; // ngày dự đoán dùng hết (null nếu thiếu data)
  private Integer estimatedDaysLeft; // số ngày còn lại ước tính
  private String explanation; // giải thích tiếng Việt
  private boolean hasEnoughData; // có đủ lịch sử để dự đoán không
}