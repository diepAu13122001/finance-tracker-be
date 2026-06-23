package com.diepau1312.financeTrackerBE.dto.household;

import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemCategory;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class HouseholdAnalyticsDTO {

  private LocalDate purchaseDate;
  private ItemCategory category;
  private Long totalSpent;
  private Long itemCount;

  public HouseholdAnalyticsDTO(
      LocalDate purchaseDate,
      ItemCategory category,
      Long totalSpent,
      Long itemCount) {
    this.purchaseDate = purchaseDate;
    this.category = category;
    this.totalSpent = totalSpent;
    this.itemCount = itemCount;
  }
}