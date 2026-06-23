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
   * Tổng hợp chi tiêu theo category và theo tháng cho một user.
   * <p>
   * JPQL dùng FUNCTION('TO_CHAR', ...) để format date thành "YYYY-MM" trên
   * PostgreSQL.
   * GROUP BY theo chuỗi tháng + category để ra từng dòng riêng.
   *
   * @param userId   ID của user cần lấy dữ liệu
   * @param fromDate ngày bắt đầu của khoảng thời gian cần phân tích
   */
  @Query("""
          SELECT new com.diepau1312.financeTrackerBE.dto.household.HouseholdAnalyticsDTO(
              h.purchaseDate,
              h.category,
              SUM(h.price),
              COUNT(h)
          )
          FROM HouseholdItem h
          WHERE h.user.id = :userId
            AND h.purchaseDate >= :fromDate
            AND h.price IS NOT NULL
          GROUP BY h.purchaseDate, h.category
      """)
  List<HouseholdAnalyticsDTO> findSpendingByMonthAndCategory(
      @Param("userId") UUID userId,
      @Param("fromDate") LocalDate fromDate);

  /**
   * Tính tổng chi tiêu đồ dùng trong một khoảng thời gian cụ thể.
   * Dùng để so sánh tháng này vs tháng trước.
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
