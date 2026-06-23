package com.diepau1312.financeTrackerBE.dto.household;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdAnalyticsSummaryDTO {

    // Tổng chi tiêu đồ dùng tháng hiện tại
    private Long currentMonthTotal;

    // Tổng chi tiêu tháng trước (để tính % thay đổi)
    private Long previousMonthTotal;

    // Phần trăm thay đổi so với tháng trước (dương = tăng, âm = giảm)
    private Double percentageChange;

    // Danh sách chi tiết theo từng category và từng tháng
    private List<HouseholdAnalyticsDTO> breakdown;
}
