package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.Category;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

  // Đếm số con của 1 parent — dùng để check trước khi xóa
  @Query("SELECT COUNT(c) FROM Category c WHERE c.parent.id = :parentId")
  long countChildrenByParentId(@Param("parentId") UUID parentId);

  // Lấy tất cả con của 1 parent (sorted theo tên)
  List<Category> findByParentIdOrderByNameAsc(UUID parentId);

  // Lấy categories root (parent IS NULL) của user, filter theo type nếu cần
  @Query("SELECT c FROM Category c " +
      "WHERE c.user.id = :userId AND c.parent IS NULL " +
      "AND (:type IS NULL OR c.type = :type) " +
      "ORDER BY c.name ASC")
  List<Category> findRootsByUserId(@Param("userId") UUID userId,
                                   @Param("type") TransactionType type);

  /**
   * Tổng EXPENSE của category trong 1 tháng.
   * Loại transfer (source != 'manual') vì transfer không phải chi tiêu thực.
   */
  @Query("""
          SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
          WHERE t.category.id = :categoryId
            AND t.type = 'EXPENSE'
            AND t.source = 'manual'
            AND EXTRACT(YEAR FROM t.transactionDate) = :year
            AND EXTRACT(MONTH FROM t.transactionDate) = :month
      """)
  Long sumExpenseByMonth(@Param("categoryId") UUID categoryId,
                         @Param("year") int year,
                         @Param("month") int month);

  /**
   * Top N categories chi tiêu cao nhất trong khoảng thời gian.
   * Dùng cho Dashboard widget "Top chi tiêu".
   * Native query để xử lý EXTRACT() linh hoạt với cả month/quarter/year.
   */
  @Query(value = """
          SELECT c.id, c.name, c.icon, c.color, c.monthly_budget,
                 SUM(t.amount) AS spent,
                 COUNT(t.id) AS tx_count
          FROM transactions t
          JOIN categories c ON t.category_id = c.id
          WHERE t.user_id = CAST(:userId AS uuid)
            AND t.type = 'EXPENSE'
            AND t.source = 'manual'
            AND t.transaction_date >= :startDate
            AND t.transaction_date <= :endDate
          GROUP BY c.id, c.name, c.icon, c.color, c.monthly_budget
          ORDER BY spent DESC
          LIMIT :limit
      """, nativeQuery = true)
  List<Object[]> findTopSpendingCategories(@Param("userId") UUID userId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate,
                                           @Param("limit") int limit);
}