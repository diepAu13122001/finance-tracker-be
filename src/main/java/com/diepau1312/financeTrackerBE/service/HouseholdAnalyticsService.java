package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.household.HouseholdSpendingByMonth;
import com.diepau1312.financeTrackerBE.repository.HouseholdItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Service để xử lý analytics cho Household Tracker
 * Gồm: chi tiêu theo tháng, chi tiêu theo category, chi tiêu tối đa/tối thiểu
 */
@Service
@RequiredArgsConstructor
public class HouseholdAnalyticsService {

  private final HouseholdItemRepository householdItemRepository;

  /**
   * Lấy chi tiêu theo tháng (trong 12 tháng gần nhất)
   * @return List<HouseholdSpendingByMonthDTO> được sắp xếp theo tháng gần nhất trước
   */
  public List<HouseholdSpendingByMonth> getMonthlySpending(UUID userId) {
    List<Object[]> results = householdItemRepository.findMonthlySpending(userId);
    List<HouseholdSpendingByMonth> dtos = new ArrayList<>();

    for (Object[] row : results) {
      // row[0] = month, row[1] = year, row[2] = totalSpent, row[3] = itemCount
      int month = ((Number) row[0]).intValue();
      int year = ((Number) row[1]).intValue();
      BigDecimal totalSpent = new BigDecimal(row[2].toString());
      long itemCount = ((Number) row[3]).longValue();

      dtos.add(new HouseholdSpendingByMonth(month, year, totalSpent, itemCount));
    }

    return dtos;
  }

  /**
   * Lấy chi tiêu theo loại đồ dùng (category)
   * @return Map<String, BigDecimal> ví dụ: {"SKINCARE": 1200000, "HOUSECARE": 800000}
   */
  public Map<String, BigDecimal> getSpendingByCategory(UUID userId) {
    List<Object[]> results = householdItemRepository.findSpendingByCategory(userId);
    Map<String, BigDecimal> categorySpending = new LinkedHashMap<>();

    for (Object[] row : results) {
      // row[0] = category, row[1] = totalSpent
      String category = (String) row[0];
      BigDecimal totalSpent = new BigDecimal(row[1].toString());
      categorySpending.put(category, totalSpent);
    }

    return categorySpending;
  }

  /**
   * Tính phần trăm chi tiêu tăng so với tháng trước
   * Ví dụ: nếu tháng này chi 800k, tháng trước chi 700k → +14%
   */
  public Double getMonthlyGrowthPercentage(UUID userId) {
    List<HouseholdSpendingByMonth> monthly = getMonthlySpending(userId);

    if (monthly.size() < 2) {
      return 0.0; // Không đủ dữ liệu
    }

    // monthly[0] là tháng gần nhất, monthly[1] là tháng trước
    BigDecimal thisMonth = monthly.get(0).getTotalSpent();
    BigDecimal lastMonth = monthly.get(1).getTotalSpent();

    if (lastMonth.compareTo(BigDecimal.ZERO) == 0) {
      return 0.0;
    }

    return thisMonth
        .subtract(lastMonth)
        .divide(lastMonth, 4, java.math.RoundingMode.HALF_UP)
        .multiply(new BigDecimal(100))
        .doubleValue();
  }
}