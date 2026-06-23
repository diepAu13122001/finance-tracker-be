package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.HouseholdItem;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HouseholdItemRepository extends JpaRepository<HouseholdItem, UUID> {

  Page<HouseholdItem> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Page<HouseholdItem> findByUserIdAndStatusOrderByCreatedAtDesc(
      UUID userId, ItemStatus status, Pageable pageable);

  Page<HouseholdItem> findByUserIdAndCategoryOrderByCreatedAtDesc(
      UUID userId, HouseholdItem.ItemCategory category, Pageable pageable);

  Optional<HouseholdItem> findByIdAndUserId(UUID id, UUID userId);

  List<HouseholdItem> findByUserIdAndNameIgnoreCaseOrderByPurchaseDateDesc(UUID userId, String name);

  /**
   * Tìm items sắp hết hạn trong khoảng [today, today + notifyBeforeDays].
   * Scheduler gọi mỗi ngày lúc 8am để tạo notifications.
   * <p>
   * Giải thích query:
   * - h.expiryDate BETWEEN :today AND :cutoff → hết hạn trong vòng N ngày
   * - h.status = 'IN_USE' → chỉ cảnh báo item đang dùng
   */
  @Query("""
      SELECT h FROM HouseholdItem h
      WHERE h.user.id = :userId
        AND h.status = 'IN_USE'
        AND h.expiryDate IS NOT NULL
        AND h.expiryDate BETWEEN :today AND :cutoff
      ORDER BY h.expiryDate ASC
      """)
  List<HouseholdItem> findExpiringItems(
      @Param("userId") UUID userId,
      @Param("today") LocalDate today,
      @Param("cutoff") LocalDate cutoff);

  /**
   * Lấy items sắp hết hạn của TẤT CẢ user — dùng cho global scheduler.
   */
  @Query("""
      SELECT h FROM HouseholdItem h
      WHERE h.status = 'IN_USE'
        AND h.expiryDate IS NOT NULL
        AND h.expiryDate BETWEEN :today AND :cutoff
      """)
  List<HouseholdItem> findAllExpiringItems(
      @Param("today") LocalDate today,
      @Param("cutoff") LocalDate cutoff);

  List<HouseholdItem> findByUserIdAndStatus(UUID userId, String status);

  /**
   * Query để lấy chi tiêu theo tháng
   * SELECT EXTRACT(MONTH FROM purchase_date) as month,
   * EXTRACT(YEAR FROM purchase_date) as year,
   * SUM(price) as totalSpent,
   * COUNT(*) as itemCount
   * FROM household_items
   * WHERE user_id = ? AND price IS NOT NULL
   * GROUP BY year, month
   * ORDER BY year DESC, month DESC
   */
  @Query(value = """
      SELECT 
          EXTRACT(MONTH FROM h.purchase_date)::INTEGER as month,
          EXTRACT(YEAR FROM h.purchase_date)::INTEGER as year,
          COALESCE(SUM(h.price), 0) as totalSpent,
          COUNT(h.id) as itemCount
      FROM household_items h
      WHERE h.user_id = :userId AND h.price IS NOT NULL
      GROUP BY EXTRACT(YEAR FROM h.purchase_date), 
               EXTRACT(MONTH FROM h.purchase_date)
      ORDER BY year DESC, month DESC
      """, nativeQuery = true)
  List<Object[]> findMonthlySpending(@Param("userId") UUID userId);

  /**
   * Query để lấy chi tiêu theo category (loại đồ dùng)
   * Ví dụ: Skincare: 1.2M, Housecare: 800k, ...
   */
  @Query(value = """
      SELECT h.category, 
             COALESCE(SUM(h.price), 0) as totalSpent
      FROM household_items h
      WHERE h.user_id = :userId AND h.price IS NOT NULL
      GROUP BY h.category
      ORDER BY totalSpent DESC
      """, nativeQuery = true)
  List<Object[]> findSpendingByCategory(@Param("userId") UUID userId);
}
