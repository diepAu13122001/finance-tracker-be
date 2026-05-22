package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.entity.Transaction;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.repository.TransactionRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;

  // ─── Helper lấy + validate transactions ──────────────────────────────────

  /**
   * Lấy và validate transactions trước khi export.
   * <p>
   * Kiểm tra 2 điều kiện:
   * 1. Không cho phép xuất tương lai (year/month chưa đến)
   * → Vì chưa có dữ liệu → file trống → confuse user
   * 2. Không cho phép xuất kỳ không có giao dịch nào
   * → Xuất file trống không có ý nghĩa, UX tệ
   * <p>
   * Cả 2 đều throw AuthException → GlobalExceptionHandler trả 400 + message rõ ràng
   */
  private List<Transaction> fetchAndValidate(Integer year, Integer month) {
    var user = userRepository.findByEmail(SecurityUtil.getCurrentUserEmail()).orElseThrow();

    LocalDate today = LocalDate.now();
    int targetYear = year != null ? year : today.getYear();

    LocalDate startDate;
    LocalDate endDate;

    if (month != null) {
      startDate = LocalDate.of(targetYear, month, 1);
      endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
    } else if (year != null) {
      startDate = LocalDate.of(targetYear, 1, 1);
      endDate = LocalDate.of(targetYear, 12, 31);
    } else {
      startDate = today.withDayOfMonth(1);
      endDate = today.withDayOfMonth(today.lengthOfMonth());
    }

    // ── Validation 1: Không xuất tương lai ───────────────────────────────
    //
    // Tương lai được định nghĩa:
    // - startDate > today → toàn bộ kỳ chưa bắt đầu (tháng/năm tương lai)
    //
    // VD hôm nay 22/05/2026:
    // - month=6, year=2026 → startDate=01/06/2026 > 22/05/2026 → TỪ CHỐI
    // - year=2027          → startDate=01/01/2027 > 22/05/2026 → TỪ CHỐI
    // - month=5, year=2026 → startDate=01/05/2026 < 22/05/2026 → OK (tháng đang diễn ra)
    if (startDate.isAfter(today)) {
      String label = month != null ? "Tháng " + month + "/" + targetYear : "Năm " + targetYear;
      throw new AuthException(label + " chưa đến. Chỉ có thể xuất dữ liệu từ hiện tại trở về trước.");
    }

    // ── Fetch ─────────────────────────────────────────────────────────────
    var pageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by("transactionDate").descending());
    List<Transaction> transactions = transactionRepository.findByUserIdAndDateBetween(user.getId(), startDate, endDate, pageable).getContent();

    // ── Validation 2: Không xuất kỳ trống ────────────────────────────────
    //
    // Nếu không có giao dịch nào → file Excel/PDF chỉ có header → vô nghĩa
    // Thông báo rõ kỳ nào trống để user biết cần kiểm tra lại
    if (transactions.isEmpty()) {
      String label = month != null ? "Tháng " + month + "/" + targetYear : "Năm " + targetYear;
      throw new AuthException(label + " không có giao dịch nào. Không thể xuất file trống.");
    }

    return transactions;
  }

  // ─── Excel Export ─────────────────────────────────────────────────────────

  public byte[] exportToExcel(Integer year, Integer month) throws Exception {
    return buildExcel(fetchAndValidate(year, month));
  }

  private byte[] buildExcel(List<Transaction> transactions) throws Exception {
    try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {

      var sheet = workbook.createSheet("Giao dịch");

      var headerStyle = workbook.createCellStyle();
      var font = workbook.createFont();
      font.setBold(true);
      headerStyle.setFont(font);
      headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      var header = sheet.createRow(0);
      String[] columns = {"Ngày", "Loại", "Số tiền (VND)", "Ghi chú", "Danh mục", "Nguồn tiền"};
      for (int i = 0; i < columns.length; i++) {
        var cell = header.createCell(i);
        cell.setCellValue(columns[i]);
        cell.setCellStyle(headerStyle);
      }

      for (int i = 0; i < transactions.size(); i++) {
        var tx = transactions.get(i);
        var row = sheet.createRow(i + 1);
        row.createCell(0).setCellValue(tx.getTransactionDate().toString());
        row.createCell(1).setCellValue(tx.getType().name());
        row.createCell(2).setCellValue(tx.getAmount());
        row.createCell(3).setCellValue(tx.getNote() != null ? tx.getNote() : "");
        row.createCell(4).setCellValue(tx.getCategory() != null ? tx.getCategory().getName() : "");
        row.createCell(5).setCellValue(tx.getWallet() != null ? tx.getWallet().getName() : "");
      }

      for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);
      workbook.write(out);
      return out.toByteArray();
    }
  }

  // ─── PDF Export ───────────────────────────────────────────────────────────

  public byte[] exportToPdf(Integer year, Integer month) throws Exception {
    List<Transaction> transactions = fetchAndValidate(year, month);
    String periodLabel = buildPeriodLabel(year, month);

    try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      PDType0Font fontRegular;
      PDType0Font fontBold;
      try (InputStream rs = new ClassPathResource("fonts/DejaVuSans.ttf").getInputStream(); InputStream bs = new ClassPathResource("fonts/DejaVuSans-Bold.ttf").getInputStream()) {
        fontRegular = PDType0Font.load(doc, rs, true);
        fontBold = PDType0Font.load(doc, bs, true);
      }

      PDRectangle landscape = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
      float margin = 40f;
      float pageWidth = landscape.getWidth() - 2 * margin;
      float[] colWidths = {70f, 75f, 95f, 200f, 120f, 120f};
      String[] headers = {"Ngày", "Loại", "Số tiền (VND)", "Ghi chú", "Danh mục", "Ví"};
      float rowHeight = 18f;
      int rowsPerPage = (int) ((landscape.getHeight() - 2 * margin - 60f) / rowHeight);

      int totalRows = transactions.size();
      int totalPages = Math.max(1, (int) Math.ceil((double) totalRows / rowsPerPage));

      for (int pageNum = 0; pageNum < totalPages; pageNum++) {
        PDPage page = new PDPage(landscape);
        doc.addPage(page);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
          float y = landscape.getHeight() - margin;

          if (pageNum == 0) {
            cs.setFont(fontBold, 16);
            cs.beginText();
            cs.newLineAtOffset(margin, y);
            cs.showText("Finance Tracker - Bao cao giao dich");
            cs.endText();
            y -= 20f;

            cs.setFont(fontRegular, 10);
            cs.beginText();
            cs.newLineAtOffset(margin, y);
            cs.showText("Ky: " + stripVietnamese(periodLabel) + "  |  Xuat ngay: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "  |  Tong: " + totalRows + " giao dich");
            cs.endText();
            y -= 20f;
          }

          y = drawTableRow(cs, headers, colWidths, margin, y, rowHeight, fontBold, 9f, true);

          int startIdx = pageNum * rowsPerPage;
          int endIdx = Math.min(startIdx + rowsPerPage, totalRows);

          for (int i = startIdx; i < endIdx; i++) {
            Transaction tx = transactions.get(i);
            boolean isEven = (i % 2 == 0);

            if (isEven) {
              cs.setNonStrokingColor(0.95f, 0.95f, 0.95f);
              cs.addRect(margin, y - rowHeight + 3, pageWidth, rowHeight);
              cs.fill();
              cs.setNonStrokingColor(0f, 0f, 0f);
            }

            String[] rowData = {tx.getTransactionDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), formatType(tx.getType().name()), formatAmount(tx.getAmount()), truncate(tx.getNote() != null ? tx.getNote() : "-", 35), truncate(tx.getCategory() != null ? tx.getCategory().getName() : "-", 20), truncate(tx.getWallet() != null ? tx.getWallet().getName() : "-", 20),};

            y = drawTableRow(cs, rowData, colWidths, margin, y, rowHeight, fontRegular, 8.5f, false);
          }

          cs.setFont(fontRegular, 8);
          cs.beginText();
          cs.newLineAtOffset(margin, 20f);
          cs.showText("Trang " + (pageNum + 1) + " / " + totalPages);
          cs.endText();
        }
      }

      doc.save(out);
      return out.toByteArray();
    }
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private float drawTableRow(PDPageContentStream cs, String[] cells, float[] colWidths, float startX, float y, float rowHeight, PDType0Font font, float fontSize, boolean isHeader) throws Exception {

    float cellY = y - rowHeight + 5f;

    if (isHeader) {
      cs.setNonStrokingColor(0.2f, 0.4f, 0.8f);
      float totalWidth = 0;
      for (float w : colWidths) totalWidth += w;
      cs.addRect(startX, y - rowHeight + 3, totalWidth, rowHeight);
      cs.fill();
      cs.setNonStrokingColor(1f, 1f, 1f);
    }

    cs.setFont(font, fontSize);
    float x = startX;
    for (int i = 0; i < cells.length && i < colWidths.length; i++) {
      cs.beginText();
      cs.newLineAtOffset(x + 3f, cellY);
      cs.showText(cells[i] != null ? cells[i] : "");
      cs.endText();
      x += colWidths[i];
    }

    if (isHeader) cs.setNonStrokingColor(0f, 0f, 0f);
    return y - rowHeight;
  }

  private String buildPeriodLabel(Integer year, Integer month) {
    int y = year != null ? year : LocalDate.now().getYear();
    if (month != null) return "Thang " + month + "/" + y;
    return "Nam " + y;
  }

  private String formatType(String type) {
    return switch (type) {
      case "INCOME" -> "Thu nhap";
      case "EXPENSE" -> "Chi tieu";
      case "TRANSFER" -> "Chuyen khoan";
      default -> type;
    };
  }

  private String formatAmount(long amount) {
    return String.format("%,d", amount).replace(',', '.');
  }

  private String truncate(String s, int maxLen) {
    if (s == null || s.isBlank()) return "-";
    return s.length() <= maxLen ? s : s.substring(0, maxLen - 2) + "..";
  }

  private String stripVietnamese(String s) {
    String[][] map = {{"à", "a"}, {"á", "a"}, {"ả", "a"}, {"ã", "a"}, {"ạ", "a"}, {"ă", "a"}, {"ằ", "a"}, {"ắ", "a"}, {"ẳ", "a"}, {"ẵ", "a"}, {"ặ", "a"}, {"â", "a"}, {"ầ", "a"}, {"ấ", "a"}, {"ẩ", "a"}, {"ẫ", "a"}, {"ậ", "a"}, {"đ", "d"}, {"è", "e"}, {"é", "e"}, {"ẻ", "e"}, {"ẽ", "e"}, {"ẹ", "e"}, {"ê", "e"}, {"ề", "e"}, {"ế", "e"}, {"ể", "e"}, {"ễ", "e"}, {"ệ", "e"}, {"ì", "i"}, {"í", "i"}, {"ỉ", "i"}, {"ĩ", "i"}, {"ị", "i"}, {"ò", "o"}, {"ó", "o"}, {"ỏ", "o"}, {"õ", "o"}, {"ọ", "o"}, {"ô", "o"}, {"ồ", "o"}, {"ố", "o"}, {"ổ", "o"}, {"ỗ", "o"}, {"ộ", "o"}, {"ơ", "o"}, {"ờ", "o"}, {"ớ", "o"}, {"ở", "o"}, {"ỡ", "o"}, {"ợ", "o"}, {"ù", "u"}, {"ú", "u"}, {"ủ", "u"}, {"ũ", "u"}, {"ụ", "u"}, {"ư", "u"}, {"ừ", "u"}, {"ứ", "u"}, {"ử", "u"}, {"ữ", "u"}, {"ự", "u"}, {"ỳ", "y"}, {"ý", "y"}, {"ỷ", "y"}, {"ỹ", "y"}, {"ỵ", "y"},};
    String result = s.toLowerCase();
    for (String[] pair : map) result = result.replace(pair[0], pair[1]);
    return result;
  }
}