package com.diepau1312.financeTrackerBE.dto.household;

import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemCategory;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class HouseholdAnalyticsDTO {

  // Chuỗi tháng dạng "YYYY-MM" (ví dụ "2026-06") — dùng làm trục X của biểu đồ
  private String month;
  private ItemCategory category;
  private Long totalSpent;
  private Long itemCount;

  // Constructor để JPQL "SELECT new ..." map trực tiếp kết quả query vào DTO
  public HouseholdAnalyticsDTO(String month, ItemCategory category, Long totalSpent, Long itemCount) {
    this.month = month;
    this.category = category;
    this.totalSpent = totalSpent;
    this.itemCount = itemCount;
  }
}