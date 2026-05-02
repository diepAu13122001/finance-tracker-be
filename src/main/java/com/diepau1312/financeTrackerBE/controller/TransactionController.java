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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

  private final TransactionService transactionService;

  @PostMapping
  public ResponseEntity<TransactionResponse> create(
      @Valid @RequestBody TransactionRequest request
  ) {
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(transactionService.create(request));
  }

  @GetMapping
  public ResponseEntity<Page<TransactionResponse>> getAll(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String type  // "INCOME" | "EXPENSE" | null
  ) {
    return ResponseEntity.ok(transactionService.getAll(page, size, type));
  }

  @GetMapping("/summary")
  public ResponseEntity<TransactionSummaryResponse> getSummary(
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month,
      @RequestParam(required = false) Integer quarter
  ) {
    return ResponseEntity.ok(transactionService.getSummary(year, month, quarter));
  }

  @GetMapping("/{id}")
  public ResponseEntity<TransactionResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(transactionService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<TransactionResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody TransactionRequest request
  ) {
    return ResponseEntity.ok(transactionService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    transactionService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/chart/daily")
  public ResponseEntity<List<DailyChartResponse>> getDailyChart(
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month
  ) {
    return ResponseEntity.ok(transactionService.getDailyChart(year, month));
  }

  @GetMapping("/chart/monthly")
  public ResponseEntity<List<MonthlyChartResponse>> getMonthlyChart(
      @RequestParam(required = false) Integer year
  ) {
    return ResponseEntity.ok(transactionService.getMonthlyChart(year));
  }
}