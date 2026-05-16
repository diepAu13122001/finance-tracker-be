package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.entity.Transaction;
import com.diepau1312.financeTrackerBE.repository.TransactionRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;

  public byte[] exportToExcel(Integer year, Integer month) throws Exception {
    // 1. Lấy user hiện tại
    var user = userRepository.findByEmail(SecurityUtil.getCurrentUserEmail())
        .orElseThrow();

    // 2. Xác định date range
    LocalDate start, end;
    int targetYear = year != null ? year : LocalDate.now().getYear();
    if (month != null) {
      start = LocalDate.of(targetYear, month, 1);
      end = start.withDayOfMonth(start.lengthOfMonth());
    } else {
      start = LocalDate.of(targetYear, 1, 1);
      end = LocalDate.of(targetYear, 12, 31);
    }

    // 3. Fetch transactions (không phân trang — export all)
    var pageable = PageRequest.of(0, Integer.MAX_VALUE,
        Sort.by("transactionDate").descending());
    var transactions = transactionRepository
        .findByUserIdOrderByTransactionDateDesc(user.getId(), pageable)
        .getContent();

    // 4. Build Excel
    return buildExcel(transactions);
  }

  private byte[] buildExcel(List<Transaction> transactions) throws Exception {
    try (var workbook = new XSSFWorkbook();
         var out = new ByteArrayOutputStream()) {

      var sheet = workbook.createSheet("Giao dịch");

      // Header style
      var headerStyle = workbook.createCellStyle();
      var font = workbook.createFont();
      font.setBold(true);
      headerStyle.setFont(font);
      headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      // Header row
      var header = sheet.createRow(0);
      String[] columns = {"Ngày", "Loại", "Số tiền", "Ghi chú",
          "Danh mục", "Nguồn tiền"};
      for (int i = 0; i < columns.length; i++) {
        var cell = header.createCell(i);
        cell.setCellValue(columns[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data rows
      for (int i = 0; i < transactions.size(); i++) {
        var tx = transactions.get(i);
        var row = sheet.createRow(i + 1);

        row.createCell(0).setCellValue(tx.getTransactionDate().toString());
        row.createCell(1).setCellValue(tx.getType().name());
        row.createCell(2).setCellValue(tx.getAmount());
        row.createCell(3).setCellValue(tx.getNote() != null ? tx.getNote() : "");
        row.createCell(4).setCellValue(
            tx.getCategory() != null ? tx.getCategory().getName() : "");
        row.createCell(5).setCellValue(
            tx.getWallet() != null ? tx.getWallet().getName() : "");
      }

      // Auto-size columns
      for (int i = 0; i < columns.length; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(out);
      return out.toByteArray();
    }
  }
}