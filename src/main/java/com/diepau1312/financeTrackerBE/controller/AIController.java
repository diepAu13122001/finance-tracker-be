package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.dto.ai.*;
import com.diepau1312.financeTrackerBE.dto.transaction.CategoryChartItem;
import com.diepau1312.financeTrackerBE.entity.Transaction;
import com.diepau1312.financeTrackerBE.service.GeminiService;
import com.diepau1312.financeTrackerBE.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "AI", description = "AI features — Plus plan required")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

  private final GeminiService geminiService;
  private final TransactionService transactionService;

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

  /**
   * Phân tích chi tiêu tháng bằng Gemini.
   *
   * Flow:
   *   1. Lấy summary (totalIncome, totalExpense) từ TransactionService
   *   2. Lấy top 5 danh mục chi tiêu từ getCategoryChart
   *   3. Gọi GeminiService.analyzeSpending() với dữ liệu đã chuẩn bị
   *   4. Trả về insights (overview, topInsight, suggestion, warnings)
   *
   * Lý do tổng hợp data ở controller thay vì service:
   *   - GeminiService không nên phụ thuộc vào TransactionService (tách trách nhiệm)
   *   - Controller làm điều phối, service làm business logic thuần túy
   */
  @Operation(
      summary = "Phân tích chi tiêu tháng bằng AI",
      description = "Plus feature. Gemini phân tích tổng thu chi và top danh mục, đưa ra insights."
  )
  @PostMapping("/analyze-spending")
  @RequiresPlan("PLUS")
  public ResponseEntity<AIAnalyzeResponse> analyzeSpending(
      @Valid @RequestBody AIAnalyzeRequest request) {

    // Lấy tổng thu/chi tháng
    var summary = transactionService.getSummary(
        request.getYear(), request.getMonth(), null);

    // Lấy top 5 danh mục chi tiêu để cung cấp context cho AI
    List<CategoryChartItem> categoryItems = transactionService.getCategoryChart(
        Transaction.TransactionType.EXPENSE,
        request.getYear(), request.getMonth(), null, null);

    List<String> topCategories = categoryItems.stream()
        .limit(5)
        .map(c -> c.getCategoryName() + " ("
            + String.format("%,d", c.getTotalAmount()) + " VND)")
        .collect(Collectors.toList());

    return ResponseEntity.ok(
        geminiService.analyzeSpending(
            summary.getTotalIncome()  != null ? summary.getTotalIncome()  : 0L,
            summary.getTotalExpense() != null ? summary.getTotalExpense() : 0L,
            topCategories,
            request.getYear(),
            request.getMonth(),
            request.getGeminiApiKey()
        )
    );
  }
}
