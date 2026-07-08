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

  // Đường dẫn FE đang gọi
  @GetMapping("/restock-prediction/{itemId}")
  @RequiresPlan("PREMIUM")
  public ResponseEntity<RestockPredictionDTO> predictRestock(@PathVariable UUID itemId) {
    return ResponseEntity.ok(householdAiService.predictRestock(itemId));
  }
}