package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.dto.common.PageResponse;
import com.diepau1312.financeTrackerBE.dto.transaction.*;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import com.diepau1312.financeTrackerBE.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.List;
import java.util.UUID;

@Tag(name = "Transactions", description = "Quản lý giao dịch thu chi")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

  private final TransactionService transactionService;

  @Operation(summary = "Tạo giao dịch mới")
  @PostMapping
  public ResponseEntity<TransactionResponse> create(
      @Valid @RequestBody TransactionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(transactionService.create(request));
  }

  /**
   * Lấy danh sách giao dịch với pagination và filter.
   * <p>
   * Params:
   * - page, size: phân trang
   * - type: filter INCOME / EXPENSE / TRANSFER (optional)
   * - categoryId: filter theo category (optional)
   * - walletId: filter theo wallet — bao gồm transfer_in/out (optional)
   * - search: tìm kiếm text trong note và tên ví (optional, THÊM MỚI)
   * <p>
   * Priority: walletId > categoryId > search+type > type > all
   */
  @Operation(summary = "Danh sách giao dịch với search + filter")
  @GetMapping
  public ResponseEntity<PageResponse<TransactionResponse>> getAll(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) UUID categoryId,
      @Parameter(description = "Filter theo wallet — bao gồm cả transfer_in/out của ví đó")
      @RequestParam(required = false) UUID walletId,
      @Parameter(description = "Tìm kiếm text trong note và tên ví")
      @RequestParam(required = false) String search) {

    int clampedSize = Math.min(size, 100);
    var result = transactionService.getAll(page, clampedSize, type, categoryId, walletId, search);
    return ResponseEntity.ok(PageResponse.from(result));
  }

  @Operation(summary = "Tổng hợp thu chi theo kỳ")
  @GetMapping("/summary")
  public ResponseEntity<TransactionSummaryResponse> getSummary(
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month,
      @RequestParam(required = false) Integer quarter) {
    return ResponseEntity.ok(transactionService.getSummary(year, month, quarter));
  }

  @Operation(summary = "Biểu đồ thu chi theo ngày")
  @GetMapping("/chart/daily")
  public ResponseEntity<List<DailyChartResponse>> getDailyChart(
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month,
      @RequestParam(required = false) Integer startMonth,
      @RequestParam(required = false) Integer endMonth) {
    return ResponseEntity.ok(
        transactionService.getDailyChart(year, month, startMonth, endMonth));
  }

  @Operation(summary = "Biểu đồ xu hướng theo tháng")
  @GetMapping("/chart/monthly")
  public ResponseEntity<List<MonthlyChartResponse>> getMonthlyChart(
      @RequestParam(required = false) Integer year) {
    return ResponseEntity.ok(transactionService.getMonthlyChart(year));
  }

  @Operation(summary = "Phân bổ thu/chi theo danh mục")
  @GetMapping("/chart/categories")
  public ResponseEntity<List<CategoryChartItem>> getCategoryChart(
      @RequestParam(defaultValue = "EXPENSE") TransactionType type,
      @RequestParam int year,
      @RequestParam(required = false) Integer month,
      @RequestParam(required = false) Integer startMonth,
      @RequestParam(required = false) Integer endMonth) {
    return ResponseEntity.ok(
        transactionService.getCategoryChart(type, year, month, startMonth, endMonth));
  }

  @Operation(summary = "Cập nhật giao dịch")
  @PutMapping("/{id}")
  public ResponseEntity<TransactionResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody TransactionRequest request) {
    return ResponseEntity.ok(transactionService.update(id, request));
  }

  @Operation(summary = "Xóa giao dịch (transfer: xóa cả cặp)")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    transactionService.delete(id);
    return ResponseEntity.noContent().build();
  }
}