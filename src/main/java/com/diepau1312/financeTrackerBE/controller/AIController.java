package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.dto.ai.AIParseRequest;
import com.diepau1312.financeTrackerBE.dto.ai.AIParseResponse;
import com.diepau1312.financeTrackerBE.service.GeminiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI", description = "AI features — Plus plan required")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

  private final GeminiService geminiService;

  @Operation(
      summary = "Parse text tiếng Việt thành transaction",
      description = "Plus feature. Gemini API key do user tự cung cấp, không lưu server."
  )
  @PostMapping("/parse-transaction")
  @RequiresPlan("PLUS")
  public ResponseEntity<AIParseResponse> parseTransaction(
      @Valid @RequestBody AIParseRequest request) {
    return ResponseEntity.ok(
        geminiService.parseTransaction(request.getText(), request.getGeminiApiKey())
    );
  }
}