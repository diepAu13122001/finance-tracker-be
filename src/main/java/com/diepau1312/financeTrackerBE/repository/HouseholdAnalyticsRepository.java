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
   * to_char(date, 'YYYY-MM') -> chuyển ngày thành chuỗi "2026-06" (PostgreSQL),
   * để gom tất cả ngày trong cùng 1 tháng vào 1 nhóm.
   * <p>
   * QUAN TRỌNG: phải bọc CAST(... AS String).
   * Lý do: Hibernate 6.5 không biết trước kiểu trả về của hàm to_char
   * (nó là hàm tự do, không đăng ký sẵn). Khi map vào constructor
   * HouseholdAnalyticsDTO(String, ...) mà Hibernate không xác định được
   * kiểu là String -> query fail lúc khởi động app ("Could not create query").
   * CAST(... AS String) nói rõ cho Hibernate biết kết quả là chuỗi.
   * <p>
   * GROUP BY theo (tháng + category) -> mỗi tháng có nhiều dòng (mỗi category 1 dòng).
   */
  @Query("""
          SELECT new com.diepau1312.financeTrackerBE.dto.household.HouseholdAnalyticsDTO(
              CAST(FUNCTION('to_char', h.purchaseDate, 'YYYY-MM') AS String),
              h.category,
              SUM(h.price),
              COUNT(h)
          )
          FROM HouseholdItem h
          WHERE h.user.id = :userId
            AND h.purchaseDate >= :fromDate
            AND h.price IS NOT NULL
          GROUP BY CAST(FUNCTION('to_char', h.purchaseDate, 'YYYY-MM') AS String), h.category
          ORDER BY CAST(FUNCTION('to_char', h.purchaseDate, 'YYYY-MM') AS String) ASC
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