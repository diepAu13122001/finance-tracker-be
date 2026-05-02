package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.dto.transaction.*;
import com.diepau1312.financeTrackerBE.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

  @Operation(
      summary = "Tạo giao dịch mới",
      description = "Free user: tối đa 50 giao dịch/tháng. Plus/Premium: không giới hạn."
  )
  @PostMapping
  public ResponseEntity<TransactionResponse> create(
      @Valid @RequestBody TransactionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(transactionService.create(request));
  }

  @Operation(summary = "Danh sách giao dịch", description = "Phân trang, filter theo type")
  @GetMapping
  public ResponseEntity<Page<TransactionResponse>> getAll(
      @Parameter(description = "Số trang (bắt đầu từ 0)")
      @RequestParam(defaultValue = "0") int page,

      @Parameter(description = "Số item mỗi trang")
      @RequestParam(defaultValue = "20") int size,

      @Parameter(description = "Filter: INCOME, EXPENSE. Bỏ trống = tất cả")
      @RequestParam(required = false) String type
  ) {
    return ResponseEntity.ok(transactionService.getAll(page, size, type));
  }

  @Operation(
      summary = "Tổng hợp thu chi theo kỳ",
      description = "Hỗ trợ filter: tháng, quý, năm. Trả về giới hạn giao dịch cho Free user."
  )
  @GetMapping("/summary")
  public ResponseEntity<TransactionSummaryResponse> getSummary(
      @Parameter(description = "Năm (mặc định: năm hiện tại)")
      @RequestParam(required = false) Integer year,

      @Parameter(description = "Tháng 1-12. Bỏ trống = toàn bộ năm")
      @RequestParam(required = false) Integer month,

      @Parameter(description = "Quý 1-4. Ưu tiên hơn month nếu cả 2 được truyền")
      @RequestParam(required = false) Integer quarter
  ) {
    return ResponseEntity.ok(transactionService.getSummary(year, month, quarter));
  }

  @Operation(summary = "Biểu đồ thu chi theo ngày")
  @GetMapping("/chart/daily")
  public ResponseEntity<List<DailyChartResponse>> getDailyChart(
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month,
      @RequestParam(required = false) Integer startMonth,
      @RequestParam(required = false) Integer endMonth
  ) {
    return ResponseEntity.ok(
        transactionService.getDailyChart(year, month, startMonth, endMonth));
  }

  @Operation(summary = "Biểu đồ xu hướng theo tháng trong năm")
  @GetMapping("/chart/monthly")
  public ResponseEntity<List<MonthlyChartResponse>> getMonthlyChart(
      @Parameter(description = "Năm cần xem (mặc định: năm hiện tại)")
      @RequestParam(required = false) Integer year
  ) {
    return ResponseEntity.ok(transactionService.getMonthlyChart(year));
  }

  @Operation(summary = "Cập nhật giao dịch")
  @PutMapping("/{id}")
  public ResponseEntity<TransactionResponse> update(
      @Parameter(description = "UUID của giao dịch")
      @PathVariable UUID id,
      @Valid @RequestBody TransactionRequest request
  ) {
    return ResponseEntity.ok(transactionService.update(id, request));
  }

  @Operation(summary = "Xóa giao dịch")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    transactionService.delete(id);
    return ResponseEntity.noContent().build();
  }
}