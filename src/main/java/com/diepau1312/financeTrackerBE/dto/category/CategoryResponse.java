package com.diepau1312.financeTrackerBE.dto.category;

import com.diepau1312.financeTrackerBE.entity.Category;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CategoryResponse {
  private UUID id;
  private String name;
  private String icon;
  private String color;
  private TransactionType type;
  private LocalDateTime createdAt;
  private Long transactionCount;
  private Long totalAmount;  // 👈 THÊM MỚI

  public static CategoryResponse from(Category cat) {
    return CategoryResponse.builder()
        .id(cat.getId())
        .name(cat.getName())
        .icon(cat.getIcon())
        .color(cat.getColor())
        .type(cat.getType())
        .createdAt(cat.getCreatedAt())
        .build();
  }

  public static CategoryResponse from(Category cat, Long txCount) {
    CategoryResponse res = from(cat);
    res.setTransactionCount(txCount);
    return res;
  }

  // 👇 THÊM MỚI: overload với cả count + amount
  public static CategoryResponse from(Category cat, Long txCount, Long totalAmount) {
    CategoryResponse res = from(cat, txCount);
    res.setTotalAmount(totalAmount);
    return res;
  }
}