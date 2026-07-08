package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.dto.household.HouseholdAnalyticsDTO;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface HouseholdAnalyticsRepository extends JpaRepository<HouseholdItem, UUID> {

  /**
   * Tổng hợp chi tiêu theo THÁNG và theo category cho một user.
   * <p>
   * FUNCTION('TO_CHAR', date, 'YYYY-MM') -> chuyển ngày thành chuỗi "2026-06"
   * trên PostgreSQL, để gom tất cả ngày trong cùng 1 tháng vào 1 cột.
   * GROUP BY theo (tháng + category) -> mỗi tháng có nhiều dòng (mỗi category 1 dòng).
   */
  @Query("""
          SELECT new com.diepau1312.financeTrackerBE.dto.household.HouseholdAnalyticsDTO(
              FUNCTION('TO_CHAR', h.purchaseDate, 'YYYY-MM'),
              h.category,
              SUM(h.price),
              COUNT(h)
          )
          FROM HouseholdItem h
          WHERE h.user.id = :userId
            AND h.purchaseDate >= :fromDate
            AND h.price IS NOT NULL
          GROUP BY FUNCTION('TO_CHAR', h.purchaseDate, 'YYYY-MM'), h.category
          ORDER BY FUNCTION('TO_CHAR', h.purchaseDate, 'YYYY-MM') ASC
      """)
  List<HouseholdAnalyticsDTO> findSpendingByMonthAndCategory(
      @Param("userId") UUID userId,
      @Param("fromDate") LocalDate fromDate);

  /**
   * Tổng chi tiêu đồ dùng trong khoảng [from, to) — dùng để so sánh tháng này vs tháng trước.
   * COALESCE(SUM, 0): nếu không có item nào thì trả 0 thay vì null.
   */
  @Query("""
          SELECT COALESCE(SUM(h.price), 0)
          FROM HouseholdItem h
          WHERE h.user.id = :userId
            AND h.purchaseDate >= :from
            AND h.purchaseDate < :to
            AND h.price IS NOT NULL
      """)
  Long sumSpendingBetween(
      @Param("userId") UUID userId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}