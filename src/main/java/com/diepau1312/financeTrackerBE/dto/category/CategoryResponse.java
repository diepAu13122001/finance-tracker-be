package com.diepau1312.financeTrackerBE.dto.category;

import com.diepau1312.financeTrackerBE.entity.Category;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
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
  private Long totalAmount;

  // ─── THÊM MỚI ─────────────────────────────────────────────────────────
  // ID của parent — null nếu là root
  private UUID parentCategoryId;
  // Tên parent (denormalized) — tiện cho frontend hiển thị "Sinh hoạt → Ăn uống"
  private String parentName;
  // Danh sách children — chỉ populated khi gọi getAll (tree view)
  // Khi mapping bình thường (vd: transaction.category) thì null
  private List<CategoryResponse> children;

  public static CategoryResponse from(Category cat) {
    return CategoryResponse.builder()
        .id(cat.getId())
        .name(cat.getName())
        .icon(cat.getIcon())
        .color(cat.getColor())
        .type(cat.getType())
        .createdAt(cat.getCreatedAt())
        // parent có thể là proxy LAZY — chỉ lấy id và name
        .parentCategoryId(cat.getParent() != null ? cat.getParent().getId() : null)
        .parentName(cat.getParent() != null ? cat.getParent().getName() : null)
        .build();
  }

  public static CategoryResponse from(Category cat, Long txCount) {
    CategoryResponse res = from(cat);
    res.setTransactionCount(txCount);
    return res;
  }

  public static CategoryResponse from(Category cat, Long txCount, Long totalAmount) {
    CategoryResponse res = from(cat, txCount);
    res.setTotalAmount(totalAmount);
    return res;
  }
}