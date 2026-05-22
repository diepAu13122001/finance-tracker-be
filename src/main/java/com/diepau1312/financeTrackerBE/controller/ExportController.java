package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

  private final ExportService exportService;

  /**
   * Xuất Excel.
   * GET /api/export/excel?year=2026&month=5
   */
  @GetMapping("/excel")
  public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) throws Exception {
    byte[] content = exportService.exportToExcel(year, month);
    String filename = buildFilename("transactions", year, month, "xlsx");

    return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(content);
  }

  /**
   * Xuất PDF.
   * GET /api/export/pdf?year=2026&month=5
   * <p>
   * Khác với Excel:
   * - PDF phù hợp để in ấn hoặc gửi email
   * - Trang A4 landscape, có header/footer
   * - Nền màu xen kẽ cho dễ đọc
   */
  @GetMapping("/pdf")
  public ResponseEntity<byte[]> exportPdf(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) throws Exception {
    byte[] content = exportService.exportToPdf(year, month);
    String filename = buildFilename("transactions", year, month, "pdf");

    return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename).contentType(MediaType.APPLICATION_PDF).body(content);
  }

  // ─── Helper ───────────────────────────────────────────────────────────────

  /**
   * Tạo tên file: transactions_2026_05.xlsx
   */
  private String buildFilename(String base, Integer year, Integer month, String ext) {
    int y = year != null ? year : LocalDate.now().getYear();
    String suffix = month != null ? String.format("%d_%02d", y, month) : String.valueOf(y);
    return base + "_" + suffix + "." + ext;
  }
}