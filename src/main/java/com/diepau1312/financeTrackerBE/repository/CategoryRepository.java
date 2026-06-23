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

  List<Category> findByUserIdOrderByNameAsc(UUID userId);

  List<Category> findByUserIdAndTypeOrderByNameAsc(UUID userId, TransactionType type);

  Optional<Category> findByIdAndUserId(UUID id, UUID userId);

  Optional<Category> findFirstByUserIdAndTypeAndNameContainingIgnoreCaseOrderByNameAsc(
      UUID userId, TransactionType type, String name);

  boolean existsByUserIdAndNameAndType(UUID userId, String name, TransactionType type);

  @Query("SELECT COUNT(t) FROM Transaction t WHERE t.category.id = :categoryId")
  long countTransactionsByCategoryId(UUID categoryId);

  @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.category.id = :categoryId")
  Long sumAmountByCategoryId(@Param("categoryId") UUID categoryId);

  @Query("SELECT COUNT(c) FROM Category c WHERE c.parent.id = :parentId")
  long countChildrenByParentId(@Param("parentId") UUID parentId);

  List<Category> findByParentIdOrderByNameAsc(UUID parentId);

  @Query("SELECT c FROM Category c " + "WHERE c.user.id = :userId AND c.parent IS NULL " + "AND (:type IS NULL OR c.type = :type) " + "ORDER BY c.name ASC")
  List<Category> findRootsByUserId(@Param("userId") UUID userId, @Param("type") TransactionType type);

  // ── Giữ lại để tương thích — sẽ phase out ──
  @Query("""
          SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
          WHERE t.category.id = :categoryId
            AND t.type = 'EXPENSE'
            AND t.source = 'manual'
            AND EXTRACT(YEAR FROM t.transactionDate) = :year
            AND EXTRACT(MONTH FROM t.transactionDate) = :month
      """)
  Long sumExpenseByMonth(@Param("categoryId") UUID categoryId, @Param("year") int year, @Param("month") int month);

  /**
   * THÊM MỚI: Tính chi tiêu của category trong khoảng [startDate, endDate].
   * Dùng cho monthStartDay khác 1 — kỳ ngân sách lệch với lịch dương lịch.
   */
  @Query("""
          SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
          WHERE t.category.id = :categoryId
            AND t.type = 'EXPENSE'
            AND t.source = 'manual'
            AND t.transactionDate >= :startDate
            AND t.transactionDate <= :endDate
      """)
  Long sumExpenseByPeriod(@Param("categoryId") UUID categoryId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

  /**
   * THÊM MỚI: Tổng ngân sách của các con (trừ excludeId nếu update).
   * Dùng để validate: SUM(children.budget) ≤ parent.budget
   */
  @Query("""
          SELECT COALESCE(SUM(c.monthlyBudget), 0) FROM Category c
          WHERE c.parent.id = :parentId
            AND c.monthlyBudget IS NOT NULL
            AND (:excludeId IS NULL OR c.id <> :excludeId)
      """)
  Long sumChildrenBudget(@Param("parentId") UUID parentId, @Param("excludeId") UUID excludeId);

  @Query(value = """
          SELECT c.id, c.name, c.icon, c.color, c.monthly_budget,
                 SUM(t.amount) AS spent,
                 COUNT(t.id) AS tx_count,
                 c.budget_started_at
          FROM transactions t
          JOIN categories c ON t.category_id = c.id
          WHERE t.user_id = CAST(:userId AS uuid)
            AND t.type = 'EXPENSE'
            AND t.source = 'manual'
            AND t.transaction_date >= :startDate
            AND t.transaction_date <= :endDate
          GROUP BY c.id, c.name, c.icon, c.color, c.monthly_budget, c.budget_started_at
          ORDER BY spent DESC
          LIMIT :limit
      """, nativeQuery = true)
  List<Object[]> findTopSpendingCategories(@Param("userId") UUID userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("limit") int limit);
}
