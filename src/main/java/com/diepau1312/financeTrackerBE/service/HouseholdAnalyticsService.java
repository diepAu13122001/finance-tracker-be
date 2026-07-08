package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.household.HouseholdAnalyticsDTO;
import com.diepau1312.financeTrackerBE.dto.household.HouseholdAnalyticsSummaryDTO;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.repository.HouseholdAnalyticsRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HouseholdAnalyticsService {

  private final HouseholdAnalyticsRepository analyticsRepository;
  private final UserRepository userRepository;

  public HouseholdAnalyticsSummaryDTO getSummary() {
    String userEmail = SecurityUtil.getCurrentUserEmail();
    User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));
    UUID userId = user.getId();

    LocalDate today = LocalDate.now();
    LocalDate startOfCurrentMonth = today.withDayOfMonth(1);     // ngày 1 tháng này
    LocalDate startOfPreviousMonth = startOfCurrentMonth.minusMonths(1);

    // today.plusDays(1): để bao gồm cả ngày hôm nay (vì điều kiện là < to)
    Long currentTotal = analyticsRepository.sumSpendingBetween(userId, startOfCurrentMonth, today.plusDays(1));
    Long previousTotal = analyticsRepository.sumSpendingBetween(userId, startOfPreviousMonth, startOfCurrentMonth);
    currentTotal = currentTotal != null ? currentTotal : 0L;
    previousTotal = previousTotal != null ? previousTotal : 0L;

    // % thay đổi = (tháng này - tháng trước) / tháng trước * 100
    // Chỉ tính khi tháng trước > 0 để tránh chia cho 0
    Double percentageChange = null;
    if (previousTotal > 0) {
      percentageChange = BigDecimal.valueOf(currentTotal - previousTotal)
          .divide(BigDecimal.valueOf(previousTotal), 4, RoundingMode.HALF_UP)
          .multiply(BigDecimal.valueOf(100))
          .doubleValue();
    }

    // Lấy dữ liệu 6 tháng gần nhất để vẽ biểu đồ
    LocalDate sixMonthsAgo = startOfCurrentMonth.minusMonths(5);
    List<HouseholdAnalyticsDTO> breakdown =
        analyticsRepository.findSpendingByMonthAndCategory(userId, sixMonthsAgo);

    return HouseholdAnalyticsSummaryDTO.builder()
        .currentMonthTotal(currentTotal)
        .previousMonthTotal(previousTotal)
        .percentageChange(percentageChange)
        .breakdown(breakdown)
        .build();
  }
}