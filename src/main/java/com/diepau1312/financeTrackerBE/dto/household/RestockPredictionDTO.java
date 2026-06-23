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

  // ID của item cần dự đoán
  private UUID itemId;

  // Tên sản phẩm
  private String itemName;

  // Ngày dự đoán sẽ dùng hết (có thể null nếu không đủ dữ liệu)
  private LocalDate predictedRunOutDate;

  // Số ngày ước tính còn lại
  private Integer estimatedDaysLeft;

  // Giải thích từ AI (tiếng Việt)
  private String explanation;

  // Có đủ dữ liệu để dự đoán không
  private boolean hasEnoughData;
}