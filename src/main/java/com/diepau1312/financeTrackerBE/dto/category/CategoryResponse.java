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
  private Long monthlyBudget;             // budget user đặt (base)
  private Long effectiveBudget;           // budget sau khi rollover từ tháng trước
  private Long currentMonthSpent;         // đã chi tháng này
  private Long rolloverAmount;            // dư/thiếu từ tháng trước (+/-)
  private Double budgetProgressPercent;   // spent / effectiveBudget * 100
  private boolean overBudget;             // spent > effectiveBudget
  private boolean warningBudget;          // progress >= 80%

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
        .parentCategoryId(cat.getParent() != null ? cat.getParent().getId() : null)
        .parentName(cat.getParent() != null ? cat.getParent().getName() : null)
        .monthlyBudget(cat.getMonthlyBudget())
        .build();
  }

  /**
   * Build response kèm budget progress đã tính rollover.
   *
   * @param rolloverAmount:    dư từ tháng trước (>0) hoặc nợ (<0)
   * @param currentMonthSpent: tổng chi tháng hiện tại
   */
  public static CategoryResponse withBudgetProgress(
      Category cat, Long txCount, Long totalAmount,
      Long currentMonthSpent, Long rolloverAmount
  ) {
    CategoryResponse res = from(cat, txCount, totalAmount);
    res.setCurrentMonthSpent(currentMonthSpent != null ? currentMonthSpent : 0L);
    res.setRolloverAmount(rolloverAmount != null ? rolloverAmount : 0L);

    if (cat.getMonthlyBudget() != null && cat.getMonthlyBudget() > 0) {
      // Effective budget = base + rollover (có thể âm nếu tháng trước tiêu lố quá nhiều)
      long effective = cat.getMonthlyBudget() + (rolloverAmount != null ? rolloverAmount : 0L);
      res.setEffectiveBudget(Math.max(0L, effective)); // không cho âm hiển thị

      // Tính % so với effective budget
      if (effective > 0) {
        double pct = (res.getCurrentMonthSpent() * 100.0) / effective;
        res.setBudgetProgressPercent(Math.round(pct * 10.0) / 10.0);
        res.setOverBudget(res.getCurrentMonthSpent() > effective);
        res.setWarningBudget(pct >= 80.0);
      } else {
        // Effective = 0 → tháng này không còn budget
        res.setBudgetProgressPercent(100.0);
        res.setOverBudget(res.getCurrentMonthSpent() > 0);
        res.setWarningBudget(true);
      }
    }
    return res;
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