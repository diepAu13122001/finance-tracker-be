package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.dto.household.RestockPredictionDTO;
import com.diepau1312.financeTrackerBE.service.HouseholdAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class HouseholdAiController {

  private final HouseholdAiService householdAiService;

  /**
   * Dự đoán khi nào cần mua lại một sản phẩm.
   * Yêu cầu gói PREMIUM.
   */
  @GetMapping("/household/restock-prediction/{itemId}")
  @RequiresPlan("PREMIUM")
  public ResponseEntity<RestockPredictionDTO> predictRestock(
      @PathVariable UUID itemId) {
    return ResponseEntity.ok(householdAiService.predictRestock(itemId));
  }

  @GetMapping("/restock-prediction/{itemId}")
  @RequiresPlan("PREMIUM")
  public ResponseEntity<RestockPredictionDTO> predictRestockPlannerPath(
      @PathVariable UUID itemId) {
    return ResponseEntity.ok(householdAiService.predictRestock(itemId));
  }
}
