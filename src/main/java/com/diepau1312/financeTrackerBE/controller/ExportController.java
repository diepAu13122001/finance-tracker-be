package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

  private final ExportService exportService;

  @GetMapping("/excel")
  public ResponseEntity<byte[]> exportExcel(
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month
  ) throws Exception {
    byte[] content = exportService.exportToExcel(year, month);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=transactions.xlsx")
        .contentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(content);
  }
}