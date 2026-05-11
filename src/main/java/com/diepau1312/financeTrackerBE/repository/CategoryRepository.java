package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.Category;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

  /**
   * Lấy tất cả categories của 1 user, sắp xếp theo tên.
   * Dùng cho: trang quản lý categories.
   */
  List<Category> findByUserIdOrderByNameAsc(UUID userId);

  /**
   * Lấy categories của user theo type (INCOME hoặc EXPENSE).
   * Dùng cho: dropdown chọn category khi tạo transaction.
   */
  List<Category> findByUserIdAndTypeOrderByNameAsc(UUID userId, TransactionType type);

  /**
   * Tìm category theo id và userId — đảm bảo ownership.
   * Dùng cho: update/delete (user không được sửa category của user khác).
   */
  Optional<Category> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Check xem user đã có category với tên + type này chưa.
   * Dùng để validate khi tạo mới.
   */
  boolean existsByUserIdAndNameAndType(UUID userId, String name, TransactionType type);

  /**
   * Đếm số transactions đang dùng category này.
   * Dùng để cảnh báo trước khi xóa: "Category này đang có 23 giao dịch".
   */
  @Query("SELECT COUNT(t) FROM Transaction t WHERE t.category.id = :categoryId")
  long countTransactionsByCategoryId(UUID categoryId);

  // 👇 THÊM MỚI: tổng tiền tất cả transactions của category (all time)
  @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.category.id = :categoryId")
  Long sumAmountByCategoryId(@Param("categoryId") UUID categoryId);
}