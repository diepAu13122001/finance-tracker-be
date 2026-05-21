package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.dto.recurring.*;
import com.diepau1312.financeTrackerBE.dto.transaction.TransactionResponse;
import com.diepau1312.financeTrackerBE.service.RecurringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Recurring", description = "Giao dịch định kỳ — Plus plan required")
@RestController
@RequestMapping("/api/recurring")
@RequiredArgsConstructor
public class RecurringController {

  private final RecurringService recurringService;

  @Operation(summary = "Danh sách giao dịch định kỳ")
  @GetMapping
  @RequiresPlan("PLUS")
  public ResponseEntity<List<RecurringTransactionResponse>> findAll() {
    return ResponseEntity.ok(recurringService.findAll());
  }

  @Operation(summary = "Tạo giao dịch định kỳ mới")
  @PostMapping
  @RequiresPlan("PLUS")
  public ResponseEntity<RecurringTransactionResponse> create(
      @Valid @RequestBody CreateRecurringRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(recurringService.create(request));
  }

  @Operation(summary = "Cập nhật giao dịch định kỳ")
  @PutMapping("/{id}")
  @RequiresPlan("PLUS")
  public ResponseEntity<RecurringTransactionResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRecurringRequest request) {
    return ResponseEntity.ok(recurringService.update(id, request));
  }

  @Operation(summary = "Xóa giao dịch định kỳ")
  @DeleteMapping("/{id}")
  @RequiresPlan("PLUS")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    recurringService.delete(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Execute: thực hiện ngay 1 lần và đẩy nextExecutionDate lên.
   * Dùng khi user muốn ghi giao dịch của hôm nay mà không đợi tự động.
   */
  @Operation(summary = "Thực hiện giao dịch định kỳ ngay")
  @PostMapping("/{id}/execute")
  @RequiresPlan("PLUS")
  public ResponseEntity<TransactionResponse> execute(@PathVariable UUID id) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(recurringService.execute(id));
  }
}
