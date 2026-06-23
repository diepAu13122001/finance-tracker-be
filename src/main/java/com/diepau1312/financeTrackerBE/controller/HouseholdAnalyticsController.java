package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.dto.household.HouseholdAnalyticsSummaryDTO;
import com.diepau1312.financeTrackerBE.service.HouseholdAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/household/analytics")
@RequiredArgsConstructor
@Tag(name = "Household Analytics", description = "Phân tích chi tiêu đồ dùng gia đình")
public class HouseholdAnalyticsController {

  private final HouseholdAnalyticsService analyticsService;

  /**
   * Trả về tóm tắt chi tiêu đồ dùng gia đình của user.
   * Yêu cầu gói PREMIUM.
   */
  @GetMapping("/summary")
  @RequiresPlan("PREMIUM")
  @Operation(summary = "Lấy tóm tắt chi tiêu đồ dùng gia đình")
  public ResponseEntity<HouseholdAnalyticsSummaryDTO> getSummary() {
    return ResponseEntity.ok(analyticsService.getSummary());
  }
}